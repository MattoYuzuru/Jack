package com.keykomi.jack.processing.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record StoredArtifact(
	UUID id,
	UUID jobId,
	String kind,
	String fileName,
	String mediaType,
	long sizeBytes,
	String sha256,
	Instant createdAt,
	Instant expiresAt,
	Path storagePath
) {
}
