package com.keykomi.jack.processing.application;

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.ins.InsExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.util.misc.Extension;
import com.keykomi.jack.processing.domain.MarkdownRenderContract;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarkdownRenderService {

	public static final String PROFILE_VERSION = "jack-markdown-1.0.0";
	private static final int MAX_SOURCE_CHARACTERS = 1_000_000;
	private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R([\\s\\S]*?)\\R---(?:\\R|\\z)");
	private static final Pattern OBSIDIAN_REFERENCE_PATTERN = Pattern.compile("(!?)\\[\\[([^]#|]+)(?:#([^]|]+))?(?:\\|([^]]+))?]]");
	private static final Pattern CALLOUT_PATTERN = Pattern.compile("(?m)^>\\s*\\[!([A-Za-z][\\w-]*)](?:[+-])?\\s*(.*)$");
	private static final Pattern TAG_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_/-]+)");
	private static final Pattern BLOCK_REFERENCE_PATTERN = Pattern.compile("(?m)(?:^|\\s)\\^([A-Za-z0-9-]+)\\s*$");
	private static final Pattern RAW_HTML_PATTERN = Pattern.compile("<[/!]?[A-Za-z][^>]*>");
	private static final Pattern TABLE_PATTERN = Pattern.compile("(?m)^\\s*\\|?.+\\|.+$\\R\\s*\\|?\\s*:?-{3,}");
	private static final Pattern TASK_PATTERN = Pattern.compile("(?m)^\\s*[-*+]\\s+\\[[ xX]]\\s+");
	private static final Pattern FOOTNOTE_PATTERN = Pattern.compile("(?m)^\\[\\^[^]]+]:");
	private static final Pattern DEFINITION_PATTERN = Pattern.compile("(?m)^\\s*:\\s+.+$");
	private static final Pattern HIGHLIGHT_PATTERN = Pattern.compile("==([^=\\n]+)==");
	private static final Set<String> OPTIONAL_EXTENSIONS = Set.of(
		"footnotes",
		"definition-lists",
		"heading-anchors",
		"toc",
		"highlight",
		"sub-sup"
	);

	private final Cleaner sanitizer;
	private final ConcurrentHashMap<Set<String>, ParserBundle> parserBundles = new ConcurrentHashMap<>();

	public MarkdownRenderService() {
		this.sanitizer = new Cleaner(markdownSafelist());
	}

	public MarkdownRenderContract render(String source, String requestedProfile) {
		return render(source, requestedProfile, Set.of());
	}

	public MarkdownRenderContract render(String source, String requestedProfile, Set<String> requestedExtensions) {
		var markdown = source == null ? "" : source;
		if (markdown.length() > MAX_SOURCE_CHARACTERS) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Markdown превышает лимит синхронного preview.");
		}

		var profile = normalizeProfile(requestedProfile);
		var enabledExtensions = normalizeExtensions(requestedExtensions);
		var warnings = new ArrayList<String>();
		var detectedFeatures = detectFeatures(markdown);
		var unresolvedReferences = new ArrayList<MarkdownRenderContract.UnresolvedReference>();
		var prepared = markdown;

		if ("obsidian-safe".equals(profile)) {
			prepared = preprocessObsidian(prepared, unresolvedReferences, warnings, detectedFeatures);
		}
		if (enabledExtensions.contains("highlight")) {
			prepared = HIGHLIGHT_PATTERN.matcher(prepared).replaceAll("++$1++");
		}

		if (RAW_HTML_PATTERN.matcher(markdown).find()) {
			warnings.add("Raw HTML экранирован политикой профиля.");
		}

		var parserBundle = this.parserBundles.computeIfAbsent(enabledExtensions, this::buildParserBundle);
		var rendered = parserBundle.renderer().render(parserBundle.parser().parse(prepared));
		if (enabledExtensions.contains("highlight")) {
			rendered = rendered.replace("<ins>", "<mark>").replace("</ins>", "</mark>");
		}
		var sanitized = sanitize(rendered, warnings);
		var outline = extractOutline(sanitized);

		if (sanitized.isBlank()) {
			sanitized = "<p class=\"markdown-empty\">Документ пока пуст.</p>";
		}

		return new MarkdownRenderContract(
			PROFILE_VERSION,
			profile,
			sanitized,
			outline,
			List.copyOf(unresolvedReferences),
			List.copyOf(new LinkedHashSet<>(warnings)),
			enabledExtensions,
			Set.copyOf(detectedFeatures)
		);
	}

	private String preprocessObsidian(
		String markdown,
		List<MarkdownRenderContract.UnresolvedReference> unresolvedReferences,
		List<String> warnings,
		Set<String> detectedFeatures
	) {
		var prepared = markdown;
		var frontmatterMatcher = FRONTMATTER_PATTERN.matcher(prepared);
		if (frontmatterMatcher.find()) {
			detectedFeatures.add("yaml-frontmatter");
			prepared = frontmatterMatcher.replaceFirst("");
		}

		var calloutMatcher = CALLOUT_PATTERN.matcher(prepared);
		var calloutBuffer = new StringBuffer();
		while (calloutMatcher.find()) {
			detectedFeatures.add("obsidian-callout");
			var kind = calloutMatcher.group(1).toUpperCase(Locale.ROOT);
			var title = calloutMatcher.group(2).isBlank() ? kind : calloutMatcher.group(2).trim();
			calloutMatcher.appendReplacement(calloutBuffer, Matcher.quoteReplacement("> **" + title + "**"));
		}
		calloutMatcher.appendTail(calloutBuffer);
		prepared = calloutBuffer.toString();

		var referenceMatcher = OBSIDIAN_REFERENCE_PATTERN.matcher(prepared);
		var referenceBuffer = new StringBuffer();
		while (referenceMatcher.find()) {
			var embed = !referenceMatcher.group(1).isBlank();
			var target = referenceMatcher.group(2).trim();
			var section = referenceMatcher.group(3);
			var explicitLabel = referenceMatcher.group(4);
			var label = explicitLabel == null || explicitLabel.isBlank() ? target : explicitLabel.trim();
			var fullTarget = section == null || section.isBlank() ? target : target + "#" + section.trim();
			var kind = embed ? "embed" : "wikilink";
			detectedFeatures.add("obsidian-" + kind);
			unresolvedReferences.add(new MarkdownRenderContract.UnresolvedReference(kind, fullTarget, label));
			var fallback = embed
				? "`[Вложение недоступно: " + label + "]`"
				: "`[Ссылка не разрешена: " + label + "]`";
			referenceMatcher.appendReplacement(referenceBuffer, Matcher.quoteReplacement(fallback));
		}
		referenceMatcher.appendTail(referenceBuffer);
		prepared = referenceBuffer.toString();

		if (TAG_PATTERN.matcher(markdown).find()) {
			detectedFeatures.add("obsidian-tag");
		}
		if (BLOCK_REFERENCE_PATTERN.matcher(markdown).find()) {
			detectedFeatures.add("obsidian-block-reference");
			warnings.add("Block references показаны как source без vault context.");
		}

		return prepared;
	}

	private Set<String> detectFeatures(String markdown) {
		var features = new LinkedHashSet<String>();
		if (TABLE_PATTERN.matcher(markdown).find()) features.add("gfm-table");
		if (TASK_PATTERN.matcher(markdown).find()) features.add("gfm-task-list");
		if (markdown.contains("~~")) features.add("gfm-strikethrough");
		if (markdown.contains("```")) features.add("fenced-code");
		if (FOOTNOTE_PATTERN.matcher(markdown).find()) features.add("footnote");
		if (DEFINITION_PATTERN.matcher(markdown).find()) features.add("definition-list");
		return features;
	}

	private String sanitize(String rendered, List<String> warnings) {
		var dirty = Jsoup.parseBodyFragment(rendered);
		var images = dirty.select("img");
		for (Element image : images) {
			var label = image.attr("alt").isBlank() ? "без подписи" : image.attr("alt");
			image.replaceWith(new TextNode("[Изображение заблокировано: " + label + "]"));
		}
		if (!images.isEmpty()) {
			warnings.add("Внешние изображения не загружаются автоматически.");
		}

		var clean = this.sanitizer.clean(dirty);
		clean.outputSettings(new Document.OutputSettings().prettyPrint(false));
		return clean.body().html();
	}

	private List<MarkdownRenderContract.OutlineItem> extractOutline(String html) {
		var document = Jsoup.parseBodyFragment(html);
		var outline = new ArrayList<MarkdownRenderContract.OutlineItem>();
		var index = 0;
		for (Element heading : document.select("h1,h2,h3,h4,h5,h6")) {
			index += 1;
			var id = heading.id().isBlank() ? "heading-" + index : heading.id();
			outline.add(new MarkdownRenderContract.OutlineItem(id, heading.text(), Integer.parseInt(heading.tagName().substring(1)), "heading"));
		}
		return List.copyOf(outline);
	}

	private String normalizeProfile(String requestedProfile) {
		if (requestedProfile == null || requestedProfile.isBlank()) {
			return "commonmark-gfm";
		}
		return switch (requestedProfile.trim().toLowerCase(Locale.ROOT)) {
			case "commonmark-gfm", "obsidian-safe" -> requestedProfile.trim().toLowerCase(Locale.ROOT);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный Markdown profile.");
		};
	}

	private Set<String> normalizeExtensions(Set<String> requestedExtensions) {
		if (requestedExtensions == null || requestedExtensions.isEmpty()) {
			return Set.of();
		}

		var normalized = new LinkedHashSet<String>();
		for (String extension : requestedExtensions) {
			if (extension == null || extension.isBlank()) {
				continue;
			}
			var candidate = extension.trim().toLowerCase(Locale.ROOT);
			if (!OPTIONAL_EXTENSIONS.contains(candidate)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное Markdown extension: " + candidate);
			}
			normalized.add(candidate);
		}
		return Set.copyOf(normalized);
	}

	private ParserBundle buildParserBundle(Set<String> enabledExtensions) {
		var extensions = new ArrayList<Extension>();
		extensions.add(AutolinkExtension.create());
		extensions.add(
			enabledExtensions.contains("sub-sup")
				? StrikethroughSubscriptExtension.create()
				: StrikethroughExtension.create()
		);
		extensions.add(TablesExtension.create());
		extensions.add(TaskListExtension.create());
		if (enabledExtensions.contains("footnotes")) extensions.add(FootnoteExtension.create());
		if (enabledExtensions.contains("definition-lists")) extensions.add(DefinitionExtension.create());
		if (enabledExtensions.contains("heading-anchors")) extensions.add(AnchorLinkExtension.create());
		if (enabledExtensions.contains("toc")) extensions.add(TocExtension.create());
		if (enabledExtensions.contains("highlight")) extensions.add(InsExtension.create());
		if (enabledExtensions.contains("sub-sup")) {
			extensions.add(SuperscriptExtension.create());
		}

		var options = new MutableDataSet();
		options.set(Parser.EXTENSIONS, extensions);
		options.set(HtmlRenderer.ESCAPE_HTML, true);
		options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
		options.set(HtmlRenderer.RENDER_HEADER_ID, true);
		return new ParserBundle(Parser.builder(options).build(), HtmlRenderer.builder(options).build());
	}

	private Safelist markdownSafelist() {
		return new Safelist()
			.addTags("p", "br", "blockquote", "code", "pre", "strong", "em", "del", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "table", "thead", "tbody", "tr", "th", "td", "hr", "sup", "sub", "mark", "dl", "dt", "dd", "input", "span")
			.addTags("a")
			.addAttributes("a", "href", "title")
			.addProtocols("a", "href", "http", "https", "mailto")
			.addAttributes("h1", "id")
			.addAttributes("h2", "id")
			.addAttributes("h3", "id")
			.addAttributes("h4", "id")
			.addAttributes("h5", "id")
			.addAttributes("h6", "id")
			.addAttributes("input", "type", "checked", "disabled")
			.addAttributes("li", "class")
			.addAttributes("table", "class")
			.addEnforcedAttribute("a", "rel", "noreferrer noopener")
			.preserveRelativeLinks(false);
	}

	private record ParserBundle(Parser parser, HtmlRenderer renderer) {
	}
}
