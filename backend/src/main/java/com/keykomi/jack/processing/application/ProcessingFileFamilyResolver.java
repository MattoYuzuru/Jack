package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.StoredUpload;
import java.util.List;

public final class ProcessingFileFamilyResolver {

	private ProcessingFileFamilyResolver() {
	}

	public static String detectFamily(StoredUpload upload) {
		var mediaType = upload.mediaType().toLowerCase();
		if (mediaType.startsWith("image/")) {
			return "image";
		}
		if (mediaType.startsWith("video/")) {
			return "media";
		}
		if (mediaType.startsWith("audio/")) {
			return "audio";
		}
		if (mediaType.startsWith("text/") || List.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "epub", "sqlite", "db").contains(upload.extension())) {
			return "document";
		}

		return "unknown";
	}

}
