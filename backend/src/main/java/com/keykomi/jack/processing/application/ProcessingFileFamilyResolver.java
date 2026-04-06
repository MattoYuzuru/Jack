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
		"pdf", "txt", "text", "log", "sql", "md", "markdown", "json", "yaml", "yml",
		"xml", "env", "csv", "tsv", "html", "htm", "rtf", "doc", "docx",
		"odt", "xls", "xlsx", "ods", "pptx", "epub", "sqlite", "db"
	);
	private static final Set<String> VIDEO_EXTENSIONS = Set.of(
		"mp4", "mov", "webm", "avi", "mkv", "wmv", "flv"
	);
	private static final Set<String> AUDIO_EXTENSIONS = Set.of(
		"mp3", "wav", "aac", "m4a", "flac", "ogg", "opus", "aiff", "aif"
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
		if (VIDEO_EXTENSIONS.contains(extension)) {
			return "media";
		}
		if (mediaType.startsWith("audio/")) {
			return "audio";
		}
		if (AUDIO_EXTENSIONS.contains(extension)) {
			return "audio";
		}
		if (mediaType.startsWith("text/") || DOCUMENT_EXTENSIONS.contains(extension)) {
			return "document";
		}

		return "unknown";
	}

}
