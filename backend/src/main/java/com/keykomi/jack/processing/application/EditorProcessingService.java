package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.EditorPayloads;
import com.keykomi.jack.processing.domain.EditorRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

@Service
public class EditorProcessingService {

	private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*$");
	private static final Pattern TEXT_SECTION_PATTERN = Pattern.compile("(?m)^([A-Z0-9][^\\n]{2,80}:)\\s*$");
	private static final Pattern CSS_BLOCK_PATTERN = Pattern.compile("(?m)^\\s*([^@\\s][^{]+|@[\\w-]+[^\\{]*)\\s*\\{");
	private static final Pattern JAVASCRIPT_SYMBOL_PATTERN = Pattern.compile(
		"(?m)^\\s*(?:export\\s+)?(?:async\\s+)?(?:function\\s+([A-Za-z_$][\\w$]*)|class\\s+([A-Za-z_$][\\w$]*)|(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=)"
	);

	private static final List<PatternIssueRule> MARKDOWN_ISSUE_RULES = List.of(
		new PatternIssueRule(
			"warning",
			"MARKDOWN_RAW_HTML",
			Pattern.compile("(?i)<(script|iframe|object|embed|style|form)\\b"),
			"Markdown содержит raw HTML block, который стоит проверить перед публикацией.",
			"Если это документационный preview, лучше оставить HTML только там, где он действительно нужен."
		),
		new PatternIssueRule(
			"warning",
			"MARKDOWN_JAVASCRIPT_LINK",
			Pattern.compile("(?i)\\]\\(\\s*javascript:"),
			"Markdown содержит javascript:-ссылку.",
			"Замените ссылку на безопасный http/https/mailto target."
		)
	);

	private static final List<PatternIssueRule> CSS_ISSUE_RULES = List.of(
		new PatternIssueRule(
			"warning",
			"CSS_EXPRESSION",
			Pattern.compile("(?i)expression\\s*\\("),
			"CSS использует legacy expression(), который считается небезопасным.",
			"Замените expression() на обычную декларацию или вычисление на стороне приложения."
		),
		new PatternIssueRule(
			"warning",
			"CSS_BEHAVIOR",
			Pattern.compile("(?i)behavior\\s*:"),
			"CSS использует legacy behavior:, который стоит исключить из modern delivery.",
			"Удалите behavior: или перенесите нужное поведение в JS runtime."
		),
		new PatternIssueRule(
			"info",
			"CSS_REMOTE_IMPORT",
			Pattern.compile("(?i)@import\\s+url\\((['\"])?https?://"),
			"CSS тянет внешний @import, что может влиять на preview-поведение и стабильность.",
			"Подумайте о локальном asset pipeline или preload стратегии."
		),
		new PatternIssueRule(
			"warning",
			"CSS_JAVASCRIPT_URL",
			Pattern.compile("(?i)url\\((['\"])?javascript:"),
			"CSS содержит javascript:-URL.",
			"Для assets используйте только безопасные URL-схемы."
		)
	);

	private static final List<PatternIssueRule> JAVASCRIPT_ISSUE_RULES = List.of(
		new PatternIssueRule(
			"warning",
			"JS_EVAL",
			Pattern.compile("(?<![\\w$])eval\\s*\\("),
			"JavaScript использует eval().",
			"Если нужен parse/dispatch слой, лучше заменить eval() на явный parser или map handlers."
		),
		new PatternIssueRule(
			"warning",
			"JS_FUNCTION_CONSTRUCTOR",
			Pattern.compile("(?i)new\\s+Function\\s*\\("),
			"JavaScript использует Function constructor.",
			"Замените dynamic execution на обычные функции или заранее описанные handlers."
		),
		new PatternIssueRule(
			"warning",
			"JS_INNER_HTML",
			Pattern.compile("(?i)\\.innerHTML\\s*="),
			"JavaScript пишет в innerHTML, что требует явной sanitization-политики.",
			"Если это user content, используйте textContent или заранее санитизированный HTML."
		),
		new PatternIssueRule(
			"warning",
			"JS_DOCUMENT_WRITE",
			Pattern.compile("(?i)document\\.write\\s*\\("),
			"JavaScript использует document.write().",
			"Лучше заменить это на DOM append/update flow."
		),
		new PatternIssueRule(
			"warning",
			"JS_STRING_TIMEOUT",
			Pattern.compile("(?i)set(?:Timeout|Interval)\\s*\\(\\s*['\"]"),
			"JavaScript передаёт строку в setTimeout/setInterval.",
			"Передавайте callback-функцию вместо строки."
		)
	);

