package com.keykomi.jack.processing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.keykomi.jack.processing.domain.ProcessingJobStatus;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ProcessingStorageCleanupTests {

	private static final Path STORAGE_ROOT = createStorageRoot();

	@Autowired
	private ProcessingStateStore stateStore;

	@Autowired
	private UploadStorageService uploadStorageService;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", STORAGE_ROOT::toString);
	}

	@Test
	void cleanupIsIdempotentAndKeepsRetainedUploads() throws Exception {
		var ownerId = UUID.randomUUID();
		var old = Instant.now().minusSeconds(10_000);
		var stale = upload(ownerId, "stale.txt", old);
		var retained = upload(ownerId, "retained.txt", old);

		var firstPass = this.uploadStorageService.purgeExpired(Instant.now().minusSeconds(3_600), Set.of(retained.id()));
		var secondPass = this.uploadStorageService.purgeExpired(Instant.now().minusSeconds(3_600), Set.of(retained.id()));

		assertThat(firstPass).isEqualTo(1);
		assertThat(secondPass).isZero();
		assertThat(this.stateStore.findUpload(stale.id())).isEmpty();
		assertThat(Files.exists(stale.storagePath())).isFalse();
		assertThat(this.stateStore.findUpload(retained.id())).isPresent();
	}

	@Test
	void restartReconciliationKeepsTerminalJobAndFailsOrphanRunningJob() throws Exception {
		var ownerId = UUID.randomUUID();
		var upload = upload(ownerId, "restart.txt", Instant.now());
		var now = Instant.now();
		var running = StoredProcessingJob.queued(
			UUID.randomUUID(), upload.id(), ProcessingJobType.DOCUMENT_PREVIEW, Map.of(),
			now, now.plusSeconds(3_600), "test-policy", UUID.randomUUID()
		).start(now, "running");
		var completed = StoredProcessingJob.queued(
			UUID.randomUUID(), upload.id(), ProcessingJobType.DOCUMENT_PREVIEW, Map.of(),
			now, now.plusSeconds(3_600), "test-policy", UUID.randomUUID()
		).complete(now, "done", java.util.List.of());
		this.stateStore.createJob(ownerId, running);
		this.stateStore.createJob(ownerId, completed);
		this.stateStore.updateJob(running.id(), ignored -> running);
		this.stateStore.updateJob(completed.id(), ignored -> completed);

		assertThat(this.stateStore.reconcileInterruptedJobs(now.plusSeconds(1))).isEqualTo(1);
		assertThat(this.stateStore.findJob(running.id())).get().extracting(StoredProcessingJob::status)
			.isEqualTo(ProcessingJobStatus.FAILED);
		assertThat(this.stateStore.findJob(completed.id())).get().extracting(StoredProcessingJob::status)
			.isEqualTo(ProcessingJobStatus.COMPLETED);
	}

	private StoredUpload upload(UUID ownerId, String fileName, Instant createdAt) throws Exception {
		var id = UUID.randomUUID();
		var path = STORAGE_ROOT.resolve("uploads").resolve(id + "-" + fileName);
		Files.createDirectories(path.getParent());
		Files.writeString(path, "payload");
		var upload = new StoredUpload(
			id, fileName, "text/plain", "txt", Files.size(path), "a".repeat(64), createdAt,
			createdAt.plusSeconds(3_600), "test-policy", path
		);
		this.stateStore.saveUpload(ownerId, upload);
		return upload;
	}

	private static Path createStorageRoot() {
		try {
			return Files.createTempDirectory("jack-processing-cleanup-tests");
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
