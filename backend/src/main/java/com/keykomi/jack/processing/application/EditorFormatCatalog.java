package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class EditorFormatCatalog {

	static final List<EditorFormatSpec> FORMAT_SPECS = List.of(
		new EditorFormatSpec(
			"markdown",
			"Markdown",
			List.of("md", "markdown"),
			List.of("text/markdown", "text/x-markdown"),
			"markdown",
			"rendered",
			true,
			true,
			"Split view с rendered preview, outline по heading'ам и безопасными export-артефактами.",
			List.of("Preview", "Outline", "Snippets"),
			List.of(ProcessingJobType.EDITOR_PROCESS),
			List.of("Headings", "Links", "Code fences")
		),
		new EditorFormatSpec(
			"html",
			"HTML",
			List.of("html", "htm"),
			List.of("text/html"),
			"html",
			"sandbox",
			true,
			true,
			"Backend валидирует risky tags/attrs, а frontend рендерит sandbox preview без выполнения script payload.",
			List.of("Sandbox", "Sanitize", "Structure"),
			List.of(ProcessingJobType.EDITOR_PROCESS, ProcessingJobType.DOCUMENT_PREVIEW),
			List.of("Sections", "Sandbox", "Accessibility")
		),
		new EditorFormatSpec(
			"css",
			"CSS",
			List.of("css"),
			List.of("text/css"),
			"css",
			"sandbox",
			true,
			true,
			"Editor даёт live style preview на безопасном sample canvas и предупреждает про risky CSS primitives.",
			List.of("Styles", "Selectors", "Preview"),
			List.of(ProcessingJobType.EDITOR_PROCESS),
			List.of("Variables", "Grid", "Media queries")
		),
		new EditorFormatSpec(
			"javascript",
			"JavaScript",
			List.of("js", "mjs", "cjs"),
			List.of("text/javascript", "application/javascript", "application/x-javascript"),
			"javascript",
			"syntax",
			true,
			true,
			"Фронтенд форматирует код как IDE, а backend поднимает diagnostics по unsafe execution-паттернам и structural issues.",
			List.of("Async", "Diagnostics", "Exports"),
			List.of(ProcessingJobType.EDITOR_PROCESS),
			List.of("Functions", "Async", "Fetch")
		),
		new EditorFormatSpec(
			"json",
			"JSON",
			List.of("json"),
			List.of("application/json", "text/json"),
			"json",
			"structured",
			true,
			true,
			"JSON проходит strict parse на backend и получает structured tree preview без browser-side eval.",
			List.of("Tree", "Strict", "Config"),
			List.of(ProcessingJobType.EDITOR_PROCESS),
			List.of("Objects", "Arrays", "Payloads")
		),
		new EditorFormatSpec(
			"yaml",
			"YAML",
			List.of("yaml", "yml"),
			List.of("application/yaml", "text/yaml", "text/x-yaml"),
			"yaml",
			"structured",
			true,
			true,
			"YAML проходит strict parse через backend parser и получает diagnostics по indentation и key structure.",
			List.of("Config", "Indent", "Tree"),
			List.of(ProcessingJobType.EDITOR_PROCESS),
			List.of("Mappings", "Lists", "Anchors")
		),
		new EditorFormatSpec(
			"txt",
			"Plain Text",
			List.of("txt", "text"),
			List.of("text/plain"),
			"txt",
			"text",
			false,
			true,
			"Быстрый текстовый режим с draft persistence, line stats и безопасным server export contract.",
			List.of("Notes", "Drafts", "Stats"),
			List.of(ProcessingJobType.EDITOR_PROCESS, ProcessingJobType.DOCUMENT_PREVIEW),
			List.of("Notes", "Checklists", "Quick drafts")
		)
	);

	private EditorFormatCatalog() {
	}

	static String buildAcceptAttribute() {
		var extensions = new LinkedHashSet<String>();

		for (EditorFormatSpec spec : FORMAT_SPECS) {
			for (String extension : spec.extensions()) {
				extensions.add("." + extension);
			}
		}

		return String.join(",", extensions);
	}

	static EditorFormatSpec resolveById(String formatId) {
		if (formatId == null || formatId.isBlank()) {
			return null;
		}

		var normalized = normalize(formatId);
		return FORMAT_SPECS.stream()
			.filter(spec -> spec.id().equals(normalized))
			.findFirst()
			.orElse(null);
	}

	static EditorFormatSpec resolveByExtension(String extension) {
		if (extension == null || extension.isBlank()) {
			return null;
		}

		var normalized = normalize(extension);
		return FORMAT_SPECS.stream()
			.filter(spec -> spec.extensions().contains(normalized))
			.findFirst()
			.orElse(null);
	}

	static EditorFormatSpec resolveByMimeType(String mimeType) {
		if (mimeType == null || mimeType.isBlank()) {
			return null;
		}

		var normalized = mimeType.trim().toLowerCase(Locale.ROOT);
		return FORMAT_SPECS.stream()
			.filter(spec -> spec.mimeTypes().contains(normalized))
			.findFirst()
			.orElse(null);
	}

	static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
	}

	record EditorFormatSpec(
		String id,
		String label,
		List<String> extensions,
		List<String> mimeTypes,
		String syntaxMode,
		String previewMode,
		boolean supportsFormatting,
		boolean supportsPlainTextExport,
		String notes,
		List<String> accents,
		List<ProcessingJobType> requiredJobTypes,
		List<String> suggestions
	) {
		Set<String> extensionSet() {
			return Set.copyOf(this.extensions);
		}
	}

}
