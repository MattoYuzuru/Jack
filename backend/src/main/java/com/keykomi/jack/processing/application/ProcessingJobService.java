package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobStatus;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProcessingJobService {

	private final UploadStorageService uploadStorageService;
	private final ArtifactStorageService artifactStorageService;
	private final ExecutorService processingExecutor;
	private final Map<UUID, StoredProcessingJob> jobs = new ConcurrentHashMap<>();

	public ProcessingJobService(
		UploadStorageService uploadStorageService,
		ArtifactStorageService artifactStorageService,
		ExecutorService processingExecutor
	) {
		this.uploadStorageService = uploadStorageService;
		this.artifactStorageService = artifactStorageService;
		this.processingExecutor = processingExecutor;
	}

	public StoredProcessingJob enqueue(UUID uploadId, ProcessingJobType jobType) {
		var upload = this.uploadStorageService.getRequiredUpload(uploadId);
		ensureJobTypeSupported(jobType);

		var job = StoredProcessingJob.queued(UUID.randomUUID(), upload.id(), jobType, Instant.now());
		this.jobs.put(job.id(), job);

		// Даже foundation-срез сразу запускаем через async executor, чтобы следующие
		// фазы с ffmpeg/document/imaging не ломали уже заведённый job lifecycle contract.
		this.processingExecutor.submit(() -> process(job.id()));
		return job;
	}

	public StoredProcessingJob getRequiredJob(UUID jobId) {
		return findJob(jobId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processing job не найден."));
	}

	public Optional<StoredProcessingJob> findJob(UUID jobId) {
		return Optional.ofNullable(this.jobs.get(jobId));
	}

	public StoredArtifact getRequiredArtifact(UUID jobId, UUID artifactId) {
		return getRequiredJob(jobId)
			.artifacts()
			.stream()
			.filter(artifact -> artifact.id().equals(artifactId))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact не найден у указанного job."));
	}

	private void process(UUID jobId) {
		try {
			updateJob(jobId, job -> job.start(Instant.now(), "Начинаю intake-analysis для backend processing foundation."));

			var job = getRequiredJob(jobId);
			var upload = this.uploadStorageService.getRequiredUpload(job.uploadId());

			updateJob(jobId, currentJob -> currentJob.updateProgress(45, "Собираю manifest и проверяю upload metadata."));
			var artifact = switch (job.type()) {
				case UPLOAD_INTAKE_ANALYSIS -> buildUploadIntakeArtifact(job.id(), upload);
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для этого job type backend processor ещё не реализован.");
			};

			updateJob(
				jobId,
				currentJob -> currentJob.complete(
					Instant.now(),
					"Intake-analysis завершён, manifest artifact готов к скачиванию.",
					List.of(artifact)
				)
			);
		}
		catch (Exception exception) {
			updateJob(jobId, job -> job.fail(Instant.now(), resolveErrorMessage(exception)));
		}
	}

	private StoredArtifact buildUploadIntakeArtifact(UUID jobId, StoredUpload upload) {
		try {
			// Первый artifact здесь намеренно простой: он доказывает, что upload уже
			// прошёл через storage/job pipeline и может дальше обрабатываться server-side.
			var payload = new UploadIntakeManifest(
				upload.id(),
				upload.originalFileName(),
				upload.mediaType(),
				upload.extension(),
				upload.sizeBytes(),
				upload.sha256(),
				detectUploadFamily(upload),
				Files.probeContentType(upload.storagePath()),
				upload.createdAt(),
				List.of(
					"Это foundation artifact: backend пока не декодирует формат, а подтверждает intake/storage/job flow.",
					"Следующие фазы навесят на этот же job pipeline ffmpeg, imaging и document adapters."
				)
			);
			return this.artifactStorageService.storeJsonArtifact(jobId, "upload-manifest", "upload-intake-manifest.json", payload);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать upload manifest artifact.", exception);
		}
	}

	private void ensureJobTypeSupported(ProcessingJobType jobType) {
		if (jobType != ProcessingJobType.UPLOAD_INTAKE_ANALYSIS) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Этот job type уже описан в backend plan, но ещё не реализован в текущем foundation-срезе."
			);
		}
	}

	private void updateJob(UUID jobId, UnaryOperator<StoredProcessingJob> mutation) {
		this.jobs.compute(jobId, (ignored, currentJob) -> currentJob == null ? null : mutation.apply(currentJob));
	}

	private String detectUploadFamily(StoredUpload upload) {
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

	private String resolveErrorMessage(Exception exception) {
		if (exception instanceof ResponseStatusException responseStatusException && responseStatusException.getReason() != null) {
			return responseStatusException.getReason();
		}

		return exception.getMessage() != null ? exception.getMessage() : "Неизвестная ошибка processing job.";
	}

	public record UploadIntakeManifest(
		UUID uploadId,
		String originalFileName,
		String mediaType,
		String extension,
		long sizeBytes,
		String sha256,
		String detectedFamily,
		String probedContentType,
		Instant uploadedAt,
		List<String> notes
	) {
	}

}
