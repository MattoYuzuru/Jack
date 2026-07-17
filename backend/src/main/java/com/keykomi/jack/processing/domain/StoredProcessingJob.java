package com.keykomi.jack.processing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StoredProcessingJob(
	UUID id,
	UUID uploadId,
	ProcessingJobType type,
	Map<String, Object> parameters,
	ProcessingJobStatus status,
	int progressPercent,
	String message,
	String errorCode,
	String errorMessage,
	UUID correlationId,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	Instant expiresAt,
	String policyVersion,
	List<StoredArtifact> artifacts
) {

	public static StoredProcessingJob queued(
		UUID id,
		UUID uploadId,
		ProcessingJobType type,
		Map<String, Object> parameters,
		Instant createdAt,
		Instant expiresAt,
		String policyVersion,
		UUID correlationId
	) {
		return new StoredProcessingJob(
			id,
			uploadId,
			type,
			Map.copyOf(parameters),
			ProcessingJobStatus.QUEUED,
			0,
			"Job поставлен в очередь.",
			null,
			null,
			correlationId,
			createdAt,
			null,
			null,
			expiresAt,
			policyVersion,
			List.of()
		);
	}

	public StoredProcessingJob start(Instant startedAt, String message) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			this.parameters,
			ProcessingJobStatus.RUNNING,
			10,
			message,
			null,
			null,
			this.correlationId,
			this.createdAt,
			startedAt,
			null,
			this.expiresAt,
			this.policyVersion,
			List.of()
		);
	}

	public StoredProcessingJob updateProgress(int progressPercent, String message) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			this.parameters,
			this.status,
			Math.max(0, Math.min(progressPercent, 100)),
			message,
			this.errorCode,
			this.errorMessage,
			this.correlationId,
			this.createdAt,
			this.startedAt,
			this.completedAt,
			this.expiresAt,
			this.policyVersion,
			this.artifacts
		);
	}

	public StoredProcessingJob complete(Instant completedAt, String message, List<StoredArtifact> artifacts) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			this.parameters,
			ProcessingJobStatus.COMPLETED,
			100,
			message,
			null,
			null,
			this.correlationId,
			this.createdAt,
			this.startedAt,
			completedAt,
			this.expiresAt,
			this.policyVersion,
			List.copyOf(artifacts)
		);
	}

	public StoredProcessingJob fail(Instant completedAt, String errorCode, String errorMessage) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			this.parameters,
			ProcessingJobStatus.FAILED,
			this.progressPercent,
			"Job завершился с ошибкой.",
			errorCode,
			errorMessage,
			this.correlationId,
			this.createdAt,
			this.startedAt,
			completedAt,
			this.expiresAt,
			this.policyVersion,
			this.artifacts
		);
	}

	public StoredProcessingJob cancel(Instant completedAt, String message) {
		return new StoredProcessingJob(
			this.id,
			this.uploadId,
			this.type,
			this.parameters,
			ProcessingJobStatus.CANCELLED,
			this.progressPercent,
			message,
			null,
			null,
			this.correlationId,
			this.createdAt,
			this.startedAt,
			completedAt,
			this.expiresAt,
			this.policyVersion,
			this.artifacts
		);
	}

	public boolean isTerminal() {
		return this.status == ProcessingJobStatus.COMPLETED
			|| this.status == ProcessingJobStatus.FAILED
			|| this.status == ProcessingJobStatus.CANCELLED;
	}

}
