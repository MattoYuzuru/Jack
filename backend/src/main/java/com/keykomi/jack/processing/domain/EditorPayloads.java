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
		String hint
	) {
	}

	public record EditorOutlineItem(
		String id,
		String label,
		int depth,
		String kind
	) {
	}

}
