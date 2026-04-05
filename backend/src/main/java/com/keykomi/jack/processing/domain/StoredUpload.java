package com.keykomi.jack.processing.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record StoredUpload(
	UUID id,
	String originalFileName,
	String mediaType,
	String extension,
	long sizeBytes,
	String sha256,
	Instant createdAt,
	Path storagePath
) {
}
