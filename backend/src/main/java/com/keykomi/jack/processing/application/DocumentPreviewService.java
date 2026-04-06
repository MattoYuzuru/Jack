package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.Yaml;

@Service
public class DocumentPreviewService {

	private static final int TEXT_PARAGRAPH_LIMIT = 18;
	private static final int CSV_PREVIEW_ROW_LIMIT = 24;
	private static final int WORKBOOK_PREVIEW_COLUMN_LIMIT = 12;
	private static final int WORKBOOK_PREVIEW_ROW_LIMIT = 28;
	private static final int SQLITE_SAMPLE_ROW_LIMIT = 24;
	private static final int SQLITE_SAMPLE_COLUMN_LIMIT = 12;
	private static final int SQLITE_MAX_PREVIEW_TABLES = 12;
	private static final String SQLITE_HEADER = "SQLite format 3\u0000";
	private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*$");
	private static final Pattern ENV_LINE_PATTERN = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)\\s*$");
	private static final Pattern XML_TAG_PATTERN = Pattern.compile("(?m)^\\s*<([A-Za-z_][\\w:.-]*)");
	private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "text", "log", "sql");
	private static final Set<String> MARKDOWN_EXTENSIONS = Set.of("md", "markdown");
	private static final Set<String> JSON_EXTENSIONS = Set.of("json");
	private static final Set<String> YAML_EXTENSIONS = Set.of("yaml", "yml");
	private static final Set<String> XML_EXTENSIONS = Set.of("xml");
	private static final Set<String> ENV_EXTENSIONS = Set.of("env");
	private static final Set<String> DELIMITED_TABLE_EXTENSIONS = Set.of("csv", "tsv");
	private static final Set<String> HTML_EXTENSIONS = Set.of("html", "htm");
	private static final Set<String> SQLITE_EXTENSIONS = Set.of("sqlite", "db");

	private final ArtifactStorageService artifactStorageService;
	private final ObjectMapper objectMapper;
	private final Yaml yaml;

	public DocumentPreviewService(ArtifactStorageService artifactStorageService, ObjectMapper objectMapper) {
		this.artifactStorageService = artifactStorageService;
		this.objectMapper = objectMapper;
		this.yaml = new Yaml();
	}

	public boolean isAvailable() {
		return true;
	}

	public DocumentPreviewResult process(UUID jobId, StoredUpload upload) {
		if (!"document".equals(ProcessingFileFamilyResolver.detectFamily(upload))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCUMENT_PREVIEW job принимает только document uploads.");
		}

		var payload = analyze(upload);
		var artifacts = new ArrayList<StoredArtifact>();
		artifacts.add(
			this.artifactStorageService.storeJsonArtifact(
				jobId,
				"document-preview-manifest",
				"document-preview-manifest.json",
				payload
			)
		);

		if ("pdf".equals(payload.layout().mode())) {
			artifacts.add(
				this.artifactStorageService.storeFileArtifact(
					jobId,
					"document-preview-binary",
					buildDocumentArtifactName(upload, "pdf"),
					"application/pdf",
					upload.storagePath()
				)
			);
		}

		return new DocumentPreviewResult(artifacts, "Подготовка просмотра документа");
	}

	public DocumentPreviewPayload analyze(StoredUpload upload) {
		var extension = normalizeExtension(upload.extension());

		return switch (extension) {
			case "pdf" -> buildPdfPreview(upload);
			case "csv" -> buildDelimitedTablePreview(upload, ',', "CSV", "Delimited table preview");
			case "tsv" -> buildDelimitedTablePreview(upload, '\t', "TSV", "Tabbed table preview");
			case "rtf" -> buildRtfPreview(upload);
			case "doc" -> buildDocPreview(upload);
			case "docx" -> buildDocxPreview(upload);
			case "odt" -> buildOdtPreview(upload);
			case "xls" -> buildWorkbookPreview(upload, "XLS", "XLS legacy workbook adapter", true);
			case "xlsx" -> buildWorkbookPreview(upload, "XLSX", "XLSX workbook adapter", false);
			case "pptx" -> buildPptxPreview(upload);
			case "epub" -> buildEpubPreview(upload);
			case "json" -> buildJsonPreview(upload);
			case "yaml", "yml" -> buildYamlPreview(upload);
			case "xml" -> buildXmlPreview(upload);
			case "env" -> buildEnvPreview(upload);
			default -> {
				if (TEXT_EXTENSIONS.contains(extension)) {
					yield buildTextPreview(upload);
				}
				if (MARKDOWN_EXTENSIONS.contains(extension)) {
					yield buildMarkdownPreview(upload);
				}
				if (HTML_EXTENSIONS.contains(extension)) {
					yield buildHtmlPreview(upload);
				}
				if (SQLITE_EXTENSIONS.contains(extension)) {
					yield buildSqlitePreview(upload);
				}
				throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"DOCUMENT_PREVIEW пока не поддерживает формат %s.".formatted(extension)
				);
			}
		};
	}

	private DocumentPreviewPayload buildPdfPreview(StoredUpload upload) {
		try (var document = Loader.loadPDF(upload.storagePath().toFile())) {
			var searchableText = normalizeExtractedText(new PDFTextStripper().getText(document));
			var summary = List.of(
				new DocumentPreviewPayload.DocumentFact("Тип документа", "PDF"),
				new DocumentPreviewPayload.DocumentFact("Страниц", String.valueOf(document.getNumberOfPages())),
				new DocumentPreviewPayload.DocumentFact(
					"Search layer",
					searchableText.isBlank() ? "Browser preview only" : "Backend PDF text extraction"
				)
			);

			return new DocumentPreviewPayload(
				summary,
				searchableText,
				List.of(),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"pdf",
					document.getNumberOfPages(),
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, searchableText, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"PDF server preview"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать PDF document.", exception);
		}
	}

	private DocumentPreviewPayload buildTextPreview(StoredUpload upload) {
		var text = readDocumentText(upload.storagePath());
		return new DocumentPreviewPayload(
			buildTextSummary(resolveTextKindLabel(upload.extension()), text),
			text,
			List.of(),
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"text",
				null,
				text,
				splitParagraphs(text, TEXT_PARAGRAPH_LIMIT),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, text, "txt", null)
			),
			"Text decode adapter"
		);
	}

	private DocumentPreviewPayload buildDelimitedTablePreview(
		StoredUpload upload,
		char preferredDelimiter,
		String label,
		String previewLabel
	) {
		var text = readDocumentText(upload.storagePath());
		var table = parseDelimitedTextDocument(text, preferredDelimiter);
		var warnings = new ArrayList<String>();

		if (table.totalRows() > table.rows().size()) {
			warnings.add(
				"%s показывает первые %s строк. Полное содержимое по-прежнему доступно для поиска и проверки."
					.formatted(label, CSV_PREVIEW_ROW_LIMIT)
			);
		}

		return new DocumentPreviewPayload(
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип документа", label),
				new DocumentPreviewPayload.DocumentFact("Колонки", String.valueOf(table.totalColumns())),
				new DocumentPreviewPayload.DocumentFact("Строки", String.valueOf(table.totalRows())),
				new DocumentPreviewPayload.DocumentFact("Delimiter", describeDelimiter(table.delimiter()))
			),
			text,
			warnings,
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"table",
				null,
				text,
				null,
				table,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, text, "txt", null)
			),
			previewLabel
		);
	}

	private DocumentPreviewPayload buildHtmlPreview(StoredUpload upload) {
		var raw = readDocumentText(upload.storagePath());
		var preview = sanitizeHtmlDocument(raw);

		return new DocumentPreviewPayload(
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип документа", "HTML"),
				new DocumentPreviewPayload.DocumentFact("Headings", String.valueOf(preview.outline().size())),
				new DocumentPreviewPayload.DocumentFact("Текстовых символов", String.valueOf(preview.textContent().length())),
				new DocumentPreviewPayload.DocumentFact("Режим просмотра", "Безопасный встроенный просмотр")
			),
			preview.textContent(),
			preview.warnings(),
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"html",
				null,
				preview.textContent(),
				null,
				null,
				preview.srcDoc(),
				preview.outline(),
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, raw, "html", null)
			),
			"HTML sanitized preview"
		);
	}

	private DocumentPreviewPayload buildMarkdownPreview(StoredUpload upload) {
		var markdown = readDocumentText(upload.storagePath());
		var outline = extractMarkdownOutline(markdown);

		return new DocumentPreviewPayload(
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип документа", "Markdown"),
				new DocumentPreviewPayload.DocumentFact("Headings", String.valueOf(outline.size())),
				new DocumentPreviewPayload.DocumentFact("Строки", String.valueOf(countLines(markdown))),
				new DocumentPreviewPayload.DocumentFact("Режим preview", "Rendered article")
			),
			markdown,
			List.of(),
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"html",
				null,
				markdown,
				null,
				null,
				wrapDocumentHtml(renderMarkdownBody(markdown)),
				outline,
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, markdown, "markdown", null)
			),
			"Markdown reading preview"
		);
	}

	private DocumentPreviewPayload buildJsonPreview(StoredUpload upload) {
		var raw = readDocumentText(upload.storagePath());

		try {
			var node = this.objectMapper.readTree(raw);
			var pretty = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
			var outline = extractJsonOutline(node);
			return buildStructuredPreview(
				upload,
				"JSON",
				pretty,
				outline,
				List.of(
					new DocumentPreviewPayload.DocumentFact("Top-level keys", String.valueOf(countJsonTopLevelEntries(node))),
					new DocumentPreviewPayload.DocumentFact("Режим preview", "Structured config")
				),
				"json",
				"JSON structured preview"
			);
		}
		catch (JsonProcessingException exception) {
			return buildStructuredFallbackPreview(
				upload,
				"JSON",
				raw,
				List.of("Файл не прошёл strict JSON parse, поэтому открыт как текстовая копия без структурного дерева."),
				"json"
			);
		}
	}

	private DocumentPreviewPayload buildYamlPreview(StoredUpload upload) {
		var raw = readDocumentText(upload.storagePath());

		try {
			var parsed = this.yaml.load(raw);
			var normalized = normalizeExtractedText(Optional.ofNullable(this.yaml.dump(parsed)).orElse(raw));
			var outline = extractYamlOutline(normalized);
			return buildStructuredPreview(
				upload,
				"YAML",
				normalized,
				outline,
				List.of(
					new DocumentPreviewPayload.DocumentFact("Разделов", String.valueOf(outline.size())),
					new DocumentPreviewPayload.DocumentFact("Режим preview", "Config review")
				),
				"yaml",
				"YAML structured preview"
			);
		}
		catch (Exception exception) {
			return buildStructuredFallbackPreview(
				upload,
				"YAML",
				raw,
				List.of("YAML не удалось разобрать безопасно, поэтому открыт текстовый draft без структурного дерева."),
				"yaml"
			);
		}
	}

	private DocumentPreviewPayload buildXmlPreview(StoredUpload upload) {
		var raw = readDocumentText(upload.storagePath());

		try {
			var document = parseXml(raw);
			var outline = extractXmlOutline(document);
			var rootName = Optional.ofNullable(document.getDocumentElement())
				.map(org.w3c.dom.Element::getTagName)
				.orElse("XML");

			return new DocumentPreviewPayload(
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", "XML"),
					new DocumentPreviewPayload.DocumentFact("Root node", rootName),
					new DocumentPreviewPayload.DocumentFact("Outline entries", String.valueOf(outline.size())),
					new DocumentPreviewPayload.DocumentFact("Режим preview", "Schema read")
				),
				raw,
				List.of(),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"html",
					null,
					raw,
					null,
					null,
					wrapStructuredConfigHtml("""
						<section class="config-sheet">
						  <div class="config-sheet__meta">
						    <span>Root node</span>
						    <strong>%s</strong>
						  </div>
						  <pre>%s</pre>
						</section>
						""".formatted(escapeHtml(rootName), escapeHtml(raw))),
					outline,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, raw, "xml", null)
				),
				"XML structure preview"
			);
		}
		catch (ResponseStatusException exception) {
			return buildStructuredFallbackPreview(
				upload,
				"XML",
				raw,
				List.of("XML не удалось разобрать безопасно, поэтому открыт текстовый draft для ручной проверки."),
				"xml"
			);
		}
	}

	private DocumentPreviewPayload buildEnvPreview(StoredUpload upload) {
		var raw = readDocumentText(upload.storagePath());
		var rows = new ArrayList<List<String>>();
		var warnings = new ArrayList<String>();

		for (String line : raw.split("\\R")) {
			var trimmed = line.trim();
			if (trimmed.isBlank() || trimmed.startsWith("#")) {
				continue;
			}

			var matcher = ENV_LINE_PATTERN.matcher(line);
			if (!matcher.matches()) {
				warnings.add("Некоторые строки .env не похожи на KEY=value и оставлены только в raw draft.");
				break;
			}

			rows.add(List.of(matcher.group(1), trimEnvValue(matcher.group(2))));
		}

		var previewRows = rows.stream().limit(CSV_PREVIEW_ROW_LIMIT).toList();
		var table = new DocumentPreviewPayload.DocumentTablePreview(
			List.of("Key", "Value"),
			previewRows,
			rows.size(),
			2,
			"="
		);

		return new DocumentPreviewPayload(
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип документа", ".env"),
				new DocumentPreviewPayload.DocumentFact("Ключей", String.valueOf(rows.size())),
				new DocumentPreviewPayload.DocumentFact("Комментарии", raw.contains("#") ? "Есть" : "Нет"),
				new DocumentPreviewPayload.DocumentFact("Режим preview", "Config table")
			),
			raw,
			warnings,
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"table",
				null,
				raw,
				null,
				table,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, raw, "env", null)
			),
			"Environment config preview"
		);
	}

	private DocumentPreviewPayload buildStructuredPreview(
		StoredUpload upload,
		String kind,
		String normalizedText,
		List<DocumentPreviewPayload.DocumentOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> extraFacts,
		String editorFormatId,
		String previewLabel
	) {
		var facts = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		facts.add(new DocumentPreviewPayload.DocumentFact("Тип документа", kind));
		facts.add(new DocumentPreviewPayload.DocumentFact("Строки", String.valueOf(countLines(normalizedText))));
		facts.add(new DocumentPreviewPayload.DocumentFact("Символы", String.valueOf(normalizedText.length())));
		facts.addAll(extraFacts);

		return new DocumentPreviewPayload(
			List.copyOf(facts),
			normalizedText,
			List.of(),
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"html",
				null,
				normalizedText,
				null,
				null,
				wrapStructuredConfigHtml("""
					<section class="config-sheet">
					  <pre>%s</pre>
					</section>
					""".formatted(escapeHtml(normalizedText))),
				outline,
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, normalizedText, editorFormatId, null)
			),
			previewLabel
		);
	}

	private DocumentPreviewPayload buildStructuredFallbackPreview(
		StoredUpload upload,
		String kind,
		String raw,
		List<String> warnings,
		String editorFormatId
	) {
		var normalized = normalizeExtractedText(raw);

		return new DocumentPreviewPayload(
			buildTextSummary(kind, normalized),
			normalized,
			warnings,
			new DocumentPreviewPayload.DocumentLayoutPayload(
				"text",
				null,
				normalized,
				splitParagraphs(normalized, TEXT_PARAGRAPH_LIMIT),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				buildEditableDraft(upload, normalized, editorFormatId, null)
			),
			kind + " text fallback"
		);
	}

	private DocumentPreviewPayload buildRtfPreview(StoredUpload upload) {
		try {
			var kit = new RTFEditorKit();
			var document = new DefaultStyledDocument();
			try (InputStream inputStream = Files.newInputStream(upload.storagePath())) {
				kit.read(inputStream, document, 0);
			}

			var text = normalizeExtractedText(document.getText(0, document.getLength()));
			return new DocumentPreviewPayload(
				buildTextSummary("RTF", text),
				text,
				List.of("Показан текст документа без исходного оформления и вложенных объектов."),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"text",
					null,
					text,
					splitParagraphs(text, TEXT_PARAGRAPH_LIMIT),
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, text, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"RTF text extraction"
			);
		}
		catch (IOException | BadLocationException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось извлечь текст из RTF document.", exception);
		}
	}

	private DocumentPreviewPayload buildDocPreview(StoredUpload upload) {
		try (var inputStream = Files.newInputStream(upload.storagePath());
			var document = new HWPFDocument(inputStream);
			var extractor = new WordExtractor(document)) {
			var text = normalizeExtractedText(extractor.getText());
			return new DocumentPreviewPayload(
				buildTextSummary("DOC", text),
				text,
				List.of("Для старого DOC доступен текст и базовая структура, но сложное оформление, изображения и правки могут отличаться от оригинала."),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"text",
					null,
					text,
					splitParagraphs(text, TEXT_PARAGRAPH_LIMIT),
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, text, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"DOC legacy text adapter"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось извлечь текст из DOC document.", exception);
		}
	}

	private DocumentPreviewPayload buildDocxPreview(StoredUpload upload) {
		try (var inputStream = Files.newInputStream(upload.storagePath());
			var document = new XWPFDocument(inputStream)) {
			var blocks = new ArrayList<NarrativeBlock>();

			for (IBodyElement bodyElement : document.getBodyElements()) {
				if (bodyElement instanceof XWPFParagraph paragraph) {
					collectDocxParagraph(paragraph, blocks);
				} else if (bodyElement instanceof XWPFTable table) {
					var rows = extractDocxTable(table);
					if (!rows.isEmpty()) {
						blocks.add(NarrativeBlock.table(rows));
					}
				}
			}

			if (blocks.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCX document не содержит читаемых блоков.");
			}

			var searchableText = buildNarrativeText(blocks);
			var outline = buildNarrativeOutline(blocks, "docx-heading-");
			var tableCount = blocks.stream().filter(block -> "table".equals(block.kind())).count();

			return new DocumentPreviewPayload(
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", "DOCX"),
					new DocumentPreviewPayload.DocumentFact("Блоки", String.valueOf(blocks.size())),
					new DocumentPreviewPayload.DocumentFact("Headings", String.valueOf(outline.size())),
					new DocumentPreviewPayload.DocumentFact("Таблицы", String.valueOf(tableCount))
				),
				searchableText,
				List.of("Показано содержание документа и таблицы, но сложные стили, колонтитулы и изображения могут быть упрощены."),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"html",
					null,
					searchableText,
					null,
					null,
					wrapDocumentHtml(renderNarrativeBlocks(blocks)),
					outline,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, searchableText, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"DOCX OOXML adapter"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать DOCX document.", exception);
		}
	}

	private DocumentPreviewPayload buildWorkbookPreview(
		StoredUpload upload,
		String label,
		String previewLabel,
		boolean legacyWorkbook
	) {
		try (var inputStream = Files.newInputStream(upload.storagePath());
			Workbook workbook = WorkbookFactory.create(inputStream)) {
			var formatter = new DataFormatter(Locale.ROOT);
			var sheets = new ArrayList<DocumentPreviewPayload.DocumentSheetPreview>();

			for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex += 1) {
				var sheet = workbook.getSheetAt(sheetIndex);
				var table = buildWorkbookTable(formatter, sheet);
				if (table.totalColumns() == 0 && table.totalRows() == 0 && table.rows().isEmpty()) {
					continue;
				}

				sheets.add(
					new DocumentPreviewPayload.DocumentSheetPreview(
						(label.toLowerCase(Locale.ROOT) + "-sheet-" + (sheetIndex + 1)),
						sheet.getSheetName(),
						table
					)
				);
			}

			if (sheets.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s document не содержит читаемых листов.".formatted(label));
			}

			var searchableText = sheets.stream()
				.map(sheet -> sheet.name() + "\n" + renderTableRowsForSearch(sheet.table()))
				.collect(java.util.stream.Collectors.joining("\n\n"));
			var warnings = legacyWorkbook
				? List.of("Для XLS показаны данные листов, но макросы, стили и диаграммы в просмотр не входят.")
				: List.of("Для XLSX показаны данные листов, но формулы, объединённые ячейки, стили и диаграммы могут отличаться от исходника.");

			return new DocumentPreviewPayload(
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", label),
					new DocumentPreviewPayload.DocumentFact("Sheets", String.valueOf(sheets.size())),
					new DocumentPreviewPayload.DocumentFact("Rows", String.valueOf(sheets.getFirst().table().totalRows())),
					new DocumentPreviewPayload.DocumentFact("Columns", String.valueOf(sheets.getFirst().table().totalColumns()))
				),
				searchableText,
				warnings,
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"workbook",
					null,
					searchableText,
					null,
					null,
					null,
					null,
					sheets,
					0,
					null,
					null,
					null,
					null
				),
				previewLabel
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать %s workbook.".formatted(label), exception);
		}
	}

	private DocumentPreviewPayload buildPptxPreview(StoredUpload upload) {
		try (var inputStream = Files.newInputStream(upload.storagePath());
			var slideShow = new XMLSlideShow(inputStream)) {
			var slides = new ArrayList<DocumentPreviewPayload.DocumentSlidePreview>();
			var searchableParts = new ArrayList<String>();

			for (int slideIndex = 0; slideIndex < slideShow.getSlides().size(); slideIndex += 1) {
				var slide = slideShow.getSlides().get(slideIndex);
				var title = Optional.ofNullable(normalizeText(slide.getTitle()))
					.filter(value -> !value.isBlank())
					.orElse("Slide " + (slideIndex + 1));
				var bullets = extractPptxBullets(slide, title);
				slides.add(
					new DocumentPreviewPayload.DocumentSlidePreview(
						"slide-" + (slideIndex + 1),
						title,
						bullets
					)
				);
				searchableParts.add(title);
				searchableParts.addAll(bullets);
			}

			if (slides.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PPTX document не содержит читаемых слайдов.");
			}

			var searchableText = normalizeExtractedText(String.join("\n\n", searchableParts));
			return new DocumentPreviewPayload(
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", "PPTX"),
					new DocumentPreviewPayload.DocumentFact("Слайды", String.valueOf(slides.size())),
					new DocumentPreviewPayload.DocumentFact(
						"Текстовых блоков",
						String.valueOf(slides.stream().mapToInt(slide -> slide.bullets().size()).sum())
					)
				),
				searchableText,
				List.of("Показаны заголовки и основные пункты слайдов, но оформление, анимации и встроенные медиа в просмотр не входят."),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"slides",
					null,
					searchableText,
					null,
					null,
					null,
					null,
					null,
					null,
					slides,
					null,
					null,
					buildEditableDraft(upload, searchableText, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"PPTX slide adapter"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать PPTX document.", exception);
		}
	}

	private DocumentPreviewPayload buildOdtPreview(StoredUpload upload) {
		try (var zipFile = new ZipFile(upload.storagePath().toFile())) {
			var content = readZipEntryAsText(zipFile, "content.xml")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODT adapter не нашёл content.xml внутри архива документа."));
			var documentRoot = parseXml(content);
			var textRoot = findFirstElementByLocalName(documentRoot.getDocumentElement(), "text")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODT adapter не нашёл office:text внутри content.xml."));

			var blocks = new ArrayList<NarrativeBlock>();
			forEachChildElement(textRoot, child -> collectOdtBlocks(child, blocks, ""));

			if (blocks.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODT document не содержит читаемых narrative blocks.");
			}

			var searchableText = buildNarrativeText(blocks);
			var outline = buildNarrativeOutline(blocks, "odt-heading-");
			var tableCount = blocks.stream().filter(block -> "table".equals(block.kind())).count();

			return new DocumentPreviewPayload(
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", "ODT"),
					new DocumentPreviewPayload.DocumentFact("Блоки", String.valueOf(blocks.size())),
					new DocumentPreviewPayload.DocumentFact("Headings", String.valueOf(outline.size())),
					new DocumentPreviewPayload.DocumentFact("Таблицы", String.valueOf(tableCount))
				),
				searchableText,
				List.of("Показан текст, заголовки и таблицы, но оформление, изображения и сноски могут быть упрощены."),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"html",
					null,
					searchableText,
					null,
					null,
					wrapDocumentHtml(renderNarrativeBlocks(blocks)),
					outline,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, searchableText, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"ODT archive adapter"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать ODT document.", exception);
		}
	}

	private DocumentPreviewPayload buildEpubPreview(StoredUpload upload) {
		try (var zipFile = new ZipFile(upload.storagePath().toFile())) {
			var containerContent = readZipEntryAsText(zipFile, "META-INF/container.xml")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB adapter не нашёл META-INF/container.xml внутри архива."));
			var containerRoot = parseXml(containerContent);
			var rootFile = findFirstElementByLocalName(containerRoot.getDocumentElement(), "rootfile")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB adapter не смог определить package path."));
			var packagePath = Optional.ofNullable(rootFile.getAttribute("full-path"))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB adapter не смог определить OPF package path из container.xml."));
			var packageContent = readZipEntryAsText(zipFile, packagePath)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB adapter не нашёл package-документ по пути %s.".formatted(packagePath)));
			var packageRoot = parseXml(packageContent);
			var manifest = readEpubManifest(packageRoot.getDocumentElement());
			var spineItems = findElementsByLocalName(packageRoot.getDocumentElement(), "itemref");

			var outline = new ArrayList<DocumentPreviewPayload.DocumentOutlineItem>();
			var sectionHtml = new ArrayList<String>();
			var sectionTexts = new ArrayList<String>();
			int chapterCount = 0;

			for (int spineIndex = 0; spineIndex < spineItems.size(); spineIndex += 1) {
				var itemRef = spineItems.get(spineIndex);
				var idRef = Optional.ofNullable(itemRef.getAttribute("idref")).map(String::trim).orElse("");
				var manifestItem = manifest.get(idRef);

				if (manifestItem == null || !isRenderableEpubItem(manifestItem.mediaType())) {
					continue;
				}

				var chapterPath = resolveArchivePath(packagePath, manifestItem.href());
				var chapterContent = readZipEntryAsText(zipFile, chapterPath).orElse(null);
				if (chapterContent == null) {
					continue;
				}

				var chapter = parseEpubChapter(chapterContent, spineIndex, manifestItem);
				if (chapter.text().isBlank()) {
					continue;
				}

				chapterCount += 1;
				sectionTexts.add(chapter.text());
				sectionHtml.add("<section class=\"epub-chapter\">" + chapter.html() + "</section>");
				outline.addAll(chapter.outline());
			}

			if (chapterCount == 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EPUB adapter не нашёл ни одной главы с читаемым текстовым содержимым.");
			}

			var title = readFirstMetadataValue(packageRoot.getDocumentElement(), "title").orElse(upload.originalFileName());
			var author = readFirstMetadataValue(packageRoot.getDocumentElement(), "creator").orElse("Не определён");
			var language = readFirstMetadataValue(packageRoot.getDocumentElement(), "language").orElse("Не определён");
			var searchableText = normalizeExtractedText(String.join("\n\n", sectionTexts));

			return new DocumentPreviewPayload(
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", "EPUB"),
					new DocumentPreviewPayload.DocumentFact("Название", title),
					new DocumentPreviewPayload.DocumentFact("Автор", author),
					new DocumentPreviewPayload.DocumentFact("Язык", language),
					new DocumentPreviewPayload.DocumentFact("Главы", String.valueOf(chapterCount))
				),
				searchableText,
				List.of("Показано содержимое книги в режиме чтения, но тема оформления, заметки и медиа-слои не воспроизводятся полностью."),
				new DocumentPreviewPayload.DocumentLayoutPayload(
					"html",
					null,
					searchableText,
					null,
					null,
					wrapDocumentHtml(String.join("", sectionHtml)),
					outline,
					null,
					null,
					null,
					null,
					null,
					buildEditableDraft(upload, searchableText, "txt", buildDocumentArtifactName(upload, "txt"))
				),
				"EPUB reading adapter"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать EPUB document.", exception);
		}
	}

	private DocumentPreviewPayload buildSqlitePreview(StoredUpload upload) {
		try {
			if (!looksLikeSqlite(upload.storagePath())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл распознан как DB/SQLite по расширению, но его сигнатура не похожа на SQLite container.");
			}

			try (var connection = DriverManager.getConnection("jdbc:sqlite:" + upload.storagePath().toAbsolutePath())) {
				var tableEntries = queryRows(
					connection,
					"SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
				);
				var viewCount = querySingleLong(
					connection,
					"SELECT COUNT(*) AS count FROM sqlite_master WHERE type = 'view'"
				);
				var triggerCount = querySingleLong(
					connection,
					"SELECT COUNT(*) AS count FROM sqlite_master WHERE type = 'trigger'"
				);
				var tables = tableEntries.stream()
					.limit(SQLITE_MAX_PREVIEW_TABLES)
					.map(entry -> buildDatabaseTablePreview(
						connection,
						String.valueOf(entry.getOrDefault("name", "table")),
						String.valueOf(entry.getOrDefault("sql", ""))
					))
					.toList();
				var searchableText = tables.stream()
					.map(table ->
						table.name() + "\n" + table.schemaSql() + "\n" +
						table.columns().stream()
							.map(column -> (column.name() + " " + column.type()).trim())
							.collect(java.util.stream.Collectors.joining("\n")) + "\n" +
						renderTableRowsForSearch(table.sample())
					)
					.collect(java.util.stream.Collectors.joining("\n\n"));

				var warnings = new ArrayList<String>();
				warnings.add("Просмотр базы доступен только для чтения: можно изучить структуру и примеры строк без изменения файла.");
				if (tableEntries.size() > SQLITE_MAX_PREVIEW_TABLES) {
					warnings.add(
						"Для скорости показаны первые %s таблиц из %s, а полный список остаётся доступен через поиск по содержимому."
							.formatted(SQLITE_MAX_PREVIEW_TABLES, tableEntries.size())
					);
				}

				return new DocumentPreviewPayload(
					List.of(
						new DocumentPreviewPayload.DocumentFact("Тип документа", "SQLite"),
						new DocumentPreviewPayload.DocumentFact("Таблицы", String.valueOf(tableEntries.size())),
						new DocumentPreviewPayload.DocumentFact("Views", String.valueOf(viewCount == null ? 0 : viewCount)),
						new DocumentPreviewPayload.DocumentFact("Triggers", String.valueOf(triggerCount == null ? 0 : triggerCount))
					),
					searchableText,
					warnings,
					new DocumentPreviewPayload.DocumentLayoutPayload(
						"database",
						null,
						searchableText,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						tables,
						0,
						null
					),
					"SQLite database adapter"
				);
			}
		}
		catch (SQLException | IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать SQLite database preview.", exception);
		}
	}

	private DocumentPreviewPayload.DocumentTablePreview parseDelimitedTextDocument(String text, char preferredDelimiter) {
		var delimiter = detectDelimiter(text, preferredDelimiter);
		var rows = new ArrayList<List<String>>();

		try (var parser = CSVParser.parse(
			text,
			CSVFormat.DEFAULT.builder()
				.setDelimiter(delimiter)
				.setIgnoreSurroundingSpaces(false)
				.setTrim(false)
				.build()
		)) {
			for (var record : parser) {
				var values = new ArrayList<String>();
				for (String value : record) {
					values.add(normalizeTableCell(value));
				}
				rows.add(trimTrailingEmptyCells(values));
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать CSV/TSV документ.", exception);
		}

		var normalizedRows = rows.stream()
			.filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
			.toList();
		if (normalizedRows.isEmpty()) {
			return new DocumentPreviewPayload.DocumentTablePreview(List.of(), List.of(), 0, 0, String.valueOf(delimiter));
		}

		int maxColumns = normalizedRows.stream().mapToInt(List::size).max().orElse(0);
		var headerRow = normalizedRows.getFirst();
		var columns = new ArrayList<String>();
		for (int columnIndex = 0; columnIndex < maxColumns; columnIndex += 1) {
			var value = columnIndex < headerRow.size() ? headerRow.get(columnIndex).trim() : "";
			columns.add(value.isBlank() ? toSpreadsheetColumnLabel(columnIndex) : value);
		}

		var previewRows = normalizedRows.stream()
			.skip(1)
			.limit(CSV_PREVIEW_ROW_LIMIT)
			.map(row -> padRow(row, maxColumns))
			.toList();

		return new DocumentPreviewPayload.DocumentTablePreview(
			columns,
			previewRows,
			Math.max(normalizedRows.size() - 1, 0),
			maxColumns,
			String.valueOf(delimiter)
		);
	}

	private HtmlPreview sanitizeHtmlDocument(String rawHtml) {
		var dirtyDocument = Jsoup.parse(rawHtml);
		var hadUnsafeNodes = !dirtyDocument.select("script,style,iframe,object,embed").isEmpty();
		var cleaner = new Cleaner(
			Safelist.relaxed()
				.addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "section", "article")
				.addAttributes("td", "colspan", "rowspan")
				.addAttributes("th", "colspan", "rowspan")
		);
		var cleanedDocument = cleaner.clean(dirtyDocument);
		var outline = cleanedDocument.select("h1, h2, h3, h4, h5, h6").stream()
			.map(this::toOutlineItem)
			.filter(Objects::nonNull)
			.toList();
		var textContent = normalizeExtractedText(cleanedDocument.text());
		var warnings = hadUnsafeNodes
			? List.of("Потенциально опасные HTML-узлы удалены для безопасного просмотра.")
			: List.<String>of();

		return new HtmlPreview(
			textContent,
			wrapDocumentHtml(Optional.ofNullable(cleanedDocument.body()).map(Element::html).orElse("")),
			outline,
			warnings
		);
	}

	private DocumentPreviewPayload.DocumentOutlineItem toOutlineItem(Element element) {
		var text = normalizeText(element.text());
		if (text.isBlank()) {
			return null;
		}
		var level = switch (element.tagName().toLowerCase(Locale.ROOT)) {
			case "h1" -> 1;
			case "h2" -> 2;
			case "h3" -> 3;
			case "h4" -> 4;
			case "h5" -> 5;
			default -> 6;
		};
		return new DocumentPreviewPayload.DocumentOutlineItem("html-heading-" + element.elementSiblingIndex(), text, level);
	}

	private void collectDocxParagraph(XWPFParagraph paragraph, List<NarrativeBlock> blocks) {
		var text = normalizeText(paragraph.getText());
		if (text.isBlank()) {
			return;
		}

		var headingLevel = resolveDocxHeadingLevel(paragraph);
		if (headingLevel != null) {
			blocks.add(NarrativeBlock.heading(text, headingLevel));
		} else {
			blocks.add(NarrativeBlock.paragraph(text));
		}
	}

	private Integer resolveDocxHeadingLevel(XWPFParagraph paragraph) {
		var style = Optional.ofNullable(paragraph.getStyle()).orElse("").toLowerCase(Locale.ROOT);
		if (style.startsWith("heading")) {
			var digits = style.replaceAll("\\D+", "");
			if (!digits.isBlank()) {
				return clampHeadingLevel(Integer.parseInt(digits));
			}
			return 1;
		}

		return null;
	}

	private List<List<String>> extractDocxTable(XWPFTable table) {
		return table.getRows().stream()
			.map(row -> row.getTableCells().stream()
				.map(cell -> normalizeText(cell.getText()))
				.toList()
			)
			.filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
			.toList();
	}

	private DocumentPreviewPayload.DocumentTablePreview buildWorkbookTable(DataFormatter formatter, Sheet sheet) {
		var normalizedRows = new ArrayList<List<String>>();
		int maxColumns = 0;

		for (Row row : sheet) {
			List<String> values = new ArrayList<>();
			for (int cellIndex = 0; cellIndex < Math.max(row.getLastCellNum(), 0); cellIndex += 1) {
				Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
				values.add(cell == null ? "" : normalizeTableCell(formatter.formatCellValue(cell)));
			}
			values = trimTrailingEmptyCells(values);
			if (values.stream().noneMatch(value -> !value.isBlank())) {
				continue;
			}
			maxColumns = Math.max(maxColumns, values.size());
			normalizedRows.add(values);
		}

		if (normalizedRows.isEmpty()) {
			return new DocumentPreviewPayload.DocumentTablePreview(List.of(), List.of(), 0, 0, "");
		}

		var headerRow = normalizedRows.getFirst();
		var columns = new ArrayList<String>();
		for (int columnIndex = 0; columnIndex < maxColumns; columnIndex += 1) {
			var value = columnIndex < headerRow.size() ? headerRow.get(columnIndex).trim() : "";
			columns.add(value.isBlank() ? toSpreadsheetColumnLabel(columnIndex) : value);
		}

		final int finalMaxColumns = maxColumns;
		final int previewColumnCount = Math.min(maxColumns, WORKBOOK_PREVIEW_COLUMN_LIMIT);
		var previewRows = normalizedRows.stream()
			.skip(1)
			.limit(WORKBOOK_PREVIEW_ROW_LIMIT)
			.map(row -> padRow(row, finalMaxColumns).subList(0, previewColumnCount))
			.toList();

		return new DocumentPreviewPayload.DocumentTablePreview(
			columns.subList(0, previewColumnCount),
			previewRows,
			Math.max(normalizedRows.size() - 1, 0),
			maxColumns,
			""
		);
	}

	private List<String> extractPptxBullets(XSLFSlide slide, String title) {
		var bullets = new ArrayList<String>();
		for (XSLFShape shape : slide.getShapes()) {
			if (!(shape instanceof XSLFTextShape textShape)) {
				continue;
			}

			for (var paragraph : textShape.getTextParagraphs()) {
				var line = normalizeText(paragraph.getText());
				if (line.isBlank() || line.equals(title)) {
					continue;
				}

				if (paragraph.getIndentLevel() > 0) {
					line = "• " + line;
				}
				bullets.add(line);
			}
		}
		return bullets;
	}

	private void collectOdtBlocks(org.w3c.dom.Element node, List<NarrativeBlock> blocks, String bulletPrefix) {
		var localName = node.getLocalName();
		if (localName == null) {
			return;
		}

		switch (localName) {
			case "h" -> {
				var text = normalizeText(readOdtInlineText(node));
				if (text.isBlank()) {
					return;
				}

				var level = clampHeadingLevel(parseInteger(node.getAttribute("text:outline-level"), 1));
				blocks.add(NarrativeBlock.heading(text, level));
			}
			case "p" -> {
				var text = normalizeText(readOdtInlineText(node));
				if (text.isBlank()) {
					return;
				}
				blocks.add(NarrativeBlock.paragraph((bulletPrefix == null || bulletPrefix.isBlank()) ? text : bulletPrefix + text));
			}
			case "list" -> forEachChildElement(node, child -> collectOdtBlocks(child, blocks, bulletPrefix.isBlank() ? "• " : bulletPrefix));
			case "list-item", "section", "span", "body" -> forEachChildElement(node, child -> collectOdtBlocks(child, blocks, bulletPrefix));
			case "table" -> {
				var rows = parseOdtTable(node);
				if (!rows.isEmpty()) {
					blocks.add(NarrativeBlock.table(rows));
				}
			}
			case "sequence-decls", "tracked-changes", "variable-decls" -> {
				return;
			}
			default -> forEachChildElement(node, child -> collectOdtBlocks(child, blocks, bulletPrefix));
		}
	}

	private List<List<String>> parseOdtTable(org.w3c.dom.Element tableNode) {
		var rows = new ArrayList<List<String>>();
		forEachChildElement(tableNode, rowNode -> {
			if (!"table-row".equals(rowNode.getLocalName())) {
				return;
			}
			var rawCells = new ArrayList<String>();
			forEachChildElement(rowNode, cellNode -> {
				if (!"table-cell".equals(cellNode.getLocalName())) {
					return;
				}
				var cellText = new ArrayList<String>();
				forEachChildElement(cellNode, paragraphNode -> {
					var localName = paragraphNode.getLocalName();
					if ("p".equals(localName) || "h".equals(localName)) {
						cellText.add(readOdtInlineText(paragraphNode));
					}
				});
				rawCells.add(normalizeText(String.join(" ", cellText)));
			});
			var cells = trimTrailingEmptyCells(rawCells);
			if (cells.stream().anyMatch(value -> !value.isBlank())) {
				rows.add(cells);
			}
		});
		return rows;
	}

	private String readOdtInlineText(Node node) {
		if (node.getNodeType() == Node.TEXT_NODE) {
			return Optional.ofNullable(node.getTextContent()).orElse("");
		}
		if (!(node instanceof org.w3c.dom.Element element)) {
			return "";
		}

		var localName = element.getLocalName();
		if ("tab".equals(localName)) {
			return "\t";
		}
		if ("line-break".equals(localName)) {
			return "\n";
		}
		if ("s".equals(localName)) {
			int count = parseInteger(element.getAttribute("text:c"), 1);
			return " ".repeat(Math.max(count, 1));
		}

		var builder = new StringBuilder();
		var children = element.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			builder.append(readOdtInlineText(children.item(index)));
		}
		return builder.toString();
	}

	private Map<String, EpubManifestItem> readEpubManifest(org.w3c.dom.Element packageRoot) {
		var manifest = new LinkedHashMap<String, EpubManifestItem>();
		for (var item : findElementsByLocalName(packageRoot, "item")) {
			var id = Optional.ofNullable(item.getAttribute("id")).orElse("").trim();
			var href = Optional.ofNullable(item.getAttribute("href")).orElse("").trim();
			var mediaType = Optional.ofNullable(item.getAttribute("media-type")).orElse("").trim();
			var properties = Optional.ofNullable(item.getAttribute("properties")).orElse("")
				.trim()
				.lines()
				.flatMap(line -> java.util.Arrays.stream(line.split("\\s+")))
				.filter(value -> !value.isBlank())
				.toList();

			if (!id.isBlank() && !href.isBlank()) {
				manifest.put(id, new EpubManifestItem(id, href, mediaType, properties));
			}
		}
		return manifest;
	}

	private boolean isRenderableEpubItem(String mediaType) {
		return "application/xhtml+xml".equals(mediaType) || "text/html".equals(mediaType) || "application/xml".equals(mediaType);
	}

	private EpubChapter parseEpubChapter(String chapterContent, int chapterIndex, EpubManifestItem manifestItem) {
		var document = Jsoup.parse(chapterContent);
		document.select("script,style,noscript").remove();
		var body = document.body();
		var cleaner = new Cleaner(
			Safelist.relaxed()
				.addTags("section", "article")
				.addAttributes("td", "colspan", "rowspan")
				.addAttributes("th", "colspan", "rowspan")
		);
		var sanitizedDocument = cleaner.clean(Jsoup.parseBodyFragment(body.html()));
		var sanitizedBody = sanitizedDocument.body();
		var bodyHtml = sanitizedBody.html();
		var outline = sanitizedBody.select("h1, h2, h3, h4, h5, h6").stream()
			.map(element -> {
				var label = normalizeText(element.text());
				if (label.isBlank()) {
					return null;
				}
				return new DocumentPreviewPayload.DocumentOutlineItem(
					"epub-" + (chapterIndex + 1) + "-" + (element.elementSiblingIndex() + 1),
					label,
					clampHeadingLevel(parseInteger(element.tagName().substring(1), 1))
				);
			})
			.filter(Objects::nonNull)
			.toList();
		var text = normalizeExtractedText(body.text());
		var html = bodyHtml.isBlank()
			? "<p>" + escapeHtml(Optional.ofNullable(manifestItem.id()).orElse("Chapter " + (chapterIndex + 1))) + "</p>"
			: bodyHtml;
		return new EpubChapter(text, html, outline);
	}

	private Optional<String> readFirstMetadataValue(org.w3c.dom.Element root, String localName) {
		return findElementsByLocalName(root, localName).stream()
			.map(node -> normalizeText(node.getTextContent()))
			.filter(value -> !value.isBlank())
			.findFirst();
	}

	private DocumentPreviewPayload.DocumentDatabaseTablePreview buildDatabaseTablePreview(
		java.sql.Connection connection,
		String tableName,
		String schemaSql
	) {
		var quotedName = quoteSqlIdentifier(tableName);
		var columns = queryRows(connection, "PRAGMA table_info(" + quotedName + ")").stream()
			.map(column -> new DocumentPreviewPayload.DocumentDatabaseColumnPreview(
				String.valueOf(column.getOrDefault("name", "")),
				String.valueOf(column.getOrDefault("type", "")),
				asLong(column.get("notnull")) == 0,
				asLong(column.get("pk")) > 0,
				column.get("dflt_value") == null ? "—" : String.valueOf(column.get("dflt_value"))
			))
			.toList();
		var rowCount = querySingleLong(connection, "SELECT COUNT(*) AS count FROM " + quotedName);
		var sampleRows = queryRows(connection, "SELECT * FROM " + quotedName + " LIMIT " + SQLITE_SAMPLE_ROW_LIMIT);
		var sample = buildDatabaseSample(columns, sampleRows, rowCount);

		return new DocumentPreviewPayload.DocumentDatabaseTablePreview(
			"sqlite-table-" + tableName,
			tableName,
			rowCount,
			schemaSql == null || schemaSql.isBlank() ? "CREATE TABLE " + tableName + " (...)" : schemaSql,
			columns,
			sample
		);
	}

	private DocumentPreviewPayload.DocumentTablePreview buildDatabaseSample(
		List<DocumentPreviewPayload.DocumentDatabaseColumnPreview> columns,
		List<Map<String, Object>> sampleRows,
		Long rowCount
	) {
		var visibleColumns = columns.stream()
			.map(DocumentPreviewPayload.DocumentDatabaseColumnPreview::name)
			.limit(SQLITE_SAMPLE_COLUMN_LIMIT)
			.toList();
		var rows = sampleRows.stream()
			.map(row -> visibleColumns.stream()
				.map(column -> formatSqlValue(row.get(column)))
				.toList()
			)
			.toList();

		return new DocumentPreviewPayload.DocumentTablePreview(
			visibleColumns,
			rows,
			rowCount == null ? rows.size() : rowCount.intValue(),
			columns.size(),
			""
		);
	}

	private List<Map<String, Object>> queryRows(java.sql.Connection connection, String sql) {
		try (var statement = connection.createStatement();
			var resultSet = statement.executeQuery(sql)) {
			var rows = new ArrayList<Map<String, Object>>();
			var metadata = resultSet.getMetaData();
			while (resultSet.next()) {
				var row = new LinkedHashMap<String, Object>();
				for (int columnIndex = 1; columnIndex <= metadata.getColumnCount(); columnIndex += 1) {
					row.put(metadata.getColumnLabel(columnIndex), resultSet.getObject(columnIndex));
				}
				rows.add(row);
			}
			return rows;
		}
		catch (SQLException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось выполнить SQLite introspection query.", exception);
		}
	}

	private Long querySingleLong(java.sql.Connection connection, String sql) {
		var rows = queryRows(connection, sql);
		if (rows.isEmpty()) {
			return null;
		}
		return asLong(rows.getFirst().values().stream().findFirst().orElse(null));
	}

	private Long asLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String string && !string.isBlank()) {
			try {
				return Long.parseLong(string);
			}
			catch (NumberFormatException ignored) {
			}
		}
		return 0L;
	}

	private boolean looksLikeSqlite(Path path) throws IOException {
		try (var inputStream = Files.newInputStream(path)) {
			var signature = inputStream.readNBytes(SQLITE_HEADER.length());
			return SQLITE_HEADER.equals(new String(signature, StandardCharsets.US_ASCII));
		}
	}

	private String formatSqlValue(Object value) {
		if (value == null) {
			return "NULL";
		}
		if (value instanceof byte[] bytes) {
			return "BLOB(%s bytes)".formatted(bytes.length);
		}
		return String.valueOf(value);
	}

	private String quoteSqlIdentifier(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	private DocumentPreviewPayload.DocumentEditableDraft buildEditableDraft(
		StoredUpload upload,
		String text,
		String editorFormatId,
		String fileNameOverride
	) {
		if (text == null || text.isBlank()) {
			return null;
		}

		return new DocumentPreviewPayload.DocumentEditableDraft(
			normalizeExtractedText(text),
			fileNameOverride != null ? fileNameOverride : upload.originalFileName(),
			editorFormatId
		);
	}

	private String resolveTextKindLabel(String extension) {
		return switch (normalizeExtension(extension)) {
			case "log" -> "LOG";
			case "sql" -> "SQL";
			default -> "TXT";
		};
	}

	private String trimEnvValue(String value) {
		var trimmed = Optional.ofNullable(value).orElse("").trim();
		if (
			(trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
			(trimmed.startsWith("'") && trimmed.endsWith("'"))
		) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	private List<DocumentPreviewPayload.DocumentOutlineItem> extractMarkdownOutline(String markdown) {
		var outline = new ArrayList<DocumentPreviewPayload.DocumentOutlineItem>();
		var matcher = MARKDOWN_HEADING_PATTERN.matcher(markdown);
		int index = 0;

		while (matcher.find()) {
			index += 1;
			outline.add(
				new DocumentPreviewPayload.DocumentOutlineItem(
					"md-heading-" + index,
					normalizeText(matcher.group(2)),
					clampHeadingLevel(matcher.group(1).length())
				)
			);
		}

		return List.copyOf(outline);
	}

	private String renderMarkdownBody(String markdown) {
		var lines = markdown.split("\\R");
		var html = new StringBuilder();
		boolean insideList = false;

		for (String rawLine : lines) {
			var line = rawLine.trim();
			if (line.isBlank()) {
				if (insideList) {
					html.append("</ul>");
					insideList = false;
				}
				continue;
			}

			var headingMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
			if (headingMatcher.matches()) {
				if (insideList) {
					html.append("</ul>");
					insideList = false;
				}
				int level = clampHeadingLevel(headingMatcher.group(1).length());
				html.append("<h").append(level).append(">")
					.append(applyInlineMarkdown(headingMatcher.group(2)))
					.append("</h").append(level).append(">");
				continue;
			}

			if (line.startsWith("- ") || line.startsWith("* ")) {
				if (!insideList) {
					html.append("<ul>");
					insideList = true;
				}
				html.append("<li>").append(applyInlineMarkdown(line.substring(2).trim())).append("</li>");
				continue;
			}

			if (insideList) {
				html.append("</ul>");
				insideList = false;
			}

			if (line.startsWith(">")) {
				html.append("<blockquote>").append(applyInlineMarkdown(line.replaceFirst("^>\\s*", ""))).append("</blockquote>");
				continue;
			}

			html.append("<p>").append(applyInlineMarkdown(line)).append("</p>");
		}

		if (insideList) {
			html.append("</ul>");
		}

		if (html.isEmpty()) {
			html.append("<p>Пустой Markdown файл.</p>");
		}

		return html.toString();
	}

	private String applyInlineMarkdown(String text) {
		var html = escapeHtml(text);
		html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
		html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
		html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
		html = html.replaceAll("\\[(.+?)]\\((https?://[^)]+)\\)", "<a href=\"$2\" target=\"_blank\" rel=\"noreferrer noopener\">$1</a>");
		html = html.replaceAll("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*", "<em>$1</em>");
		return html;
	}

	private List<DocumentPreviewPayload.DocumentOutlineItem> extractJsonOutline(JsonNode node) {
		if (node == null || !node.isObject()) {
			return List.of();
		}

		var outline = new ArrayList<DocumentPreviewPayload.DocumentOutlineItem>();
		var iterator = node.fieldNames();
		int index = 0;
		while (iterator.hasNext() && outline.size() < 18) {
			index += 1;
			outline.add(new DocumentPreviewPayload.DocumentOutlineItem("json-key-" + index, iterator.next(), 1));
		}
		return List.copyOf(outline);
	}

	private int countJsonTopLevelEntries(JsonNode node) {
		if (node == null) {
			return 0;
		}
		if (node.isObject()) {
			return node.size();
		}
		if (node.isArray()) {
			return node.size();
		}
		return 1;
	}

	private List<DocumentPreviewPayload.DocumentOutlineItem> extractYamlOutline(String normalizedYaml) {
		var outline = new ArrayList<DocumentPreviewPayload.DocumentOutlineItem>();
		int index = 0;
		for (String line : normalizedYaml.split("\\R")) {
			if (!line.contains(":")) {
				continue;
			}
			var indent = countLeadingSpaces(line);
			var key = line.substring(0, line.indexOf(':')).trim();
			if (key.isBlank() || key.startsWith("-")) {
				continue;
			}
			index += 1;
			outline.add(
				new DocumentPreviewPayload.DocumentOutlineItem(
					"yaml-key-" + index,
					key,
					Math.min((indent / 2) + 1, 6)
				)
			);
			if (outline.size() >= 18) {
				break;
			}
		}
		return List.copyOf(outline);
	}

	private List<DocumentPreviewPayload.DocumentOutlineItem> extractXmlOutline(org.w3c.dom.Document document) {
		var outline = new ArrayList<DocumentPreviewPayload.DocumentOutlineItem>();
		collectXmlOutline(document.getDocumentElement(), outline, 1, new int[] { 0 });
		return List.copyOf(outline);
	}

	private void collectXmlOutline(
		org.w3c.dom.Element element,
		List<DocumentPreviewPayload.DocumentOutlineItem> outline,
		int level,
		int[] counter
	) {
		if (element == null || outline.size() >= 18) {
			return;
		}

		counter[0] += 1;
		outline.add(
			new DocumentPreviewPayload.DocumentOutlineItem(
				"xml-node-" + counter[0],
				element.getTagName(),
				Math.min(level, 6)
			)
		);

		var children = element.getChildNodes();
		for (int index = 0; index < children.getLength() && outline.size() < 18; index += 1) {
			var child = children.item(index);
			if (child instanceof org.w3c.dom.Element childElement) {
				collectXmlOutline(childElement, outline, level + 1, counter);
			}
		}
	}

	private int countLeadingSpaces(String value) {
		int count = 0;
		while (count < value.length() && value.charAt(count) == ' ') {
			count += 1;
		}
		return count;
	}

	private String wrapStructuredConfigHtml(String body) {
		var safeBody = Optional.ofNullable(body).orElse("");
		return """
			<!doctype html>
			<html lang="ru">
			  <head>
			    <meta charset="utf-8" />
			    <meta name="viewport" content="width=device-width, initial-scale=1" />
			    <style>
			      :root{color-scheme:light;font-family:"Manrope","Segoe UI",sans-serif;background:#f7f1e6;color:#173436;}
			      body{margin:0;padding:28px;background:linear-gradient(180deg,#fffaf2 0%%,#f1e7d8 100%%);}
			      .config-sheet{border-radius:24px;padding:22px;background:rgba(255,253,249,0.92);box-shadow:-12px -12px 24px rgba(255,255,255,0.86),16px 18px 32px rgba(96,79,57,0.16);}
			      .config-sheet__meta{display:flex;gap:10px;align-items:center;margin-bottom:14px;font-size:0.9rem;color:#4f6963;}
			      strong{color:#173436;}
			      pre{margin:0;white-space:pre-wrap;word-break:break-word;font:500 0.95rem/1.6 "IBM Plex Mono","SFMono-Regular",monospace;color:#1f3834;}
			    </style>
			  </head>
			  <body>%s</body>
			</html>
			""".formatted(safeBody);
	}

	private List<DocumentPreviewPayload.DocumentFact> buildTextSummary(String kind, String text) {
		return List.of(
			new DocumentPreviewPayload.DocumentFact("Тип документа", kind),
			new DocumentPreviewPayload.DocumentFact("Строки", String.valueOf(countLines(text))),
			new DocumentPreviewPayload.DocumentFact("Слова", String.valueOf(countWords(text))),
			new DocumentPreviewPayload.DocumentFact("Символы", String.valueOf(text.length()))
		);
	}

	private List<String> splitParagraphs(String text, int limit) {
		return java.util.Arrays.stream(text.split("\\n{2,}"))
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.limit(limit)
			.toList();
	}

	private String readDocumentText(Path path) {
		try {
			var bytes = Files.readAllBytes(path);
			if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
				return normalizeExtractedText(new String(bytes, StandardCharsets.UTF_8));
			}
			if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
				return normalizeExtractedText(new String(bytes, StandardCharsets.UTF_16LE));
			}
			if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
				return normalizeExtractedText(new String(bytes, StandardCharsets.UTF_16BE));
			}
			return normalizeExtractedText(new String(bytes, StandardCharsets.UTF_8));
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать текстовый документ.", exception);
		}
	}

	private String normalizeExtractedText(String text) {
		return text
			.replace("\r\n", "\n")
			.replace('\r', '\n')
			.replaceAll("[ \\t]+\\n", "\n")
			.replaceAll("\\n[ \\t]+", "\n")
			.replaceAll("\\n{3,}", "\n\n")
			.trim();
	}

	private String normalizeText(String text) {
		return Optional.ofNullable(text)
			.map(value -> Normalizer.normalize(value, Normalizer.Form.NFKC))
			.map(value -> value.replace('\u0000', ' '))
			.map(value -> value.replaceAll("\\s+", " "))
			.map(String::trim)
			.orElse("");
	}

	private int countLines(String text) {
		if (text.isBlank()) {
			return 0;
		}
		return text.split("\\R").length;
	}

	private int countWords(String text) {
		var normalized = normalizeText(text);
		if (normalized.isBlank()) {
			return 0;
		}
		return normalized.split("\\s+").length;
	}

	private char detectDelimiter(String text, char preferredDelimiter) {
		var candidates = List.of(',', ';', '\t', '|');
		var sampleLines = java.util.Arrays.stream(text.split("\\R"))
			.map(String::trim)
			.filter(line -> !line.isBlank())
			.limit(12)
			.toList();
		if (sampleLines.isEmpty()) {
			return preferredDelimiter;
		}

		if (preferredDelimiter == '\t' || preferredDelimiter == ',' || preferredDelimiter == ';' || preferredDelimiter == '|') {
			var preferredScore = delimiterScore(sampleLines, preferredDelimiter);
			if (preferredScore > 0) {
				return preferredDelimiter;
			}
		}

		return candidates.stream()
			.max(Comparator.comparingInt(candidate -> delimiterScore(sampleLines, candidate)))
			.orElse(preferredDelimiter);
	}

	private int delimiterScore(List<String> sampleLines, char delimiter) {
		int score = 0;
		for (String line : sampleLines) {
			int occurrences = 0;
			boolean insideQuotes = false;
			for (int index = 0; index < line.length(); index += 1) {
				char current = line.charAt(index);
				if (current == '"') {
					insideQuotes = !insideQuotes;
				}
				if (!insideQuotes && current == delimiter) {
					occurrences += 1;
				}
			}
			if (occurrences > 0) {
				score += occurrences;
			}
		}
		return score;
	}

	private String describeDelimiter(String delimiter) {
		return switch (delimiter) {
			case "," -> "Comma";
			case ";" -> "Semicolon";
			case "\t" -> "Tab";
			case "|" -> "Pipe";
			default -> delimiter;
		};
	}

	private List<String> trimTrailingEmptyCells(List<String> values) {
		int lastNonEmptyIndex = -1;
		for (int index = 0; index < values.size(); index += 1) {
			if (!values.get(index).isBlank()) {
				lastNonEmptyIndex = index;
			}
		}
		if (lastNonEmptyIndex == -1) {
			return List.of();
		}
		return new ArrayList<>(values.subList(0, lastNonEmptyIndex + 1));
	}

	private List<String> padRow(List<String> row, int size) {
		var padded = new ArrayList<>(row);
		while (padded.size() < size) {
			padded.add("");
		}
		return padded;
	}

	private String toSpreadsheetColumnLabel(int columnIndex) {
		int value = columnIndex + 1;
		var label = new StringBuilder();

		while (value > 0) {
			int remainder = (value - 1) % 26;
			label.insert(0, (char) ('A' + remainder));
			value = (value - 1) / 26;
		}

		return label.toString();
	}

	private String normalizeTableCell(String value) {
		return Optional.ofNullable(value)
			.map(cell -> cell.replace('\u0000', ' '))
			.map(cell -> cell.replace('\r', ' ').replace('\n', ' '))
			.map(cell -> cell.replaceAll("\\s+", " "))
			.map(String::trim)
			.orElse("");
	}

	private List<DocumentPreviewPayload.DocumentOutlineItem> buildNarrativeOutline(
		List<NarrativeBlock> blocks,
		String idPrefix
	) {
		var outline = new ArrayList<DocumentPreviewPayload.DocumentOutlineItem>();
		int index = 1;
		for (NarrativeBlock block : blocks) {
			if (!"heading".equals(block.kind()) || block.text() == null || block.level() == null) {
				continue;
			}
			outline.add(new DocumentPreviewPayload.DocumentOutlineItem(idPrefix + index, block.text(), block.level()));
			index += 1;
		}
		return outline;
	}

	private String buildNarrativeText(List<NarrativeBlock> blocks) {
		return normalizeExtractedText(
			blocks.stream()
				.map(block -> {
					if ("table".equals(block.kind()) && block.rows() != null) {
						return block.rows().stream().map(row -> String.join(" ", row)).collect(java.util.stream.Collectors.joining("\n"));
					}
					return block.text() == null ? "" : block.text();
				})
				.filter(value -> !value.isBlank())
				.collect(java.util.stream.Collectors.joining("\n\n"))
		);
	}

	private String renderNarrativeBlocks(List<NarrativeBlock> blocks) {
		var builder = new StringBuilder();
		for (NarrativeBlock block : blocks) {
			switch (block.kind()) {
				case "heading" -> builder
					.append("<h")
					.append(block.level())
					.append(">")
					.append(escapeHtml(block.text()))
					.append("</h")
					.append(block.level())
					.append(">");
				case "paragraph" -> builder.append("<p>").append(escapeHtml(block.text())).append("</p>");
				case "table" -> builder.append(renderHtmlTable(block.rows()));
				default -> {
				}
			}
		}
		return builder.toString();
	}

	private String renderHtmlTable(List<List<String>> rows) {
		if (rows == null || rows.isEmpty()) {
			return "";
		}

		var builder = new StringBuilder("<table><tbody>");
		for (List<String> row : rows) {
			builder.append("<tr>");
			for (String cell : row) {
				builder.append("<td>").append(escapeHtml(cell)).append("</td>");
			}
			builder.append("</tr>");
		}
		builder.append("</tbody></table>");
		return builder.toString();
	}

	private String renderTableRowsForSearch(DocumentPreviewPayload.DocumentTablePreview table) {
		return table.rows().stream()
			.map(row -> row.stream().filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining(" ")))
			.filter(value -> !value.isBlank())
			.collect(java.util.stream.Collectors.joining("\n"));
	}

	private String wrapDocumentHtml(String bodyHtml) {
		var safeBody = bodyHtml == null || bodyHtml.isBlank()
			? "<p>Document preview не содержит рендеримого body content.</p>"
			: bodyHtml;

		return """
			<!doctype html>
			<html lang="ru">
			  <head>
			    <meta charset="utf-8">
			    <meta name="viewport" content="width=device-width, initial-scale=1">
			    <style>
			      html,body{margin:0;padding:0;background:#fffaf1;color:#102426;font-family:Manrope,Segoe UI,sans-serif;line-height:1.65;}
			      body{padding:28px;}
			      h1,h2,h3,h4,h5,h6{margin:1.1em 0 0.45em;color:#173436;}
			      p{margin:0 0 1em;}
			      table{width:100%%;border-collapse:collapse;margin:1.2em 0;background:#fffdf9;}
			      td,th{border:1px solid rgba(23,52,54,0.16);padding:10px 12px;text-align:left;vertical-align:top;}
			      ul,ol{padding-left:1.4rem;}
			      blockquote{margin:1em 0;padding-left:1rem;border-left:3px solid rgba(23,52,54,0.18);color:#355457;}
			      code,pre{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;}
			      pre{white-space:pre-wrap;background:#f4ece1;padding:16px;border-radius:14px;}
			      .epub-chapter + .epub-chapter{margin-top:2rem;padding-top:2rem;border-top:1px solid rgba(23,52,54,0.12);}
			    </style>
			  </head>
			  <body>%s</body>
			</html>
			""".formatted(safeBody);
	}

	private String escapeHtml(String value) {
		return Optional.ofNullable(value)
			.orElse("")
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private org.w3c.dom.Document parseXml(String content) {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			var builder = factory.newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(content)));
		}
		catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать XML внутри document container.", exception);
		}
	}

	private Optional<String> readZipEntryAsText(ZipFile zipFile, String entryName) throws IOException {
		ZipEntry entry = zipFile.getEntry(entryName);
		if (entry == null) {
			return Optional.empty();
		}
		try (var inputStream = zipFile.getInputStream(entry)) {
			return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private Optional<org.w3c.dom.Element> findFirstElementByLocalName(Node root, String localName) {
		if (root instanceof org.w3c.dom.Element element && localName.equals(element.getLocalName())) {
			return Optional.of(element);
		}

		var children = root.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			var candidate = findFirstElementByLocalName(children.item(index), localName);
			if (candidate.isPresent()) {
				return candidate;
			}
		}

		return Optional.empty();
	}

	private List<org.w3c.dom.Element> findElementsByLocalName(Node root, String localName) {
		var elements = new ArrayList<org.w3c.dom.Element>();
		if (root instanceof org.w3c.dom.Element element && localName.equals(element.getLocalName())) {
			elements.add(element);
		}

		var children = root.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			elements.addAll(findElementsByLocalName(children.item(index), localName));
		}
		return elements;
	}

	private void forEachChildElement(org.w3c.dom.Element element, java.util.function.Consumer<org.w3c.dom.Element> consumer) {
		var children = element.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			var child = children.item(index);
			if (child instanceof org.w3c.dom.Element childElement) {
				consumer.accept(childElement);
			}
		}
	}

	private int parseInteger(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private int clampHeadingLevel(int value) {
		return Math.min(Math.max(value, 1), 6);
	}

	private String resolveArchivePath(String basePath, String relativePath) {
		var base = Path.of(basePath).getParent();
		var resolved = base == null ? Path.of(relativePath) : base.resolve(relativePath);
		return resolved.normalize().toString().replace('\\', '/');
	}

	private String buildDocumentArtifactName(StoredUpload upload, String extension) {
		var name = upload.originalFileName();
		var lastDotIndex = name.lastIndexOf('.');
		var baseName = lastDotIndex <= 0 ? name : name.substring(0, lastDotIndex);
		return baseName + ".preview." + extension;
	}

	private String normalizeExtension(String extension) {
		return Optional.ofNullable(extension).orElse("").trim().toLowerCase(Locale.ROOT);
	}

	public record DocumentPreviewResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel
	) {
	}

	private record HtmlPreview(
		String textContent,
		String srcDoc,
		List<DocumentPreviewPayload.DocumentOutlineItem> outline,
		List<String> warnings
	) {
	}

	private record NarrativeBlock(
		String kind,
		String text,
		Integer level,
		List<List<String>> rows
	) {
		private static NarrativeBlock heading(String text, int level) {
			return new NarrativeBlock("heading", text, level, null);
		}

		private static NarrativeBlock paragraph(String text) {
			return new NarrativeBlock("paragraph", text, null, null);
		}

		private static NarrativeBlock table(List<List<String>> rows) {
			return new NarrativeBlock("table", null, null, rows);
		}
	}

	private record EpubManifestItem(
		String id,
		String href,
		String mediaType,
		List<String> properties
	) {
	}

	private record EpubChapter(
		String text,
		String html,
		List<DocumentPreviewPayload.DocumentOutlineItem> outline
	) {
	}

}
