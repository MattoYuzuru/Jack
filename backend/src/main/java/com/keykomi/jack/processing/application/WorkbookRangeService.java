package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingException;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorkbookRangeService {

	private static final int MAX_RANGE_ROWS = 100;
	private static final int MAX_RANGE_COLUMNS = 50;
	private static final int MAX_SHEETS = 50;
	private static final int MAX_TEXT_CHARS = 32_768;
	private final ProcessingResourceBudgetService budgets;

	public WorkbookRangeService(ProcessingResourceBudgetService budgets) {
		this.budgets = budgets;
	}

	public WorkbookRange readRange(
		StoredUpload upload,
		int sheetIndex,
		int startRow,
		int startColumn,
		int requestedRows,
		int requestedColumns
	) {
		if ("ods".equals(upload.extension())) {
			return readOdsRange(upload, sheetIndex, startRow, startColumn, requestedRows, requestedColumns);
		}
		if (!Set.of("xls", "xlsx", "xlsm").contains(upload.extension())) {
			throw new ProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT", "Semantic workbook range поддерживает XLS/XLSX/XLSM.");
		}
		if (sheetIndex < 0 || startRow < 0 || startColumn < 0) {
			throw invalidRange();
		}
		var rowCount = Math.max(1, Math.min(MAX_RANGE_ROWS, requestedRows));
		var columnCount = Math.max(1, Math.min(MAX_RANGE_COLUMNS, requestedColumns));
		this.budgets.verifyTableShape(rowCount, columnCount);

		try (var input = Files.newInputStream(upload.storagePath()); Workbook workbook = WorkbookFactory.create(input)) {
			if (workbook.getNumberOfSheets() > MAX_SHEETS) {
				throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "Workbook превышает sheet budget.");
			}
			if (sheetIndex >= workbook.getNumberOfSheets()) {
				throw invalidRange();
			}
			var sheet = workbook.getSheetAt(sheetIndex);
			var formatter = new DataFormatter(Locale.ROOT, false);
			var warnings = new LinkedHashSet<String>();
			var rows = new ArrayList<List<WorkbookCell>>();
			for (int rowOffset = 0; rowOffset < rowCount; rowOffset += 1) {
				var resultRow = new ArrayList<WorkbookCell>();
				var sourceRow = sheet.getRow(startRow + rowOffset);
				for (int columnOffset = 0; columnOffset < columnCount; columnOffset += 1) {
					var columnIndex = startColumn + columnOffset;
					var cell = sourceRow == null ? null : sourceRow.getCell(columnIndex);
					resultRow.add(toCell(cell, startRow + rowOffset, columnIndex, formatter, warnings));
				}
				rows.add(List.copyOf(resultRow));
			}
			var pane = sheet.getPaneInformation();
			var mergedRanges = sheet.getMergedRegions().stream()
				.filter(range -> intersects(range.getFirstRow(), range.getLastRow(), startRow, startRow + rowCount - 1)
					&& intersects(range.getFirstColumn(), range.getLastColumn(), startColumn, startColumn + columnCount - 1))
				.limit(256)
				.map(org.apache.poi.ss.util.CellRangeAddress::formatAsString)
				.toList();
			if (upload.extension().equals("xlsm")) {
				warnings.add("VBA macros не загружаются и не исполняются.");
			}
			return new WorkbookRange(
				upload.sha256().substring(0, 16) + ":sheet:" + sheetIndex,
				sheetIndex,
				sheet.getSheetName(),
				workbook.isSheetHidden(sheetIndex) || workbook.isSheetVeryHidden(sheetIndex),
				pane == null ? 0 : pane.getHorizontalSplitPosition(),
				pane == null ? 0 : pane.getVerticalSplitPosition(),
				startRow,
				startColumn,
				rowCount,
				columnCount,
				List.copyOf(rows),
				mergedRanges,
				List.copyOf(warnings)
			);
		}
		catch (ProcessingException exception) {
			throw exception;
		}
		catch (IOException | RuntimeException exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "Workbook range не удалось прочитать.", exception);
		}
	}

	public List<OdsSheetDescriptor> listOdsSheets(StoredUpload upload) {
		var document = loadOdsDocument(upload);
		var sheets = odsSheets(document.getDocumentElement());
		if (sheets.size() > MAX_SHEETS) {
			throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "ODS превышает sheet budget.");
		}
		var descriptors = new ArrayList<OdsSheetDescriptor>();
		for (int index = 0; index < sheets.size(); index += 1) {
			var sheet = sheets.get(index);
			descriptors.add(new OdsSheetDescriptor(
				index,
				attribute(sheet, "name", "Sheet " + (index + 1)),
				"false".equalsIgnoreCase(attribute(sheet, "display", "true")),
				logicalOdsRowCount(sheet)
			));
		}
		return List.copyOf(descriptors);
	}

	private WorkbookRange readOdsRange(
		StoredUpload upload,
		int sheetIndex,
		int startRow,
		int startColumn,
		int requestedRows,
		int requestedColumns
	) {
		if (sheetIndex < 0 || startRow < 0 || startColumn < 0) {
			throw invalidRange();
		}
		var rowCount = Math.max(1, Math.min(MAX_RANGE_ROWS, requestedRows));
		var columnCount = Math.max(1, Math.min(MAX_RANGE_COLUMNS, requestedColumns));
		this.budgets.verifyTableShape(rowCount, columnCount);
		var document = loadOdsDocument(upload);
		var sheets = odsSheets(document.getDocumentElement());
		if (sheetIndex >= sheets.size() || sheets.size() > MAX_SHEETS) {
			throw invalidRange();
		}
		var sheet = sheets.get(sheetIndex);
		var logicalRows = new ArrayList<List<WorkbookCell>>();
		var logicalRowIndex = 0;
		for (var rowElement : directChildren(sheet, "table-row")) {
			var repeat = boundedRepeat(rowElement, "number-rows-repeated");
			for (int repeated = 0; repeated < repeat; repeated += 1) {
				if (logicalRowIndex >= startRow && logicalRows.size() < rowCount) {
					logicalRows.add(readOdsRow(rowElement, logicalRowIndex, startColumn, columnCount));
				}
				logicalRowIndex += 1;
				if (logicalRows.size() >= rowCount) {
					break;
				}
			}
			if (logicalRows.size() >= rowCount) {
				break;
			}
		}
		while (logicalRows.size() < rowCount) {
			var blankRow = new ArrayList<WorkbookCell>();
			for (int column = 0; column < columnCount; column += 1) {
				blankRow.add(new WorkbookCell(startRow + logicalRows.size(), startColumn + column, "", "", "blank", null, null, null, null, null, null, null));
			}
			logicalRows.add(List.copyOf(blankRow));
		}
		return new WorkbookRange(
			upload.sha256().substring(0, 16) + ":ods:" + sheetIndex,
			sheetIndex,
			attribute(sheet, "name", "Sheet " + (sheetIndex + 1)),
			"false".equalsIgnoreCase(attribute(sheet, "display", "true")),
			0,
			0,
			startRow,
			startColumn,
			rowCount,
			columnCount,
			List.copyOf(logicalRows),
			List.of(),
			List.of("ODS semantic range ограничен значениями, типами и formula text; внешние links и macros не исполняются.")
		);
	}

	private List<WorkbookCell> readOdsRow(Element row, int rowIndex, int startColumn, int columnCount) {
		var result = new ArrayList<WorkbookCell>();
		var logicalColumn = 0;
		for (var cell : directChildren(row, "table-cell", "covered-table-cell")) {
			var repeat = boundedRepeat(cell, "number-columns-repeated");
			for (int repeated = 0; repeated < repeat; repeated += 1) {
				if (logicalColumn >= startColumn && result.size() < columnCount) {
					var formatted = bounded(cell.getTextContent().strip());
					var raw = attribute(cell, "value", formatted);
					var type = attribute(cell, "value-type", formatted.isBlank() ? "blank" : "string");
					var formula = attribute(cell, "formula", null);
					result.add(new WorkbookCell(
						rowIndex, logicalColumn, raw, formatted, type, formula, null,
						"date".equals(type) ? attribute(cell, "date-value", null) : null,
						"error".equals(type) ? formatted : null, null, null, null
					));
				}
				logicalColumn += 1;
				if (result.size() >= columnCount) {
					break;
				}
			}
			if (result.size() >= columnCount) {
				break;
			}
		}
		while (result.size() < columnCount) {
			var column = startColumn + result.size();
			result.add(new WorkbookCell(rowIndex, column, "", "", "blank", null, null, null, null, null, null, null));
		}
		return List.copyOf(result);
	}

	private org.w3c.dom.Document loadOdsDocument(StoredUpload upload) {
		try (var zip = new ZipFile(upload.storagePath().toFile())) {
			var content = this.budgets.readZipEntryAsText(zip, "content.xml", 8_388_608L)
				.orElseThrow(() -> new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "ODS не содержит content.xml."));
			return SecureXmlParser.parse(content);
		}
		catch (ProcessingException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "ODS content.xml не удалось разобрать.", exception);
		}
	}

	private List<Element> odsSheets(Node root) {
		var result = new ArrayList<Element>();
		collectElements(root, "table", result);
		return result;
	}

	private void collectElements(Node node, String localName, List<Element> result) {
		if (node instanceof Element element && localName.equals(element.getLocalName())
			&& element.getNamespaceURI() != null && element.getNamespaceURI().contains(":table:")) {
			result.add(element);
		}
		var children = node.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			collectElements(children.item(index), localName, result);
		}
	}

	private List<Element> directChildren(Node node, String... localNames) {
		var allowed = Set.of(localNames);
		var result = new ArrayList<Element>();
		var children = node.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			if (children.item(index) instanceof Element element && allowed.contains(element.getLocalName())) {
				result.add(element);
			}
		}
		return result;
	}

	private int logicalOdsRowCount(Element sheet) {
		long rows = 0;
		for (var row : directChildren(sheet, "table-row")) {
			rows += boundedRepeat(row, "number-rows-repeated");
			this.budgets.verifyTableShape(rows, 1);
		}
		return Math.toIntExact(rows);
	}

	private int boundedRepeat(Element element, String localName) {
		var raw = attribute(element, localName, "1");
		try {
			var repeat = Integer.parseInt(raw);
			if (repeat < 1 || repeat > 20_000) {
				throw new NumberFormatException();
			}
			return repeat;
		}
		catch (NumberFormatException exception) {
			throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "ODS repeat count превышает budget.");
		}
	}

	private String attribute(Element element, String localName, String fallback) {
		if (element.hasAttribute(localName)) {
			return element.getAttribute(localName);
		}
		var attributes = element.getAttributes();
		for (int index = 0; index < attributes.getLength(); index += 1) {
			var attribute = attributes.item(index);
			if (localName.equals(attribute.getLocalName())) {
				return attribute.getNodeValue();
			}
		}
		return fallback;
	}

	private WorkbookCell toCell(
		Cell cell,
		int rowIndex,
		int columnIndex,
		DataFormatter formatter,
		Set<String> warnings
	) {
		if (cell == null) {
			return new WorkbookCell(rowIndex, columnIndex, "", "", "blank", null, null, null, null, null, null, null);
		}
		var type = cell.getCellType();
		var formula = type == CellType.FORMULA ? bounded(cell.getCellFormula()) : null;
		var effectiveType = type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
		var raw = rawValue(cell, effectiveType);
		var formatted = bounded(formatter.formatCellValue(cell));
		var cached = type == CellType.FORMULA ? raw : null;
		var dateValue = effectiveType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
			? cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC).toString()
			: null;
		var error = effectiveType == CellType.ERROR ? Byte.toString(cell.getErrorCellValue()) : null;
		var hyperlink = safeHyperlink(cell, warnings);
		var comment = cell.getCellComment() == null ? null : bounded(cell.getCellComment().getString().getString());
		var style = safeStyle(cell);
		if (formula != null) {
			warnings.add("Формулы не вычисляются: range возвращает formula text и cached result из файла.");
			if (formula.contains("[") || formula.toLowerCase(Locale.ROOT).contains("http")) {
				warnings.add("External workbook references не загружаются.");
			}
		}
		return new WorkbookCell(
			rowIndex,
			columnIndex,
			raw,
			formatted,
			typeName(effectiveType),
			formula,
			cached,
			dateValue,
			error,
			style,
			hyperlink,
			comment
		);
	}

	private String rawValue(Cell cell, CellType type) {
		return switch (type) {
			case STRING -> bounded(cell.getStringCellValue());
			case NUMERIC -> Double.toString(cell.getNumericCellValue());
			case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
			case ERROR -> Byte.toString(cell.getErrorCellValue());
			case BLANK, _NONE -> "";
			case FORMULA -> "";
		};
	}

	private CellStyle safeStyle(Cell cell) {
		var style = cell.getCellStyle();
		var font = cell.getSheet().getWorkbook().getFontAt(style.getFontIndex());
		return new CellStyle(
			font.getBold(),
			font.getItalic(),
			style.getAlignment().name().toLowerCase(Locale.ROOT),
			bounded(style.getDataFormatString())
		);
	}

	private String safeHyperlink(Cell cell, Set<String> warnings) {
		if (cell.getHyperlink() == null || cell.getHyperlink().getAddress() == null) {
			return null;
		}
		var address = bounded(cell.getHyperlink().getAddress());
		var normalized = address.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("https://") || normalized.startsWith("http://") || normalized.startsWith("mailto:")) {
			return address;
		}
		warnings.add("Небезопасная hyperlink scheme удалена из semantic range.");
		return null;
	}

	private String typeName(CellType type) {
		return type.name().toLowerCase(Locale.ROOT);
	}

	private String bounded(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= MAX_TEXT_CHARS ? value : value.substring(0, MAX_TEXT_CHARS);
	}

	private boolean intersects(int first, int last, int requestedFirst, int requestedLast) {
		return first <= requestedLast && last >= requestedFirst;
	}

	private ProcessingException invalidRange() {
		return new ProcessingException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "Workbook range выходит за допустимые границы.");
	}

	public record WorkbookRange(
		String revision,
		int sheetIndex,
		String sheetName,
		boolean hidden,
		int frozenRows,
		int frozenColumns,
		int startRow,
		int startColumn,
		int rowCount,
		int columnCount,
		List<List<WorkbookCell>> rows,
		List<String> mergedRanges,
		List<String> warnings
	) {
	}

	public record WorkbookCell(
		int row,
		int column,
		String rawValue,
		String formattedValue,
		String type,
		String formula,
		String cachedResult,
		String dateValue,
		String error,
		CellStyle style,
		String hyperlink,
		String comment
	) {
	}

	public record CellStyle(boolean bold, boolean italic, String alignment, String numberFormat) {
	}

	public record OdsSheetDescriptor(int index, String name, boolean hidden, int rowCount) {
	}
}
