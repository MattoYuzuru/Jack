package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobStatus;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.ImageProcessingRequest;
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
	private final MediaPreviewService mediaPreviewService;
	private final ImageProcessingService imageProcessingService;
	private final DocumentPreviewService documentPreviewService;
	private final ExecutorService processingExecutor;
	private final Map<UUID, StoredProcessingJob> jobs = new ConcurrentHashMap<>();

	public ProcessingJobService(
		UploadStorageService uploadStorageService,
		ArtifactStorageService artifactStorageService,
		MediaPreviewService mediaPreviewService,
		ImageProcessingService imageProcessingService,
		DocumentPreviewService documentPreviewService,
		ExecutorService processingExecutor
	) {
		this.uploadStorageService = uploadStorageService;
		this.artifactStorageService = artifactStorageService;
		this.mediaPreviewService = mediaPreviewService;
		this.imageProcessingService = imageProcessingService;
		this.documentPreviewService = documentPreviewService;
		this.processingExecutor = processingExecutor;
	}

	public StoredProcessingJob enqueue(UUID uploadId, ProcessingJobType jobType, Map<String, Object> parameters) {
		var upload = this.uploadStorageService.getRequiredUpload(uploadId);
		var normalizedParameters = parameters == null ? Map.<String, Object>of() : Map.copyOf(parameters);
		ensureJobTypeSupported(jobType, normalizedParameters);

		var job = StoredProcessingJob.queued(UUID.randomUUID(), upload.id(), jobType, normalizedParameters, Instant.now());
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
			updateJob(jobId, job -> job.start(Instant.now(), startMessage(job.type())));

			var job = getRequiredJob(jobId);
			var upload = this.uploadStorageService.getRequiredUpload(job.uploadId());

			var result = switch (job.type()) {
				case UPLOAD_INTAKE_ANALYSIS -> processUploadIntakeAnalysis(job.id(), upload);
				case MEDIA_PREVIEW -> processMediaPreview(job.id(), upload);
				case IMAGE_CONVERT -> processImageConvert(job.id(), upload, parseImageJobRequest(job.parameters()));
				case DOCUMENT_PREVIEW -> processDocumentPreview(job.id(), upload);
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для этого job type backend processor ещё не реализован.");
			};

			updateJob(
				jobId,
				currentJob -> currentJob.complete(
					Instant.now(),
					result.message(),
					result.artifacts()
				)
			);
		}
		catch (Exception exception) {
			updateJob(jobId, job -> job.fail(Instant.now(), resolveErrorMessage(exception)));
		}
	}

	private JobProcessingResult processUploadIntakeAnalysis(UUID jobId, StoredUpload upload) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(45, "Собираю manifest и проверяю upload metadata."));
		return new JobProcessingResult(
			"Intake-analysis завершён, manifest artifact готов к скачиванию.",
			List.of(buildUploadIntakeArtifact(jobId, upload))
		);
	}

	private JobProcessingResult processMediaPreview(UUID jobId, StoredUpload upload) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(30, "Проверяю media container через ffprobe."));
		var result = this.mediaPreviewService.buildPreview(jobId, upload);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(85, "Media preview собран, сохраняю artifact и manifest.")
		);
		return new JobProcessingResult(
			"Media preview готов через backend %s path.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
	}

	private JobProcessingResult processImageConvert(UUID jobId, StoredUpload upload, ImageProcessingRequest request) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(25, "Подготавливаю imaging source и raster contract."));
		var result = this.imageProcessingService.process(jobId, upload, request);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(85, "Imaging artifacts собраны, сохраняю manifest и output blobs.")
		);
		return new JobProcessingResult(
			"Image processing готов через backend %s.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
	}

	private JobProcessingResult processDocumentPreview(UUID jobId, StoredUpload upload) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(25, "Подготавливаю document intelligence payload и search layer."));
		var result = this.documentPreviewService.process(jobId, upload);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(85, "Document manifest и preview artifacts собраны, сохраняю результат.")
		);
		return new JobProcessingResult(
			"Document preview готов через backend %s.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
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
				ProcessingFileFamilyResolver.detectFamily(upload),
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

	private void ensureJobTypeSupported(ProcessingJobType jobType, Map<String, Object> parameters) {
		if (
			jobType != ProcessingJobType.UPLOAD_INTAKE_ANALYSIS &&
			jobType != ProcessingJobType.MEDIA_PREVIEW &&
			jobType != ProcessingJobType.IMAGE_CONVERT &&
			jobType != ProcessingJobType.DOCUMENT_PREVIEW
		) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Этот job type уже описан в backend plan, но ещё не реализован в текущем foundation-срезе."
			);
		}

		if (jobType == ProcessingJobType.MEDIA_PREVIEW && !this.mediaPreviewService.isAvailable()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"MEDIA_PREVIEW job требует доступных ffmpeg/ffprobe binaries в backend окружении."
			);
		}

		if (jobType == ProcessingJobType.IMAGE_CONVERT) {
			parseImageJobRequest(parameters);
			if (!this.imageProcessingService.isAvailable()) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"IMAGE_CONVERT job требует доступных convert/ffmpeg/potrace/raw-preview binaries в backend окружении."
				);
			}
		}

		if (jobType == ProcessingJobType.DOCUMENT_PREVIEW && !this.documentPreviewService.isAvailable()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"DOCUMENT_PREVIEW job требует доступного backend document intelligence service."
			);
		}
	}

	private void updateJob(UUID jobId, UnaryOperator<StoredProcessingJob> mutation) {
		this.jobs.compute(jobId, (ignored, currentJob) -> currentJob == null ? null : mutation.apply(currentJob));
	}

	private String resolveErrorMessage(Exception exception) {
		if (exception instanceof ResponseStatusException responseStatusException && responseStatusException.getReason() != null) {
			return responseStatusException.getReason();
		}

		return exception.getMessage() != null ? exception.getMessage() : "Неизвестная ошибка processing job.";
	}

	private String startMessage(ProcessingJobType jobType) {
		return switch (jobType) {
			case UPLOAD_INTAKE_ANALYSIS -> "Начинаю intake-analysis для backend processing foundation.";
			case MEDIA_PREVIEW -> "Запускаю backend media preview pipeline через ffprobe/ffmpeg.";
			case IMAGE_CONVERT -> "Запускаю backend imaging pipeline через convert/ffmpeg/potrace.";
			case DOCUMENT_PREVIEW -> "Запускаю backend document intelligence pipeline.";
			default -> "Запускаю backend processing job.";
		};
	}

	private ImageProcessingRequest parseImageJobRequest(Map<String, Object> parameters) {
		var operation = readRequiredString(parameters, "operation");
		if (!"preview".equals(operation) && !"convert".equals(operation)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_CONVERT принимает operation только preview или convert.");
		}

		var targetExtension = readOptionalString(parameters, "targetExtension");
		if ("convert".equals(operation) && (targetExtension == null || targetExtension.isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_CONVERT convert path требует targetExtension.");
		}

		return new ImageProcessingRequest(
			operation,
			targetExtension == null ? null : targetExtension.toLowerCase(),
			readOptionalInteger(parameters, "maxWidth"),
			readOptionalInteger(parameters, "maxHeight"),
			readOptionalDouble(parameters, "quality"),
			readOptionalString(parameters, "backgroundColor"),
			readOptionalString(parameters, "presetLabel")
		);
	}

	private String readRequiredString(Map<String, Object> parameters, String key) {
		var value = readOptionalString(parameters, key);
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s обязателен для processing job.".formatted(key));
		}
		return value;
	}

	private String readOptionalString(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		return String.valueOf(value);
	}

	private Integer readOptionalInteger(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.valueOf(String.valueOf(value));
		}
		catch (NumberFormatException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть integer.".formatted(key), exception);
		}
	}

	private Double readOptionalDouble(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		try {
			return Double.valueOf(String.valueOf(value));
		}
		catch (NumberFormatException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть numeric.".formatted(key), exception);
		}
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

	private record JobProcessingResult(
		String message,
		List<StoredArtifact> artifacts
	) {
	}

}
