package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.ProcessingException;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DelimitedTablePreviewService {

	private static final int PREVIEW_ROWS = 24;
	private static final int MAX_PAGE_ROWS = 200;
	private static final int MAX_COLUMNS = 256;
	private static final int MAX_CELL_CHARS = 32_768;
	private static final int MAX_OFFSET = 100_000;
	private static final Set<Character> DELIMITERS = Set.of(',', '\t', ';', '|');
	private final ProcessingResourceBudgetService budgets;

	public DelimitedTablePreviewService(ProcessingResourceBudgetService budgets) {
		this.budgets = budgets;
	}

	public TableWindow preview(StoredUpload upload, Character requestedDelimiter, HeaderMode headerMode) {
		return readWindow(upload, requestedDelimiter, headerMode, 0, PREVIEW_ROWS);
	}

	public TableWindow page(
		StoredUpload upload,
		Character requestedDelimiter,
		HeaderMode headerMode,
		String cursor,
		Integer requestedOffset,
		int requestedLimit
	) {
		var limit = Math.max(1, Math.min(MAX_PAGE_ROWS, requestedLimit));
		var effectiveDelimiter = requestedDelimiter == null ? detectDelimiter(upload) : validateDelimiter(requestedDelimiter);
		var revision = revision(upload, effectiveDelimiter, headerMode);
		var offset = cursor == null || cursor.isBlank()
			? Math.max(0, requestedOffset == null ? 0 : requestedOffset)
			: decodeCursor(cursor, revision);
		if (offset > MAX_OFFSET) {
			throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "Table cursor превышает допустимый row offset.");
		}
		return readWindow(upload, effectiveDelimiter, headerMode, offset, limit);
	}

	private TableWindow readWindow(
		StoredUpload upload,
		Character requestedDelimiter,
		HeaderMode headerMode,
		int offset,
		int limit
	) {
		var delimiter = requestedDelimiter == null ? detectDelimiter(upload) : validateDelimiter(requestedDelimiter);
		var revision = revision(upload, delimiter, headerMode);
		var warnings = new LinkedHashSet<String>();
		var sampledRows = new ArrayList<List<String>>();
		var maxColumns = 0;
		var parsedDataRows = 0;
		var hasMore = false;
		List<String> firstRecord = null;

		try (var reader = utf8Reader(upload); var parser = new CSVParser(reader, csvFormat(delimiter))) {
			for (var record : parser) {
				var row = new ArrayList<String>();
				for (String value : record) {
					if (row.size() >= MAX_COLUMNS) {
						throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "CSV row превышает column budget.");
					}
					if (value.length() > MAX_CELL_CHARS) {
						throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "CSV cell превышает допустимый размер.");
					}
					row.add(value);
					if (startsAsFormula(value)) {
						warnings.add("Обнаружены formula-like cells: значения не исполняются и при spreadsheet export должны экранироваться.");
					}
				}
				if (firstRecord == null) {
					firstRecord = List.copyOf(row);
					if (resolveHasHeader(firstRecord, headerMode)) {
						maxColumns = Math.max(maxColumns, row.size());
						continue;
					}
				}
				maxColumns = Math.max(maxColumns, row.size());
				this.budgets.verifyTableShape(parsedDataRows + 1L, Math.max(1, maxColumns));
				if (parsedDataRows >= offset && sampledRows.size() < limit) {
					sampledRows.add(List.copyOf(row));
				}
				parsedDataRows += 1;
				if (parsedDataRows > offset + limit) {
					hasMore = true;
					break;
				}
			}
		}
		catch (CharacterCodingException exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "CSV содержит невалидную UTF-8 последовательность.", exception);
		}
		catch (IOException exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "Не удалось прочитать CSV/TSV в bounded режиме.", exception);
		}

		var hasHeader = firstRecord != null && resolveHasHeader(firstRecord, headerMode);
		var columns = buildColumns(hasHeader ? firstRecord : null, maxColumns, warnings);
		var columnCount = maxColumns;
		if (sampledRows.stream().anyMatch(row -> row.size() != columnCount)) {
			warnings.add("Строки имеют разную ширину и дополнены пустыми ячейками в preview.");
		}
		var normalizedRows = sampledRows.stream().map(row -> padRow(row, columnCount)).toList();
		var nextOffset = offset + normalizedRows.size();
		var nextCursor = hasMore ? encodeCursor(revision, nextOffset) : null;
		var metadata = inferColumns(columns, normalizedRows);
		return new TableWindow(
			new DocumentPreviewPayload.DocumentTablePreview(
				columns,
				normalizedRows,
				hasMore ? nextOffset : parsedDataRows,
				maxColumns,
				String.valueOf(delimiter),
				revision,
				hasMore,
				"UTF-8",
				hasHeader,
				metadata,
				nextCursor,
				offset
			),
			List.copyOf(warnings),
			hasMore ? null : parsedDataRows
		);
	}

	private BufferedReader utf8Reader(StoredUpload upload) throws IOException {
		var input = new PushbackInputStream(Files.newInputStream(upload.storagePath()), 3);
		var bom = input.readNBytes(3);
		if (!(bom.length == 3 && (bom[0] & 0xff) == 0xef && (bom[1] & 0xff) == 0xbb && (bom[2] & 0xff) == 0xbf)) {
			input.unread(bom);
		}
		var decoder = StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		return new BufferedReader(new InputStreamReader(input, decoder), 16_384);
	}

	private char detectDelimiter(StoredUpload upload) {
		try (var input = Files.newInputStream(upload.storagePath())) {
			var prefix = input.readNBytes(65_536);
			try {
				StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(prefix));
			}
			catch (CharacterCodingException exception) {
				throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "CSV encoding не является UTF-8.", exception);
			}
			var text = new String(prefix, StandardCharsets.UTF_8);
			var counts = new HashMap<Character, Integer>();
			for (char candidate : DELIMITERS) {
				counts.put(candidate, delimiterScore(text, candidate));
			}
			return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.filter(entry -> entry.getValue() > 0)
				.map(Map.Entry::getKey)
				.orElse(upload.extension().equals("tsv") ? '\t' : ',');
		}
		catch (IOException exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "Не удалось определить CSV delimiter.", exception);
		}
	}

	private int delimiterScore(String text, char delimiter) {
		var lines = text.lines().limit(8).toList();
		if (lines.isEmpty()) {
			return 0;
		}
		var counts = lines.stream().mapToInt(line -> (int) line.chars().filter(value -> value == delimiter).count()).toArray();
		var positive = java.util.Arrays.stream(counts).filter(value -> value > 0).count();
		var max = java.util.Arrays.stream(counts).max().orElse(0);
		return positive < 2 ? max : (int) positive * 100 + max;
	}

	private CSVFormat csvFormat(char delimiter) {
		return CSVFormat.DEFAULT.builder()
			.setDelimiter(delimiter)
			.setIgnoreEmptyLines(false)
			.setIgnoreSurroundingSpaces(false)
			.setTrim(false)
			.build();
	}

	private boolean resolveHasHeader(List<String> row, HeaderMode mode) {
		if (mode == HeaderMode.PRESENT) {
			return true;
		}
		if (mode == HeaderMode.ABSENT) {
			return false;
		}
		var normalized = row.stream().map(value -> value.strip().toLowerCase(Locale.ROOT)).toList();
		return !normalized.isEmpty()
			&& normalized.stream().allMatch(value -> !value.isBlank())
			&& normalized.stream().distinct().count() == normalized.size()
			&& normalized.stream().anyMatch(value -> !looksNumeric(value));
	}

	private List<String> buildColumns(List<String> header, int maxColumns, Set<String> warnings) {
		var names = new ArrayList<String>();
		var occurrences = new HashMap<String, Integer>();
		for (int index = 0; index < maxColumns; index += 1) {
			var raw = header != null && index < header.size() ? header.get(index).strip() : "";
			var base = raw.isBlank() ? spreadsheetColumn(index) : raw;
			var count = occurrences.merge(base, 1, Integer::sum);
			if (raw.isBlank()) {
				warnings.add("Пустые header cells заменены стабильными column labels.");
			}
			if (count > 1) {
				warnings.add("Duplicate header labels получили стабильные suffixes.");
			}
			names.add(count > 1 ? base + " (" + count + ")" : base);
		}
		return List.copyOf(names);
	}

	private List<DocumentPreviewPayload.DocumentTableColumn> inferColumns(List<String> columns, List<List<String>> rows) {
		var result = new ArrayList<DocumentPreviewPayload.DocumentTableColumn>();
		for (int index = 0; index < columns.size(); index += 1) {
			var values = new ArrayList<String>();
			for (var row : rows) {
				if (index < row.size() && !row.get(index).isBlank()) {
					values.add(row.get(index).strip());
				}
			}
			var type = values.isEmpty() ? "text"
				: values.stream().allMatch(this::looksBoolean) ? "boolean"
				: values.stream().allMatch(this::looksNumeric) ? "number"
				: "text";
			result.add(new DocumentPreviewPayload.DocumentTableColumn("column-" + (index + 1), columns.get(index), type));
		}
		return List.copyOf(result);
	}

	private List<String> padRow(List<String> row, int columns) {
		var result = new ArrayList<>(row);
		while (result.size() < columns) {
			result.add("");
		}
		return List.copyOf(result);
	}

	private String revision(StoredUpload upload, Character delimiter, HeaderMode headerMode) {
		return upload.sha256().substring(0, 16) + ":" + (delimiter == null ? "auto" : (int) delimiter.charValue()) + ":" + headerMode.name();
	}

	private String encodeCursor(String revision, int offset) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString((revision + ":" + offset).getBytes(StandardCharsets.UTF_8));
	}

	private int decodeCursor(String cursor, String revision) {
		try {
			var decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			var separator = decoded.lastIndexOf(':');
			if (separator < 0 || !decoded.substring(0, separator).equals(revision)) {
				throw new IllegalArgumentException();
			}
			return Integer.parseInt(decoded.substring(separator + 1));
		}
		catch (RuntimeException exception) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Table cursor не соответствует текущей revision.");
		}
	}

	private char validateDelimiter(char delimiter) {
		if (!DELIMITERS.contains(delimiter)) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "INVALID_TABLE_OPTIONS", "Delimiter не входит в allowlist.");
		}
		return delimiter;
	}

	private boolean startsAsFormula(String value) {
		var stripped = value.stripLeading();
		return !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0;
	}

	private boolean looksNumeric(String value) {
		return value.matches("[-+]?\\d+(?:[.,]\\d+)?");
	}

	private boolean looksBoolean(String value) {
		return Set.of("true", "false", "yes", "no", "0", "1").contains(value.toLowerCase(Locale.ROOT));
	}

	private String spreadsheetColumn(int index) {
		var builder = new StringBuilder();
		var value = index + 1;
		while (value > 0) {
			value -= 1;
			builder.append((char) ('A' + value % 26));
			value /= 26;
		}
		return builder.reverse().toString();
	}

	public enum HeaderMode {
		AUTO,
		PRESENT,
		ABSENT
	}

	public record TableWindow(
		DocumentPreviewPayload.DocumentTablePreview table,
		List<String> warnings,
		Integer exactRowCount
	) {
	}
}