	private final ArtifactStorageService artifactStorageService;
	private final DocumentPreviewService documentPreviewService;
	private final ObjectMapper objectMapper;
	private final Yaml yaml;

	public EditorProcessingService(
		ArtifactStorageService artifactStorageService,
		DocumentPreviewService documentPreviewService,
		ObjectMapper objectMapper
	) {
		this.artifactStorageService = artifactStorageService;
		this.documentPreviewService = documentPreviewService;
		this.objectMapper = objectMapper;
		this.yaml = new Yaml();
	}

	public boolean isAvailable() {
		return true;
	}

	public void ensureSupported(StoredUpload upload, EditorRequest request) {
		resolveRequiredFormat(upload, request);
	}

	public EditorProcessingResult process(java.util.UUID jobId, StoredUpload upload, EditorRequest request) {
		var format = resolveRequiredFormat(upload, request);
		var rawText = readText(upload);
		var normalizedText = normalizeLineEndings(rawText);
		var issues = new ArrayList<EditorPayloads.EditorIssue>();
		var outline = new ArrayList<EditorPayloads.EditorOutlineItem>();
		var summary = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		var suggestions = new ArrayList<>(format.suggestions());
		var readyFileName = buildReadyFileName(upload.originalFileName(), format);
		var plainTextFileName = buildPlainTextFileName(upload.originalFileName());
		var plainTextExport = buildPlainTextExport(format, normalizedText, issues, outline, summary);

		appendBaseSummary(summary, format, normalizedText, issues);

		var manifest = new EditorPayloads.EditorManifest(
			upload.id(),
			upload.originalFileName(),
			format.id(),
			format.label(),
			format.syntaxMode(),
			format.previewMode(),
			"Editor diagnostics service",
			List.copyOf(summary),
			List.copyOf(deduplicateIssues(issues)),
			List.copyOf(outline),
			List.copyOf(suggestions),
			readyFileName,
			resolveMediaType(format),
			plainTextFileName,
			"text/plain",
			Instant.now()
		);

		var artifacts = List.of(
			this.artifactStorageService.storeJsonArtifact(
				jobId,
				"editor-manifest",
				"editor-manifest.json",
				manifest
			),
			this.artifactStorageService.storeBytesArtifact(
				jobId,
				"editor-export-ready",
				readyFileName,
				resolveMediaType(format),
				normalizedText.getBytes(StandardCharsets.UTF_8)
			),
			this.artifactStorageService.storeBytesArtifact(
				jobId,
				"editor-export-plain-text",
				plainTextFileName,
				"text/plain",
				plainTextExport.getBytes(StandardCharsets.UTF_8)
			)
		);

		return new EditorProcessingResult(artifacts, "Editor diagnostics service");
	}

	private String buildPlainTextExport(
		EditorFormatCatalog.EditorFormatSpec format,
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		return switch (format.id()) {
			case "markdown" -> analyzeMarkdown(normalizedText, issues, outline, summary);
			case "html" -> analyzeHtml(normalizedText, issues, outline, summary);
			case "css" -> analyzeCss(normalizedText, issues, outline, summary);
			case "javascript" -> analyzeJavaScript(normalizedText, issues, outline, summary);
			case "json" -> analyzeJson(normalizedText, issues, outline, summary);
			case "yaml" -> analyzeYaml(normalizedText, issues, outline, summary);
			case "txt" -> analyzePlainText(normalizedText, issues, outline, summary);
			default -> normalizedText;
		};
	}

	private String analyzeMarkdown(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		collectPatternIssues(normalizedText, MARKDOWN_ISSUE_RULES, issues);
		var matcher = MARKDOWN_HEADING_PATTERN.matcher(normalizedText);
		var headingCount = 0;

		while (matcher.find() && outline.size() < 18) {
			headingCount += 1;
			outline.add(
				new EditorPayloads.EditorOutlineItem(
					"md-" + headingCount,
					matcher.group(2).trim(),
					matcher.group(1).length(),
					"heading"
				)
			);
		}

		summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Rendered Markdown"));
		summary.add(new DocumentPreviewPayload.DocumentFact("Headings", String.valueOf(headingCount)));
		return stripMarkdown(normalizedText);
	}

