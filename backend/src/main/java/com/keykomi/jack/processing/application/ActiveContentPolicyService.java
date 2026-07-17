package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;

@Service
public class ActiveContentPolicyService {

	private static final long MAX_SVG_BYTES = 8_388_608L;
	private static final Set<String> BLOCKED_SVG_ELEMENTS = Set.of(
		"script", "style", "foreignobject", "iframe", "object", "embed", "audio", "video",
		"animate", "animatemotion", "animatetransform", "set"
	);
	private static final Set<String> URL_ATTRIBUTES = Set.of(
		"href", "xlink:href", "src", "srcset", "poster", "action", "formaction", "background", "cite"
	);
	private static final Pattern EXTERNAL_CSS_URL = Pattern.compile("(?i)url\\(\\s*['\"]?(?!#)");

	public SanitizedMarkup sanitizeDocumentMarkup(String rawMarkup) {
		var dirtyDocument = Jsoup.parse(rawMarkup == null ? "" : rawMarkup);
		var unsafe = !dirtyDocument.select(
			"script,style,noscript,form,input,button,textarea,select,option,iframe,object,embed,base,link,meta,svg,math"
		).isEmpty() || dirtyDocument.getAllElements().stream().anyMatch(this::hasUnsafeHtmlAttribute);

		var cleaner = new Cleaner(
			Safelist.relaxed()
				.addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "section", "article")
				.addAttributes("td", "colspan", "rowspan")
				.addAttributes("th", "colspan", "rowspan")
		);
		var cleanedDocument = cleaner.clean(dirtyDocument);
		cleanedDocument.getAllElements().forEach(this::stripExternalHtmlAttributes);
		return new SanitizedMarkup(cleanedDocument, unsafe);
	}

	public void verifyInertSvg(Path path) {
		try {
			if (Files.size(path) > MAX_SVG_BYTES) {
				throw unsafeSvg("SVG превышает безопасный лимит разбора.");
			}
			var xml = Files.readString(path, StandardCharsets.UTF_8);
			var document = SecureXmlParser.parse(xml);
			verifySvgNode(document.getDocumentElement());
		}
		catch (ProcessingException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new ProcessingException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"UNSAFE_ACTIVE_CONTENT",
				"SVG не прошёл безопасный XML-разбор.",
				exception
			);
		}
	}

	private void verifySvgNode(Node node) throws IOException {
		if (node == null) {
			return;
		}
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			var name = localName(node).toLowerCase(Locale.ROOT);
			if (BLOCKED_SVG_ELEMENTS.contains(name)) {
				throw unsafeSvg("SVG содержит активный или встраиваемый элемент: " + name + ".");
			}
			var attributes = node.getAttributes();
			for (int index = 0; index < attributes.getLength(); index += 1) {
				var attribute = attributes.item(index);
				var attributeName = localName(attribute).toLowerCase(Locale.ROOT);
				var qualifiedName = attribute.getNodeName().toLowerCase(Locale.ROOT);
				var value = attribute.getNodeValue() == null ? "" : attribute.getNodeValue().strip();
				if (attributeName.startsWith("on") || "style".equals(attributeName) || "xml:base".equals(qualifiedName)) {
					throw unsafeSvg("SVG содержит активный атрибут: " + qualifiedName + ".");
				}
				if ((URL_ATTRIBUTES.contains(attributeName) || URL_ATTRIBUTES.contains(qualifiedName))
					&& !value.isBlank() && !value.startsWith("#")) {
					throw unsafeSvg("SVG содержит внешнюю ссылку.");
				}
				var normalizedValue = value.toLowerCase(Locale.ROOT).replace(" ", "");
				if (normalizedValue.contains("javascript:") || normalizedValue.contains("vbscript:")
					|| normalizedValue.contains("data:") || EXTERNAL_CSS_URL.matcher(value).find()) {
					throw unsafeSvg("SVG содержит небезопасную URL/CSS-ссылку.");
				}
			}
		}

		var children = node.getChildNodes();
		for (int index = 0; index < children.getLength(); index += 1) {
			verifySvgNode(children.item(index));
		}
	}

	private boolean hasUnsafeHtmlAttribute(Element element) {
		return element.attributes().asList().stream().anyMatch(attribute -> {
			var name = attribute.getKey().toLowerCase(Locale.ROOT);
			var value = attribute.getValue().strip().toLowerCase(Locale.ROOT);
			return name.startsWith("on") || "style".equals(name) || URL_ATTRIBUTES.contains(name)
				&& !value.isBlank() && !value.startsWith("#");
		});
	}

	private void stripExternalHtmlAttributes(Element element) {
		// В srcdoc нет причин сохранять сетевые ссылки: даже sandboxed iframe не должен
		// становиться неявным SSRF/browser-tracking каналом.
		for (String attribute : URL_ATTRIBUTES) {
			var value = element.attr(attribute).strip();
			if (!value.isBlank() && !("href".equals(attribute) && value.startsWith("#"))) {
				element.removeAttr(attribute);
			}
		}
		element.removeAttr("style");
	}

	private String localName(Node node) {
		return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
	}

	private ProcessingException unsafeSvg(String message) {
		return new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSAFE_ACTIVE_CONTENT", message);
	}

	public record SanitizedMarkup(org.jsoup.nodes.Document document, boolean removedUnsafeContent) {
	}
}
