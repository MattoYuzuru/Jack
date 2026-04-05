package com.keykomi.jack.processing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoredProcessingJob(
	UUID id,
	UUID uploadId,
	ProcessingJobType type,
	ProcessingJobStatus status,
	int progressPercent,
	String message,
	String errorMessage,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	List<StoredArtifact> artifacts
) {

	public static StoredProcessingJob queued(UUID id, UUID uploadId, ProcessingJobType type, Instant createdAt) {
		return new StoredProcessingJob(
			id,
			uploadId,
			type,
			ProcessingJobStatus.QUEUED,
			0,
			"Job поставлен в очередь.",
			null,
			createdAt,
			null,
			null,
			List.of()
		);
	}

	public StoredProcessingJob start(Instant startedAt, String message) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			ProcessingJobStatus.RUNNING,
			10,
			message,
			null,
			this.createdAt,
			startedAt,
			null,
			List.of()
		);
	}

	public StoredProcessingJob updateProgress(int progressPercent, String message) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			this.status,
			Math.max(0, Math.min(progressPercent, 100)),
			message,
			this.errorMessage,
			this.createdAt,
			this.startedAt,
			this.completedAt,
			this.artifacts
		);
	}

	public StoredProcessingJob complete(Instant completedAt, String message, List<StoredArtifact> artifacts) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			ProcessingJobStatus.COMPLETED,
			100,
			message,
			null,
			this.createdAt,
			this.startedAt,
			completedAt,
			List.copyOf(artifacts)
		);
	}

	public StoredProcessingJob fail(Instant completedAt, String errorMessage) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			ProcessingJobStatus.FAILED,
			this.progressPercent,
			"Job завершился с ошибкой.",
			errorMessage,
			this.createdAt,
			this.startedAt,
			completedAt,
			this.artifacts
		);
	}

}