	private String analyzeHtml(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		var previewPayload = this.documentPreviewService.analyze(
			createVirtualTextUpload("editor.html", "text/html", "html", normalizedText)
		);
		var document = Jsoup.parse(normalizedText);

		for (DocumentPreviewPayload.DocumentOutlineItem item : previewPayload.layout().outline()) {
			outline.add(
				new EditorPayloads.EditorOutlineItem(
					item.id(),
					item.label(),
					item.level(),
					"heading"
				)
			);
		}

		for (String warning : previewPayload.warnings()) {
			issues.add(new EditorPayloads.EditorIssue("info", "HTML_PREVIEW_NOTE", warning, null, null, null));
		}

		if (!document.select("script").isEmpty()) {
			issues.add(new EditorPayloads.EditorIssue(
				"warning",
				"HTML_SCRIPT_TAG",
				"HTML содержит <script>, который в preview будет отключён и требует отдельной security-проверки.",
				null,
				null,
				"Для embed-preview лучше вынести интерактивность в отдельный runtime."
			));
		}
		if (!document.select("[onload], [onclick], [onchange], [onsubmit], [onerror], [onmouseover]").isEmpty()) {
			issues.add(new EditorPayloads.EditorIssue(
				"warning",
				"HTML_INLINE_HANDLER",
				"HTML содержит inline event handlers.",
				null,
				null,
				"Перенесите DOM events в JS runtime вместо inline on* атрибутов."
			));
		}
		if (!document.select("iframe, object, embed").isEmpty()) {
			issues.add(new EditorPayloads.EditorIssue(
				"warning",
				"HTML_EMBED_TAG",
				"HTML содержит embedded runtime-теги вроде iframe/object/embed.",
				null,
				null,
				"Проверьте sandbox policy и origin доверия перед публикацией."
			));
		}
		if (!document.select("[href^=javascript:], [src^=javascript:]").isEmpty()) {
			issues.add(new EditorPayloads.EditorIssue(
				"warning",
				"HTML_JAVASCRIPT_URL",
				"HTML содержит javascript:-URL.",
				null,
				null,
				"Используйте только безопасные URL-схемы."
			));
		}

		for (Element anchor : document.select("a[target=_blank]")) {
			var rel = anchor.attr("rel").toLowerCase(Locale.ROOT);
			if (!rel.contains("noopener")) {
				issues.add(new EditorPayloads.EditorIssue(
					"info",
					"HTML_NO_NOOPENER",
					"Ссылка с target=\"_blank\" не содержит rel=\"noopener\".",
					null,
					null,
					"Добавьте rel=\"noopener noreferrer\", чтобы избежать opener leakage."
				));
				break;
			}
		}

		summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Sandbox HTML"));
		summary.add(new DocumentPreviewPayload.DocumentFact("Headings", String.valueOf(outline.size())));
		return Jsoup.parse(normalizedText).text();
	}

	private String analyzeCss(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		collectPatternIssues(normalizedText, CSS_ISSUE_RULES, issues);
		collectDelimiterIssues(normalizedText, issues, "css");

		var matcher = CSS_BLOCK_PATTERN.matcher(normalizedText);
		var selectorCount = 0;
		while (matcher.find() && outline.size() < 18) {
			selectorCount += 1;
			outline.add(
				new EditorPayloads.EditorOutlineItem(
					"css-" + selectorCount,
					matcher.group(1).trim(),
					1,
					matcher.group(1).trim().startsWith("@") ? "at-rule" : "selector"
				)
			);
		}

		summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Sandbox sample canvas"));
		summary.add(new DocumentPreviewPayload.DocumentFact("Blocks", String.valueOf(selectorCount)));
		return normalizedText;
	}

	private String analyzeJavaScript(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		collectPatternIssues(normalizedText, JAVASCRIPT_ISSUE_RULES, issues);
		collectDelimiterIssues(normalizedText, issues, "javascript");

		var matcher = JAVASCRIPT_SYMBOL_PATTERN.matcher(normalizedText);
		var symbolCount = 0;
		while (matcher.find() && outline.size() < 18) {
			symbolCount += 1;
			var functionName = matcher.group(1);
			var className = matcher.group(2);
			var variableName = matcher.group(3);
			var label = functionName != null ? functionName : className != null ? className : variableName;
			var kind = functionName != null ? "function" : className != null ? "class" : "binding";
			outline.add(new EditorPayloads.EditorOutlineItem("js-" + symbolCount, label, 1, kind));
		}

		summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Syntax mirror"));
		summary.add(new DocumentPreviewPayload.DocumentFact("Top-level symbols", String.valueOf(symbolCount)));
		return normalizedText;
	}

