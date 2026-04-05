package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.StoredUpload;
import java.util.Set;
import java.util.List;

public final class ProcessingFileFamilyResolver {

	private static final Set<String> IMAGE_EXTENSIONS = Set.of(
		"jpg", "jpeg", "png", "webp", "bmp", "svg", "ico",
		"heic", "heif", "tiff", "tif", "raw", "dng", "cr2", "cr3",
		"nef", "arw", "raf", "rw2", "orf", "pef", "srw", "psd",
		"ai", "eps", "ps"
	);
	private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
		"pdf", "txt", "text", "csv", "html", "htm", "rtf", "doc", "docx",
		"odt", "xls", "xlsx", "pptx", "epub", "sqlite", "db"
	);

	private ProcessingFileFamilyResolver() {
	}

	public static String detectFamily(StoredUpload upload) {
		var mediaType = upload.mediaType().toLowerCase();
		var extension = upload.extension().toLowerCase();
		if (mediaType.startsWith("image/")) {
			return "image";
		}
		if (IMAGE_EXTENSIONS.contains(extension)) {
			return "image";
		}
		if (mediaType.startsWith("video/")) {
			return "media";
		}
		if (mediaType.startsWith("audio/")) {
			return "audio";
		}
		if (mediaType.startsWith("text/") || DOCUMENT_EXTENSIONS.contains(extension)) {
			return "document";
		}

		return "unknown";
	}

}
