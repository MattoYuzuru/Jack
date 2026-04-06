package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProcessingStorageCleanupService {

	private final ProcessingProperties processingProperties;
	private final UploadStorageService uploadStorageService;
	private final ArtifactStorageService artifactStorageService;
	private final ProcessingJobService processingJobService;
	private final Counter deletedUploadsCounter;
	private final Counter deletedArtifactDirectoriesCounter;
	private final Counter deletedJobsCounter;

	public ProcessingStorageCleanupService(
		ProcessingProperties processingProperties,
		UploadStorageService uploadStorageService,
		ArtifactStorageService artifactStorageService,
		ProcessingJobService processingJobService,
		MeterRegistry meterRegistry
	) {
		this.processingProperties = processingProperties;
		this.uploadStorageService = uploadStorageService;
		this.artifactStorageService = artifactStorageService;
		this.processingJobService = processingJobService;
		this.deletedUploadsCounter = Counter.builder("jack.processing.cleanup.uploads.deleted.total")
			.description("Количество upload-файлов, удалённых TTL cleanup политикой.")
			.register(meterRegistry);
		this.deletedArtifactDirectoriesCounter = Counter.builder("jack.processing.cleanup.artifact_directories.deleted.total")
			.description("Количество artifact-директорий, удалённых TTL cleanup политикой.")
			.register(meterRegistry);
		this.deletedJobsCounter = Counter.builder("jack.processing.cleanup.jobs.deleted.total")
			.description("Количество terminal job-записей, удалённых TTL cleanup политикой.")
			.register(meterRegistry);
	}

	@Scheduled(
		fixedDelayString = "${jack.processing.cleanup-interval-millis:1800000}",
		initialDelayString = "${jack.processing.cleanup-interval-millis:1800000}"
	)
	void purgeExpiredState() {
		var now = Instant.now();
		var activeJobIds = this.processingJobService.listActiveJobIds();
		var activeUploadIds = this.processingJobService.listActiveUploadIds();

		var deletedUploads = this.uploadStorageService.purgeExpired(
			now.minus(this.processingProperties.getUploadRetentionHours(), ChronoUnit.HOURS),
			activeUploadIds
		);
		var deletedArtifactDirectories = this.artifactStorageService.purgeExpired(
			now.minus(this.processingProperties.getArtifactRetentionHours(), ChronoUnit.HOURS),
			activeJobIds
		);
		var deletedJobs = this.processingJobService.purgeExpiredTerminalJobs(
			now.minus(this.processingProperties.getJobRetentionHours(), ChronoUnit.HOURS)
		);

		if (deletedUploads > 0) {
			this.deletedUploadsCounter.increment(deletedUploads);
		}
		if (deletedArtifactDirectories > 0) {
			this.deletedArtifactDirectoriesCounter.increment(deletedArtifactDirectories);
		}
		if (deletedJobs > 0) {
			this.deletedJobsCounter.increment(deletedJobs);
		}
	}

}
