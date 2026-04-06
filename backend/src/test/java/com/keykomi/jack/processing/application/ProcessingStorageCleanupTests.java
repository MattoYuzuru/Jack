package com.keykomi.jack.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ProcessingStorageCleanupTests {

	@TempDir
	Path tempDir;

	@Test
	void uploadStorageServicePurgesExpiredUploadsButKeepsRetainedOnes() throws Exception {
		var properties = properties(this.tempDir);
		var uploadStorageService = new UploadStorageService(properties);
		var staleUpload = uploadStorageService.store(
			new MockMultipartFile(
				"file",
				"stale.txt",
				"text/plain",
				"stale".getBytes(StandardCharsets.UTF_8)
			)
		);
		var retainedUpload = uploadStorageService.store(
			new MockMultipartFile(
				"file",
				"retained.txt",
				"text/plain",
				"retained".getBytes(StandardCharsets.UTF_8)
			)
		);

		var deletedUploads = uploadStorageService.purgeExpired(
			Instant.now().plusSeconds(5),
			Set.of(retainedUpload.id())
		);

		assertThat(deletedUploads).isGreaterThanOrEqualTo(1);
		assertThat(uploadStorageService.findUpload(staleUpload.id())).isEmpty();
		assertThat(Files.exists(staleUpload.storagePath())).isFalse();
		assertThat(uploadStorageService.findUpload(retainedUpload.id())).isPresent();
		assertThat(Files.exists(retainedUpload.storagePath())).isTrue();
	}

	@Test
	void artifactStorageServicePurgesExpiredDirectoriesButKeepsRetainedJobArtifacts() throws Exception {
		var properties = properties(this.tempDir);
		var artifactStorageService = new ArtifactStorageService(properties, new ObjectMapper().findAndRegisterModules());
		var staleJobId = UUID.randomUUID();
		var retainedJobId = UUID.randomUUID();

		artifactStorageService.storeBytesArtifact(
			staleJobId,
			"artifact",
			"stale.txt",
			"text/plain",
			"stale".getBytes(StandardCharsets.UTF_8)
		);
		artifactStorageService.storeBytesArtifact(
			retainedJobId,
			"artifact",
			"retained.txt",
			"text/plain",
			"retained".getBytes(StandardCharsets.UTF_8)
		);

		var staleJobDirectory = properties.artifactsDirectory().resolve(staleJobId.toString());
		var retainedJobDirectory = properties.artifactsDirectory().resolve(retainedJobId.toString());

		var deletedDirectories = artifactStorageService.purgeExpired(
			Instant.now().plusSeconds(5),
			Set.of(retainedJobId)
		);

		assertThat(deletedDirectories).isEqualTo(1);
		assertThat(Files.exists(staleJobDirectory)).isFalse();
		assertThat(Files.exists(retainedJobDirectory)).isTrue();
	}

	@Test
	void processingJobServicePurgesOnlyExpiredTerminalJobs() {
		var processingJobService = new ProcessingJobService(
			mock(UploadStorageService.class),
			mock(ArtifactStorageService.class),
			mock(MediaPreviewService.class),
			mock(MediaConversionService.class),
			mock(ImageProcessingService.class),
			mock(CompressionService.class),
			mock(PdfToolkitService.class),
			mock(OfficeConversionService.class),
			mock(DocumentPreviewService.class),
			mock(MetadataProcessingService.class),
			mock(ViewerResolveService.class),
			mock(EditorProcessingService.class),
			mock(ExecutorService.class),
			new SimpleMeterRegistry()
		);
		@SuppressWarnings("unchecked")
		Map<UUID, StoredProcessingJob> jobs = (ConcurrentHashMap<UUID, StoredProcessingJob>) ReflectionTestUtils.getField(
			processingJobService,
			"jobs"
		);
		assertThat(jobs).isNotNull();

		var expiredJob = StoredProcessingJob.queued(
			UUID.randomUUID(),
			UUID.randomUUID(),
			ProcessingJobType.DOCUMENT_PREVIEW,
			Map.of(),
			Instant.now().minusSeconds(10_000)
		).complete(Instant.now().minusSeconds(7_200), "done", List.of());
		var recentJob = StoredProcessingJob.queued(
			UUID.randomUUID(),
			UUID.randomUUID(),
			ProcessingJobType.DOCUMENT_PREVIEW,
			Map.of(),
			Instant.now().minusSeconds(60)
		).complete(Instant.now().minusSeconds(30), "done", List.of());
		var runningJob = StoredProcessingJob.queued(
			UUID.randomUUID(),
			UUID.randomUUID(),
			ProcessingJobType.DOCUMENT_PREVIEW,
			Map.of(),
			Instant.now().minusSeconds(120)
		).start(Instant.now().minusSeconds(100), "running");

		jobs.put(expiredJob.id(), expiredJob);
		jobs.put(recentJob.id(), recentJob);
		jobs.put(runningJob.id(), runningJob);

		var deletedJobs = processingJobService.purgeExpiredTerminalJobs(Instant.now().minusSeconds(3_600));

		assertThat(deletedJobs).isEqualTo(1);
		assertThat(processingJobService.findJob(expiredJob.id())).isEmpty();
		assertThat(processingJobService.findJob(recentJob.id())).isPresent();
		assertThat(processingJobService.findJob(runningJob.id())).isPresent();
	}

	private ProcessingProperties properties(Path root) {
		var properties = new ProcessingProperties();
		properties.setStorageRoot(root);
		return properties;
	}

}
