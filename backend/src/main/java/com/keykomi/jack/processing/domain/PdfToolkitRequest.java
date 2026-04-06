package com.keykomi.jack.processing.domain;

import java.util.List;
import java.util.UUID;

public record PdfToolkitRequest(
	Operation operation,
	List<UUID> additionalUploadIds,
	List<String> splitRanges,
	String pageSelection,
	Integer rotationDegrees,
	List<Integer> pageOrder,
	String currentPassword,
	String userPassword,
	String ownerPassword,
	Boolean allowPrinting,
	Boolean allowCopying,
	Boolean allowModifying,
	String signatureText,
	UUID signatureImageUploadId,
	String signaturePlacement,
	Boolean includeSignatureDate,
	List<String> redactTerms,
	String ocrLanguage
) {

	public enum Operation {

		MERGE,
		SPLIT,
		ROTATE,
		REORDER,
		OCR,
		SIGN,
		REDACT,
		PROTECT,
		UNLOCK

	}

}