	private String analyzeJson(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		try {
			var root = this.objectMapper.readTree(normalizedText);
			collectJsonOutline(root, outline, "json");
			summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Structured JSON"));
			summary.add(new DocumentPreviewPayload.DocumentFact("Root", describeJsonRoot(root)));
		}
		catch (JsonProcessingException exception) {
			issues.add(new EditorPayloads.EditorIssue(
				"error",
				"JSON_PARSE_ERROR",
				"JSON не прошёл strict parse: %s".formatted(resolveJsonExceptionMessage(exception)),
				exception.getLocation() == null ? null : (int) exception.getLocation().getLineNr(),
				exception.getLocation() == null ? null : (int) exception.getLocation().getColumnNr(),
				"Проверьте запятые, кавычки и парность скобок."
			));
			summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Syntax only until JSON becomes valid"));
		}

		return normalizedText;
	}

	private String analyzeYaml(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		try {
			var value = this.yaml.load(normalizedText);
			collectGenericStructureOutline(value, outline, "yaml");
			summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Structured YAML"));
			summary.add(new DocumentPreviewPayload.DocumentFact("Root", describeGenericRoot(value)));
		}
		catch (MarkedYAMLException exception) {
			issues.add(new EditorPayloads.EditorIssue(
				"error",
				"YAML_PARSE_ERROR",
				"YAML не прошёл strict parse: %s".formatted(resolveYamlExceptionMessage(exception)),
				exception.getProblemMark() == null ? null : exception.getProblemMark().getLine() + 1,
				exception.getProblemMark() == null ? null : exception.getProblemMark().getColumn() + 1,
				"Проверьте indentation, двоеточия, списки и scalar quoting."
			));
			summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Syntax only until YAML becomes valid"));
		}
		catch (RuntimeException exception) {
			issues.add(new EditorPayloads.EditorIssue(
				"error",
				"YAML_PARSE_ERROR",
				"YAML parse завершился ошибкой.",
				null,
				null,
				"Проверьте indentation и структуру документа."
			));
		}

		return normalizedText;
	}

	private String analyzePlainText(
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues,
		List<EditorPayloads.EditorOutlineItem> outline,
		List<DocumentPreviewPayload.DocumentFact> summary
	) {
		var matcher = TEXT_SECTION_PATTERN.matcher(normalizedText);
		var sectionCount = 0;
		while (matcher.find() && outline.size() < 16) {
			sectionCount += 1;
			outline.add(new EditorPayloads.EditorOutlineItem("txt-" + sectionCount, matcher.group(1), 1, "section"));
		}

		var longestLine = normalizedText.lines().mapToInt(String::length).max().orElse(0);
		if (longestLine > 180) {
			issues.add(new EditorPayloads.EditorIssue(
				"info",
				"TEXT_LONG_LINES",
				"В документе есть очень длинные строки, которые могут хуже читаться в narrow layouts.",
				null,
				null,
				"Если это prose или notes, подумайте о переносах на уровне paragraph blocks."
			));
		}

		summary.add(new DocumentPreviewPayload.DocumentFact("Режим preview", "Readable text"));
		summary.add(new DocumentPreviewPayload.DocumentFact("Sections", String.valueOf(sectionCount)));
		return normalizedText;
	}

