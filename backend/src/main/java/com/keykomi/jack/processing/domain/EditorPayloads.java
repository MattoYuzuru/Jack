package com.keykomi.jack.processing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EditorPayloads {

	private EditorPayloads() {
	}

	public record EditorManifest(
		UUID uploadId,
		String originalFileName,
		String formatId,
		String formatLabel,
		String syntaxMode,
		String previewMode,
		String runtimeLabel,
		List<DocumentPreviewPayload.DocumentFact> summary,
		List<EditorIssue> issues,
		List<EditorOutlineItem> outline,
		List<String> suggestions,
		String readyFileName,
		String readyMediaType,
		String plainTextFileName,
		String plainTextMediaType,
		Instant generatedAt
	) {
	}

	public record EditorIssue(
		String severity,
		String code,
		String message,
		Integer line,
		Integer column,
		Integer endLine,
		Integer endColumn,
		String hint,
		String quickFixCode
	) {
		public EditorIssue(
			String severity,
			String code,
			String message,
			Integer line,
			Integer column,
			String hint
		) {
			this(severity, code, message, line, column, line, column, hint, resolveQuickFixCode(code));
		}

		private static String resolveQuickFixCode(String code) {
			if (code == null) {
				return null;
			}
			if (code.endsWith("_PARSE_ERROR")) {
				return "repair-structured-syntax";
			}
			if (code.endsWith("_DELIMITER") || code.endsWith("_CLOSER")) {
				return "balance-delimiters";
			}
			return switch (code) {
				case "HTML_NO_NOOPENER" -> "add-noopener";
				case "HTML_INLINE_HANDLER" -> "extract-inline-handler";
				case "HTML_JAVASCRIPT_URL" -> "remove-unsafe-url";
				default -> null;
			};
		}
	}

	public record EditorOutlineItem(
		String id,
		String label,
		int depth,
		String kind
	) {
	}

}
