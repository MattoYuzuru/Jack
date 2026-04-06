package com.keykomi.jack.processing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PdfToolkitPayloads {

	private PdfToolkitPayloads() {
	}

	public record PdfToolkitManifest(
		UUID uploadId,
		String originalFileName,
		String operation,
		String resultFileName,
		String resultMediaType,
		String previewFileName,
		String previewMediaType,
		String previewKind,
		Integer sourcePageCount,
		Integer resultPageCount,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		List<PdfFact> sourceFacts,
		List<PdfFact> resultFacts,
		List<PdfFact> operationFacts,
		List<String> warnings,
		Instant generatedAt
	) {
	}

	public record PdfFact(
		String label,
		String value
	) {
	}

}