	private StoredUpload createVirtualTextUpload(
		String fileName,
		String mediaType,
		String extension,
		String content
	) {
		try {
			var tempPath = Files.createTempFile("jack-editor-", "." + extension);
			Files.writeString(tempPath, content, StandardCharsets.UTF_8);
			return new StoredUpload(
				java.util.UUID.randomUUID(),
				fileName,
				mediaType,
				extension,
				Files.size(tempPath),
				"",
				Instant.now(),
				tempPath
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить временный editor preview upload.", exception);
		}
	}

	private void appendBaseSummary(
		List<DocumentPreviewPayload.DocumentFact> summary,
		EditorFormatCatalog.EditorFormatSpec format,
		String normalizedText,
		List<EditorPayloads.EditorIssue> issues
	) {
		var lineCount = normalizedText.isBlank() ? 0 : normalizedText.split("\\n", -1).length;
		var wordCount = countWords(normalizedText);
		var errorCount = issues.stream().filter(issue -> "error".equals(issue.severity())).count();
		var warningCount = issues.stream().filter(issue -> "warning".equals(issue.severity())).count();

		summary.add(new DocumentPreviewPayload.DocumentFact("Формат", format.label()));
		summary.add(new DocumentPreviewPayload.DocumentFact("Символов", String.valueOf(normalizedText.length())));
		summary.add(new DocumentPreviewPayload.DocumentFact("Строк", String.valueOf(lineCount)));
		summary.add(new DocumentPreviewPayload.DocumentFact("Слов", String.valueOf(wordCount)));
		summary.add(new DocumentPreviewPayload.DocumentFact(
			"Diagnostics",
			"%s error / %s warning".formatted(errorCount, warningCount)
		));
		summary.add(new DocumentPreviewPayload.DocumentFact(
			"Exports",
			format.supportsPlainTextExport() ? "Ready file + plain text" : "Ready file"
		));
	}

	private int countWords(String text) {
		if (text == null || text.isBlank()) {
			return 0;
		}

		return (int) Pattern.compile("\\s+")
			.splitAsStream(text.trim())
			.filter(token -> !token.isBlank())
			.count();
	}

	private void collectPatternIssues(
		String content,
		List<PatternIssueRule> rules,
		List<EditorPayloads.EditorIssue> issues
	) {
		for (PatternIssueRule rule : rules) {
			var matcher = rule.pattern().matcher(content);
			if (!matcher.find()) {
				continue;
			}

			var position = resolvePosition(content, matcher.start());
			issues.add(new EditorPayloads.EditorIssue(
				rule.severity(),
				rule.code(),
				rule.message(),
				position.line(),
				position.column(),
				rule.hint()
			));
		}
	}

	private void collectDelimiterIssues(
		String content,
		List<EditorPayloads.EditorIssue> issues,
		String formatId
	) {
		// Здесь намеренно не строится полноценный parser: нам нужен быстрый backend preflight,
		// который ловит частые structural mistakes ещё до отдельного lint/runtime шага.
		var stack = new ArrayDeque<DelimiterToken>();
		var state = ScannerState.NORMAL;
		char stringQuote = 0;
		int line = 1;
		int column = 0;

		for (int index = 0; index < content.length(); index += 1) {
			var current = content.charAt(index);
			column += 1;

			if (current == '\n') {
				line += 1;
				column = 0;
				if (state == ScannerState.LINE_COMMENT) {
					state = ScannerState.NORMAL;
				}
				continue;
			}

			if (state == ScannerState.LINE_COMMENT) {
				continue;
			}
			if (state == ScannerState.BLOCK_COMMENT) {
				if (current == '*' && index + 1 < content.length() && content.charAt(index + 1) == '/') {
					state = ScannerState.NORMAL;
					index += 1;
					column += 1;
				}
				continue;
			}
			if (state == ScannerState.STRING) {
				if (current == '\\') {
					index += 1;
					column += 1;
					continue;
				}
				if (current == stringQuote) {
					state = ScannerState.NORMAL;
				}
				continue;
			}

			if (current == '/' && index + 1 < content.length()) {
				var next = content.charAt(index + 1);
				if (next == '/') {
					state = ScannerState.LINE_COMMENT;
					index += 1;
					column += 1;
					continue;
				}
				if (next == '*') {
					state = ScannerState.BLOCK_COMMENT;
					index += 1;
					column += 1;
					continue;
				}
			}

			if (current == '"' || current == '\'' || current == '`') {
				state = ScannerState.STRING;
				stringQuote = current;
				continue;
			}

			if (current == '{' || current == '[' || current == '(') {
				stack.push(new DelimiterToken(current, line, column));
				continue;
			}
			if (current == '}' || current == ']' || current == ')') {
				if (stack.isEmpty()) {
					issues.add(new EditorPayloads.EditorIssue(
						"error",
						formatId.toUpperCase(Locale.ROOT) + "_UNEXPECTED_CLOSER",
						"Найдена закрывающая скобка без парной открывающей.",
						line,
						column,
						"Проверьте баланс скобок в текущем блоке."
					));
					return;
				}

				var opener = stack.pop();
				if (!matches(opener.symbol(), current)) {
					issues.add(new EditorPayloads.EditorIssue(
						"error",
						formatId.toUpperCase(Locale.ROOT) + "_MISMATCHED_DELIMITER",
						"Скобки закрываются в неправильном порядке.",
						line,
						column,
						"Проверьте вложенность блоков и парность delimiter'ов."
					));
					return;
				}
			}
		}

		if (!stack.isEmpty()) {
			var opener = stack.peek();
			issues.add(new EditorPayloads.EditorIssue(
				"error",
				formatId.toUpperCase(Locale.ROOT) + "_UNCLOSED_DELIMITER",
				"В документе осталась незакрытая скобка.",
				opener.line(),
				opener.column(),
				"Проверьте завершение текущего блока."
			));
		}
	}

	private boolean matches(char opener, char closer) {
		return (opener == '{' && closer == '}')
			|| (opener == '[' && closer == ']')
			|| (opener == '(' && closer == ')');
	}

	private void collectJsonOutline(
		JsonNode root,
		List<EditorPayloads.EditorOutlineItem> outline,
		String prefix
	) {
		if (root == null) {
			return;
		}
		if (root.isObject()) {
			var fields = root.fields();
			int index = 0;
			while (fields.hasNext() && outline.size() < 18) {
				var entry = fields.next();
				index += 1;
				outline.add(
					new EditorPayloads.EditorOutlineItem(
						prefix + "-" + index,
						entry.getKey(),
						1,
						describeJsonNodeKind(entry.getValue())
					)
				);
			}
			return;
		}
		if (root.isArray()) {
			for (int index = 0; index < Math.min(root.size(), 12) && outline.size() < 18; index += 1) {
				outline.add(
					new EditorPayloads.EditorOutlineItem(
						prefix + "-" + (index + 1),
						"[%s]".formatted(index),
						1,
						describeJsonNodeKind(root.get(index))
					)
				);
			}
		}
	}

	private void collectGenericStructureOutline(
		Object value,
		List<EditorPayloads.EditorOutlineItem> outline,
		String prefix
	) {
		if (value instanceof Map<?, ?> map) {
			int index = 0;
			for (Object key : map.keySet()) {
				if (outline.size() >= 18) {
					return;
				}
				index += 1;
				outline.add(
					new EditorPayloads.EditorOutlineItem(
						prefix + "-" + index,
						String.valueOf(key),
						1,
						describeGenericKind(map.get(key))
					)
				);
			}
			return;
		}
		if (value instanceof List<?> list) {
			for (int index = 0; index < Math.min(list.size(), 12) && outline.size() < 18; index += 1) {
				outline.add(
					new EditorPayloads.EditorOutlineItem(
						prefix + "-" + (index + 1),
						"[%s]".formatted(index),
						1,
						describeGenericKind(list.get(index))
					)
				);
			}
		}
	}

	private String describeJsonRoot(JsonNode root) {
		if (root.isObject()) {
			return "Object";
		}
		if (root.isArray()) {
			return "Array";
		}
		return root.getNodeType().name();
	}

	private String describeGenericRoot(Object value) {
		if (value instanceof Map<?, ?>) {
			return "Mapping";
		}
		if (value instanceof List<?>) {
			return "Sequence";
		}
		return value == null ? "Null" : "Scalar";
	}

	private String describeJsonNodeKind(JsonNode node) {
		if (node == null) {
			return "null";
		}
		if (node.isObject()) {
			return "object";
		}
		if (node.isArray()) {
			return "array";
		}
		if (node.isTextual()) {
			return "string";
		}
		if (node.isNumber()) {
			return "number";
		}
		if (node.isBoolean()) {
			return "boolean";
		}
		return node.getNodeType().name().toLowerCase(Locale.ROOT);
	}

	private String describeGenericKind(Object value) {
		if (value instanceof Map<?, ?>) {
			return "mapping";
		}
		if (value instanceof List<?>) {
			return "sequence";
		}
		return value == null ? "null" : "scalar";
	}

	private String resolveJsonExceptionMessage(JsonProcessingException exception) {
		return exception.getOriginalMessage() == null ? "Неизвестная JSON parse ошибка." : exception.getOriginalMessage();
	}

	private String resolveYamlExceptionMessage(MarkedYAMLException exception) {
		if (exception.getProblem() != null && !exception.getProblem().isBlank()) {
			return exception.getProblem();
		}
		return "Неизвестная YAML parse ошибка.";
	}

	private List<EditorPayloads.EditorIssue> deduplicateIssues(List<EditorPayloads.EditorIssue> issues) {
		var uniqueIssues = new LinkedHashSet<EditorPayloads.EditorIssue>(issues);
		return List.copyOf(uniqueIssues);
	}

	private Position resolvePosition(String content, int index) {
		int line = 1;
		int column = 1;
		for (int cursor = 0; cursor < index && cursor < content.length(); cursor += 1) {
			if (content.charAt(cursor) == '\n') {
				line += 1;
				column = 1;
			}
			else {
				column += 1;
			}
		}
		return new Position(line, column);
	}

	private String resolveMediaType(EditorFormatCatalog.EditorFormatSpec format) {
		return format.mimeTypes().isEmpty() ? "text/plain" : format.mimeTypes().getFirst();
	}

	private String buildReadyFileName(String originalFileName, EditorFormatCatalog.EditorFormatSpec format) {
		var baseName = stripExtension(originalFileName);
		return baseName + "." + format.extensions().getFirst();
	}

	private String buildPlainTextFileName(String originalFileName) {
		return stripExtension(originalFileName) + ".txt";
	}

	private String stripExtension(String originalFileName) {
		if (originalFileName == null || originalFileName.isBlank()) {
			return "untitled";
		}
		var lastDotIndex = originalFileName.lastIndexOf('.');
		if (lastDotIndex <= 0) {
			return originalFileName;
		}
		return originalFileName.substring(0, lastDotIndex);
	}

	private String stripMarkdown(String text) {
		var plain = text
			.replaceAll("(?s)```.+?```", " ")
			.replaceAll("`([^`]+)`", "$1")
			.replaceAll("!\\[[^\\]]*]\\(([^)]+)\\)", "$1")
			.replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1")
			.replaceAll("(?m)^#{1,6}\\s+", "")
			.replaceAll("(?m)^>\\s?", "")
			.replaceAll("(?m)^\\s*[-*+]\\s+", "")
			.replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
			.replaceAll("(\\*\\*|__|\\*|_)", "");

		return plain.trim();
	}

	private String readText(StoredUpload upload) {
		try {
			var raw = Files.readString(upload.storagePath(), StandardCharsets.UTF_8);
			return raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать editor source как UTF-8 text.", exception);
		}
	}

	private String normalizeLineEndings(String content) {
		return content.replace("\r\n", "\n").replace('\r', '\n');
	}

	private EditorFormatCatalog.EditorFormatSpec resolveRequiredFormat(StoredUpload upload, EditorRequest request) {
		var requestedFormat = EditorFormatCatalog.resolveById(request.formatId());
		if (requestedFormat != null) {
			validateRequiredDependencies(requestedFormat);
			return requestedFormat;
		}

		var byExtension = EditorFormatCatalog.resolveByExtension(upload.extension());
		if (byExtension != null) {
			validateRequiredDependencies(byExtension);
			return byExtension;
		}

		var byMimeType = EditorFormatCatalog.resolveByMimeType(upload.mediaType());
		if (byMimeType != null) {
			validateRequiredDependencies(byMimeType);
			return byMimeType;
		}

		throw new ResponseStatusException(
			HttpStatus.BAD_REQUEST,
			"EDITOR_PROCESS поддерживает только markdown, html, css, javascript, json, yaml и plain text."
		);
	}

	private void validateRequiredDependencies(EditorFormatCatalog.EditorFormatSpec format) {
		if (
			format.requiredJobTypes().contains(com.keykomi.jack.processing.domain.ProcessingJobType.DOCUMENT_PREVIEW)
			&& !this.documentPreviewService.isAvailable()
		) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Editor format %s требует доступного DOCUMENT_PREVIEW capability.".formatted(format.id())
			);
		}
	}

	public record EditorProcessingResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel
	) {
	}

	private record PatternIssueRule(
		String severity,
		String code,
		Pattern pattern,
		String message,
		String hint
	) {
	}

	private record Position(
		Integer line,
		Integer column
	) {
	}

	private record DelimiterToken(
		char symbol,
		int line,
		int column
	) {
	}

	private enum ScannerState {
		NORMAL,
		LINE_COMMENT,
		BLOCK_COMMENT,
		STRING
	}

}
