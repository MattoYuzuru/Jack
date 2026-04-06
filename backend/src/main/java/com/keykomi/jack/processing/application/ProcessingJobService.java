package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobStatus;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.CompressionRequest;
import com.keykomi.jack.processing.domain.EditorRequest;
import com.keykomi.jack.processing.domain.ImageProcessingRequest;
import com.keykomi.jack.processing.domain.MediaConversionRequest;
import com.keykomi.jack.processing.domain.MetadataPayloads;
import com.keykomi.jack.processing.domain.MetadataProcessingRequest;
import com.keykomi.jack.processing.domain.OfficeConversionRequest;
import com.keykomi.jack.processing.domain.PdfToolkitRequest;
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
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProcessingJobService {

	private final UploadStorageService uploadStorageService;
	private final ArtifactStorageService artifactStorageService;
	private final MediaPreviewService mediaPreviewService;
	private final MediaConversionService mediaConversionService;
	private final ImageProcessingService imageProcessingService;
	private final CompressionService compressionService;
	private final PdfToolkitService pdfToolkitService;
	private final OfficeConversionService officeConversionService;
	private final DocumentPreviewService documentPreviewService;
	private final MetadataProcessingService metadataProcessingService;
	private final ViewerResolveService viewerResolveService;
	private final EditorProcessingService editorProcessingService;
	private final ExecutorService processingExecutor;
	private final Map<UUID, StoredProcessingJob> jobs = new ConcurrentHashMap<>();
	private final Map<UUID, Future<?>> submittedJobs = new ConcurrentHashMap<>();

	public ProcessingJobService(
		UploadStorageService uploadStorageService,
		ArtifactStorageService artifactStorageService,
		MediaPreviewService mediaPreviewService,
		MediaConversionService mediaConversionService,
		ImageProcessingService imageProcessingService,
		CompressionService compressionService,
		PdfToolkitService pdfToolkitService,
		OfficeConversionService officeConversionService,
		DocumentPreviewService documentPreviewService,
		MetadataProcessingService metadataProcessingService,
		ViewerResolveService viewerResolveService,
		EditorProcessingService editorProcessingService,
		ExecutorService processingExecutor
	) {
		this.uploadStorageService = uploadStorageService;
		this.artifactStorageService = artifactStorageService;
		this.mediaPreviewService = mediaPreviewService;
		this.mediaConversionService = mediaConversionService;
		this.imageProcessingService = imageProcessingService;
		this.compressionService = compressionService;
		this.pdfToolkitService = pdfToolkitService;
		this.officeConversionService = officeConversionService;
		this.documentPreviewService = documentPreviewService;
		this.metadataProcessingService = metadataProcessingService;
		this.viewerResolveService = viewerResolveService;
		this.editorProcessingService = editorProcessingService;
		this.processingExecutor = processingExecutor;
	}

	public StoredProcessingJob enqueue(UUID uploadId, ProcessingJobType jobType, Map<String, Object> parameters) {
		var upload = this.uploadStorageService.getRequiredUpload(uploadId);
		var normalizedParameters = parameters == null ? Map.<String, Object>of() : Map.copyOf(parameters);
		ensureJobTypeSupported(upload, jobType, normalizedParameters);

		var job = StoredProcessingJob.queued(UUID.randomUUID(), upload.id(), jobType, normalizedParameters, Instant.now());
		this.jobs.put(job.id(), job);

		// Даже foundation-срез сразу запускаем через async executor, чтобы следующие
		// фазы с ffmpeg/document/imaging не ломали уже заведённый job lifecycle contract.
		this.submittedJobs.put(job.id(), this.processingExecutor.submit(() -> process(job.id())));
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

	public StoredProcessingJob cancel(UUID jobId) {
		var job = getRequiredJob(jobId);
		if (job.isTerminal()) {
			return job;
		}

		updateJob(jobId, currentJob -> currentJob.cancel(Instant.now(), "Job отменён пользователем."));
		Optional.ofNullable(this.submittedJobs.remove(jobId)).ifPresent(future -> future.cancel(true));
		return getRequiredJob(jobId);
	}

	private void process(UUID jobId) {
		try {
			if (isCancellationRequested(jobId)) {
				return;
			}

			updateJob(jobId, job -> job.status() == ProcessingJobStatus.CANCELLED ? job : job.start(Instant.now(), startMessage(job.type())));
			throwIfCancellationRequested(jobId);

			var job = getRequiredJob(jobId);
			var upload = this.uploadStorageService.getRequiredUpload(job.uploadId());

			var result = switch (job.type()) {
				case UPLOAD_INTAKE_ANALYSIS -> processUploadIntakeAnalysis(job.id(), upload);
				case MEDIA_PREVIEW -> processMediaPreview(job.id(), upload);
				case MEDIA_CONVERT -> processMediaConvert(job.id(), upload, parseMediaJobRequest(job.parameters()));
				case IMAGE_CONVERT -> processImageConvert(job.id(), upload, parseImageJobRequest(job.parameters()));
				case FILE_COMPRESS -> processFileCompress(job.id(), upload, parseCompressionJobRequest(job.parameters()));
				case PDF_TOOLKIT -> processPdfToolkit(job.id(), upload, parsePdfToolkitJobRequest(job.parameters()));
				case OFFICE_CONVERT -> processOfficeConvert(job.id(), upload, parseOfficeJobRequest(job.parameters()));
				case DOCUMENT_PREVIEW -> processDocumentPreview(job.id(), upload);
				case METADATA_EXPORT -> processMetadataExport(job.id(), upload, parseMetadataJobRequest(job.parameters()));
				case VIEWER_RESOLVE -> processViewerResolve(job.id(), upload);
				case EDITOR_PROCESS -> processEditor(job.id(), upload, parseEditorJobRequest(job.parameters()));
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для этого job type backend processor ещё не реализован.");
			};
			throwIfCancellationRequested(jobId);

			completeJob(jobId, result);
		}
		catch (Exception exception) {
			if (isCancellationRequested(jobId) || Thread.currentThread().isInterrupted() || exception instanceof JobCancellationException) {
				updateJob(
					jobId,
					job -> job.status() == ProcessingJobStatus.CANCELLED
						? job
						: job.cancel(Instant.now(), "Job отменён пользователем.")
				);
				return;
			}

			failJob(jobId, exception);
		}
		finally {
			this.submittedJobs.remove(jobId);
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

	private JobProcessingResult processMediaConvert(
		UUID jobId,
		StoredUpload upload,
		MediaConversionRequest request
	) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(24, "Подготавливаю backend media conversion pipeline."));
		var result = this.mediaConversionService.process(jobId, upload, request);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(86, "Media conversion artifacts собраны, сохраняю manifest и output blobs.")
		);
		return new JobProcessingResult(
			"Media conversion готов через backend %s.".formatted(result.runtimeLabel()),
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

	private JobProcessingResult processFileCompress(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request
	) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(18, "Готовлю compression orchestration поверх image/media pipelines."));
		var result = this.compressionService.process(
			jobId,
			upload,
			request,
			(progressPercent, message) -> updateJob(jobId, currentJob -> currentJob.updateProgress(progressPercent, message))
		);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(88, "Compression artifacts собраны, сохраняю manifest и финальный delivery result.")
		);
		return new JobProcessingResult(
			"Compression готов через backend %s.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
	}

	private JobProcessingResult processPdfToolkit(
		UUID jobId,
		StoredUpload upload,
		PdfToolkitRequest request
	) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(18, "Подготавливаю page-aware PDF toolkit orchestration."));
		var result = this.pdfToolkitService.process(
			jobId,
			upload,
			request,
			(progressPercent, message) -> updateJob(jobId, currentJob -> currentJob.updateProgress(progressPercent, message))
		);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(88, "PDF toolkit artifacts собраны, сохраняю manifest, preview и итоговый result.")
		);
		return new JobProcessingResult(
			"PDF toolkit job готов через backend %s.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
	}

	private JobProcessingResult processOfficeConvert(
		UUID jobId,
		StoredUpload upload,
		OfficeConversionRequest request
	) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(24, "Подготавливаю backend office/pdf conversion pipeline."));
		var result = this.officeConversionService.process(jobId, upload, request);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(86, "Office conversion artifacts собраны, сохраняю manifest и output blobs.")
		);
		return new JobProcessingResult(
			"Office conversion готов через backend %s.".formatted(result.runtimeLabel()),
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

	private JobProcessingResult processMetadataExport(
		UUID jobId,
		StoredUpload upload,
		MetadataProcessingRequest request
	) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(25, "Подготавливаю backend metadata inspect/export pipeline."));
		var result = this.metadataProcessingService.process(jobId, upload, request);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(85, "Metadata manifest и export artifacts собраны, сохраняю результат.")
		);
		return new JobProcessingResult(
			"Metadata processing готов через backend %s.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
	}

	private JobProcessingResult processViewerResolve(UUID jobId, StoredUpload upload) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(20, "Собираю unified viewer payload поверх backend processing services."));
		var result = this.viewerResolveService.process(jobId, upload);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(88, "Viewer payload собран, сохраняю unified manifest и связанные artifacts.")
		);
		return new JobProcessingResult(
			"Viewer resolve готов через backend %s.".formatted(result.runtimeLabel()),
			result.artifacts()
		);
	}

	private JobProcessingResult processEditor(UUID jobId, StoredUpload upload, EditorRequest request) {
		updateJob(jobId, currentJob -> currentJob.updateProgress(22, "Подготавливаю backend editor diagnostics и export pipeline."));
		var result = this.editorProcessingService.process(jobId, upload, request);
		updateJob(
			jobId,
			currentJob -> currentJob.updateProgress(88, "Editor manifest и export artifacts собраны, сохраняю результат.")
		);
		return new JobProcessingResult(
			"Editor processing готов через backend %s.".formatted(result.runtimeLabel()),
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

	private void ensureJobTypeSupported(StoredUpload upload, ProcessingJobType jobType, Map<String, Object> parameters) {
		if (
			jobType != ProcessingJobType.UPLOAD_INTAKE_ANALYSIS &&
			jobType != ProcessingJobType.MEDIA_PREVIEW &&
			jobType != ProcessingJobType.MEDIA_CONVERT &&
			jobType != ProcessingJobType.IMAGE_CONVERT &&
			jobType != ProcessingJobType.FILE_COMPRESS &&
			jobType != ProcessingJobType.PDF_TOOLKIT &&
			jobType != ProcessingJobType.OFFICE_CONVERT &&
			jobType != ProcessingJobType.DOCUMENT_PREVIEW &&
			jobType != ProcessingJobType.METADATA_EXPORT &&
			jobType != ProcessingJobType.VIEWER_RESOLVE &&
			jobType != ProcessingJobType.EDITOR_PROCESS
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

		if (jobType == ProcessingJobType.MEDIA_CONVERT) {
			parseMediaJobRequest(parameters);
			if (!this.mediaConversionService.isAvailable()) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"MEDIA_CONVERT job требует доступных ffmpeg/ffprobe binaries в backend окружении."
				);
			}
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

		if (jobType == ProcessingJobType.FILE_COMPRESS) {
			parseCompressionJobRequest(parameters);
			if (!this.compressionService.isAvailableFor(upload)) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"FILE_COMPRESS job недоступен для этого upload в текущем backend окружении."
				);
			}
		}

		if (jobType == ProcessingJobType.PDF_TOOLKIT) {
			parsePdfToolkitJobRequest(parameters);
			if (!this.pdfToolkitService.isAvailableFor(upload)) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"PDF_TOOLKIT job недоступен для этого upload в текущем backend окружении."
				);
			}
		}

		if (jobType == ProcessingJobType.OFFICE_CONVERT) {
			parseOfficeJobRequest(parameters);
			if (!this.officeConversionService.isAvailable()) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"OFFICE_CONVERT job требует доступного backend office/document conversion service."
				);
			}
		}

		if (jobType == ProcessingJobType.DOCUMENT_PREVIEW && !this.documentPreviewService.isAvailable()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"DOCUMENT_PREVIEW job требует доступного backend document intelligence service."
			);
		}

		if (jobType == ProcessingJobType.METADATA_EXPORT) {
			parseMetadataJobRequest(parameters);
			if (!this.metadataProcessingService.isAvailable()) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"METADATA_EXPORT job требует доступного backend metadata service."
				);
			}
		}

		if (jobType == ProcessingJobType.VIEWER_RESOLVE && !this.viewerResolveService.isAvailableFor(upload)) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"VIEWER_RESOLVE job недоступен для этого upload в текущем backend окружении."
			);
		}

		if (jobType == ProcessingJobType.EDITOR_PROCESS) {
			var request = parseEditorJobRequest(parameters);
			this.editorProcessingService.ensureSupported(upload, request);
			if (!this.editorProcessingService.isAvailable()) {
				throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"EDITOR_PROCESS job требует доступного backend editor processing service."
				);
			}
		}
	}

	private void updateJob(UUID jobId, UnaryOperator<StoredProcessingJob> mutation) {
		this.jobs.compute(jobId, (ignored, currentJob) -> currentJob == null ? null : mutation.apply(currentJob));
	}

	private void completeJob(UUID jobId, JobProcessingResult result) {
		this.jobs.compute(jobId, (ignored, currentJob) -> {
			if (currentJob == null || currentJob.status() == ProcessingJobStatus.CANCELLED) {
				return currentJob;
			}

			return currentJob.complete(
				Instant.now(),
				result.message(),
				result.artifacts()
			);
		});
	}

	private void failJob(UUID jobId, Exception exception) {
		this.jobs.compute(jobId, (ignored, currentJob) -> {
			if (currentJob == null || currentJob.status() == ProcessingJobStatus.CANCELLED) {
				return currentJob;
			}

			return currentJob.fail(Instant.now(), resolveErrorMessage(exception));
		});
	}

	private void throwIfCancellationRequested(UUID jobId) {
		if (Thread.currentThread().isInterrupted() || isCancellationRequested(jobId)) {
			throw new JobCancellationException();
		}
	}

	private boolean isCancellationRequested(UUID jobId) {
		return findJob(jobId)
			.map(job -> job.status() == ProcessingJobStatus.CANCELLED)
			.orElse(false);
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
			case MEDIA_CONVERT -> "Запускаю backend media conversion pipeline через ffprobe/ffmpeg.";
			case IMAGE_CONVERT -> "Запускаю backend imaging pipeline через convert/ffmpeg/potrace.";
			case FILE_COMPRESS -> "Запускаю backend compression orchestration поверх image/media processing services.";
			case PDF_TOOLKIT -> "Запускаю backend PDF toolkit orchestration для page-aware document flows.";
			case OFFICE_CONVERT -> "Запускаю backend office/pdf conversion pipeline.";
			case DOCUMENT_PREVIEW -> "Запускаю backend document intelligence pipeline.";
			case METADATA_EXPORT -> "Запускаю backend metadata inspect/export pipeline.";
			case VIEWER_RESOLVE -> "Запускаю backend viewer resolve pipeline поверх processing services.";
			case EDITOR_PROCESS -> "Запускаю backend editor diagnostics/export pipeline.";
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

	private MediaConversionRequest parseMediaJobRequest(Map<String, Object> parameters) {
		var targetExtension = readRequiredString(parameters, "targetExtension");

		return new MediaConversionRequest(
			targetExtension.toLowerCase(),
			readOptionalString(parameters, "videoCodec"),
			readOptionalString(parameters, "audioCodec"),
			readOptionalInteger(parameters, "maxWidth"),
			readOptionalInteger(parameters, "maxHeight"),
			readOptionalInteger(parameters, "targetFps"),
			readOptionalInteger(parameters, "videoBitrateKbps"),
			readOptionalInteger(parameters, "audioBitrateKbps"),
			readOptionalString(parameters, "presetLabel")
		);
	}

	private CompressionRequest parseCompressionJobRequest(Map<String, Object> parameters) {
		var rawMode = readRequiredString(parameters, "mode").trim().toLowerCase();
		var mode = switch (rawMode) {
			case "maximum", "max-reduction", "max_reduction", "maxreduction" -> CompressionRequest.Mode.MAX_REDUCTION;
			case "target-size", "target_size", "targetsize" -> CompressionRequest.Mode.TARGET_SIZE;
			case "custom" -> CompressionRequest.Mode.CUSTOM;
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"FILE_COMPRESS принимает mode только maximum, target-size или custom."
			);
		};

		var targetSizeBytes = readOptionalLong(parameters, "targetSizeBytes");
		if (mode == CompressionRequest.Mode.TARGET_SIZE && (targetSizeBytes == null || targetSizeBytes <= 0L)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"FILE_COMPRESS target-size mode требует положительный targetSizeBytes."
			);
		}

		return new CompressionRequest(
			mode,
			targetSizeBytes,
			readOptionalString(parameters, "targetExtension"),
			readOptionalInteger(parameters, "maxWidth"),
			readOptionalInteger(parameters, "maxHeight"),
			readOptionalDouble(parameters, "quality"),
			readOptionalString(parameters, "backgroundColor"),
			readOptionalInteger(parameters, "targetFps"),
			readOptionalInteger(parameters, "videoBitrateKbps"),
			readOptionalInteger(parameters, "audioBitrateKbps"),
			readOptionalString(parameters, "presetLabel")
		);
	}

	private OfficeConversionRequest parseOfficeJobRequest(Map<String, Object> parameters) {
		var targetExtension = readRequiredString(parameters, "targetExtension");

		return new OfficeConversionRequest(
			targetExtension.toLowerCase(),
			readOptionalInteger(parameters, "maxWidth"),
			readOptionalInteger(parameters, "maxHeight"),
			readOptionalDouble(parameters, "quality"),
			readOptionalString(parameters, "backgroundColor"),
			readOptionalString(parameters, "presetLabel")
		);
	}

	private PdfToolkitRequest parsePdfToolkitJobRequest(Map<String, Object> parameters) {
		var rawOperation = readRequiredString(parameters, "operation").trim().toLowerCase();
		var operation = switch (rawOperation) {
			case "merge" -> PdfToolkitRequest.Operation.MERGE;
			case "split" -> PdfToolkitRequest.Operation.SPLIT;
			case "rotate" -> PdfToolkitRequest.Operation.ROTATE;
			case "reorder", "extract-reorder", "extract_reorder", "page-reorder", "page_reorder" -> PdfToolkitRequest.Operation.REORDER;
			case "ocr" -> PdfToolkitRequest.Operation.OCR;
			case "sign", "esign", "e-sign", "stamp" -> PdfToolkitRequest.Operation.SIGN;
			case "redact", "redaction" -> PdfToolkitRequest.Operation.REDACT;
			case "protect", "password-protect", "password_protect" -> PdfToolkitRequest.Operation.PROTECT;
			case "unlock" -> PdfToolkitRequest.Operation.UNLOCK;
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"PDF_TOOLKIT operation должна быть merge, split, rotate, reorder, ocr, sign, redact, protect или unlock."
			);
		};

		var request = new PdfToolkitRequest(
			operation,
			readOptionalUuidList(parameters, "additionalUploadIds"),
			readOptionalStringList(parameters, "splitRanges"),
			readOptionalString(parameters, "pageSelection"),
			readOptionalInteger(parameters, "rotationDegrees"),
			readOptionalIntegerList(parameters, "pageOrder"),
			readOptionalString(parameters, "currentPassword"),
			readOptionalString(parameters, "userPassword"),
			readOptionalString(parameters, "ownerPassword"),
			readOptionalBoolean(parameters, "allowPrinting"),
			readOptionalBoolean(parameters, "allowCopying"),
			readOptionalBoolean(parameters, "allowModifying"),
			readOptionalString(parameters, "signatureText"),
			readOptionalUuid(parameters, "signatureImageUploadId"),
			readOptionalString(parameters, "signaturePlacement"),
			readOptionalBoolean(parameters, "includeSignatureDate"),
			readOptionalStringList(parameters, "redactTerms"),
			readOptionalString(parameters, "ocrLanguage")
		);

		if (operation == PdfToolkitRequest.Operation.MERGE && (request.additionalUploadIds() == null || request.additionalUploadIds().isEmpty())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF merge требует additionalUploadIds.");
		}
		if (operation == PdfToolkitRequest.Operation.ROTATE && request.rotationDegrees() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF rotate требует rotationDegrees.");
		}
		if (operation == PdfToolkitRequest.Operation.REORDER && (request.pageOrder() == null || request.pageOrder().isEmpty())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page reorder требует pageOrder.");
		}
		if (operation == PdfToolkitRequest.Operation.SIGN &&
			((request.signatureText() == null || request.signatureText().isBlank()) && request.signatureImageUploadId() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-sign требует signatureText или signatureImageUploadId.");
		}
		if (operation == PdfToolkitRequest.Operation.REDACT && (request.redactTerms() == null || request.redactTerms().isEmpty())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Redaction требует redactTerms.");
		}
		if (operation == PdfToolkitRequest.Operation.PROTECT && (request.ownerPassword() == null || request.ownerPassword().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Protect operation требует ownerPassword.");
		}
		if (operation == PdfToolkitRequest.Operation.UNLOCK && (request.currentPassword() == null || request.currentPassword().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unlock operation требует currentPassword.");
		}

		return request;
	}

	private MetadataProcessingRequest parseMetadataJobRequest(Map<String, Object> parameters) {
		var operation = readRequiredString(parameters, "operation");

		return switch (operation) {
			case "inspect-image" -> new MetadataProcessingRequest(
				MetadataProcessingRequest.Operation.INSPECT_IMAGE,
				null
			);
			case "inspect-audio" -> new MetadataProcessingRequest(
				MetadataProcessingRequest.Operation.INSPECT_AUDIO,
				null
			);
			case "export-image" -> new MetadataProcessingRequest(
				MetadataProcessingRequest.Operation.EXPORT_IMAGE,
				parseEditableMetadata(parameters.get("metadata"))
			);
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"METADATA_EXPORT принимает operation только inspect-image, inspect-audio или export-image."
			);
		};
	}

	private EditorRequest parseEditorJobRequest(Map<String, Object> parameters) {
		return new EditorRequest(readOptionalString(parameters, "formatId"));
	}

	private MetadataPayloads.EditableMetadata parseEditableMetadata(Object rawMetadata) {
		if (rawMetadata == null) {
			return null;
		}
		if (!(rawMetadata instanceof Map<?, ?> rawMap)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр metadata должен быть object.");
		}

		return new MetadataPayloads.EditableMetadata(
			readStringFromMap(rawMap, "description"),
			readStringFromMap(rawMap, "artist"),
			readStringFromMap(rawMap, "copyright"),
			readStringFromMap(rawMap, "capturedAt")
		);
	}

	private String readStringFromMap(Map<?, ?> rawMap, String key) {
		var value = rawMap.get(key);
		return value == null ? "" : String.valueOf(value);
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

	private Long readOptionalLong(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		try {
			return Long.valueOf(String.valueOf(value));
		}
		catch (NumberFormatException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть long.".formatted(key), exception);
		}
	}

	private Boolean readOptionalBoolean(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean booleanValue) {
			return booleanValue;
		}
		return switch (String.valueOf(value).trim().toLowerCase()) {
			case "true", "1", "yes" -> true;
			case "false", "0", "no" -> false;
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть boolean.".formatted(key));
		};
	}

	private UUID readOptionalUuid(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(String.valueOf(value));
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть UUID.".formatted(key), exception);
		}
	}

	private List<UUID> readOptionalUuidList(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (!(value instanceof List<?> values)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть массивом UUID.".formatted(key));
		}

		var result = new java.util.ArrayList<UUID>(values.size());
		for (Object entry : values) {
			try {
				result.add(UUID.fromString(String.valueOf(entry)));
			}
			catch (IllegalArgumentException exception) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s содержит невалидный UUID.".formatted(key), exception);
			}
		}
		return List.copyOf(result);
	}

	private List<String> readOptionalStringList(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (!(value instanceof List<?> values)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть массивом строк.".formatted(key));
		}
		var result = new java.util.ArrayList<String>(values.size());
		for (Object entry : values) {
			result.add(String.valueOf(entry));
		}
		return List.copyOf(result);
	}

	private List<Integer> readOptionalIntegerList(Map<String, Object> parameters, String key) {
		var value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (!(value instanceof List<?> values)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s должен быть массивом integer.".formatted(key));
		}
		var result = new java.util.ArrayList<Integer>(values.size());
		for (Object entry : values) {
			if (entry instanceof Number number) {
				result.add(number.intValue());
				continue;
			}
			try {
				result.add(Integer.valueOf(String.valueOf(entry)));
			}
			catch (NumberFormatException exception) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр %s содержит невалидный integer.".formatted(key), exception);
			}
		}
		return List.copyOf(result);
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

	private static final class JobCancellationException extends RuntimeException {
	}

}
