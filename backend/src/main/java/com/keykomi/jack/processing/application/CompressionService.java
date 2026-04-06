package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.domain.CompressionRequest;
import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.ImageProcessingRequest;
import com.keykomi.jack.processing.domain.MediaConversionRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompressionService {

	private static final Set<String> IMAGE_SOURCE_EXTENSIONS = Set.of(
		"jpg",
		"jpeg",
		"png",
		"webp",
		"bmp",
		"svg",
		"psd",
		"ai",
		"eps",
		"ps",
		"heic",
		"heif",
		"tiff",
		"tif",
		"raw",
		"dng",
		"cr2",
		"cr3",
		"nef",
		"arw",
		"raf",
		"rw2",
		"orf",
		"pef",
		"srw"
	);
	private static final Set<String> IMAGE_TARGET_EXTENSIONS = Set.of("jpg", "png", "webp", "avif", "tiff");
	private static final Set<String> VIDEO_SOURCE_EXTENSIONS = Set.of("mp4", "mov", "mkv", "avi", "webm");
	private static final Set<String> VIDEO_TARGET_EXTENSIONS = Set.of("mp4", "webm");
	private static final Set<String> AUDIO_SOURCE_EXTENSIONS = Set.of("mp3", "wav", "aac", "m4a", "flac");
	private static final Set<String> AUDIO_TARGET_EXTENSIONS = Set.of("mp3", "wav", "aac", "m4a", "flac");

	private final ImageProcessingService imageProcessingService;
	private final MediaConversionService mediaConversionService;
	private final ArtifactStorageService artifactStorageService;
	private final ObjectMapper objectMapper;

	public CompressionService(
		ImageProcessingService imageProcessingService,
		MediaConversionService mediaConversionService,
		ArtifactStorageService artifactStorageService,
		ObjectMapper objectMapper
	) {
		this.imageProcessingService = imageProcessingService;
		this.mediaConversionService = mediaConversionService;
		this.artifactStorageService = artifactStorageService;
		this.objectMapper = objectMapper;
	}

	public boolean isAvailable() {
		return this.imageProcessingService.isAvailable() || this.mediaConversionService.isAvailable();
	}

	public boolean isAvailableFor(StoredUpload upload) {
		var extension = normalizeExtension(upload.extension());
		return switch (ProcessingFileFamilyResolver.detectFamily(upload)) {
			case "image" -> this.imageProcessingService.isAvailable() && IMAGE_SOURCE_EXTENSIONS.contains(extension);
			case "media" -> this.mediaConversionService.isAvailable() && VIDEO_SOURCE_EXTENSIONS.contains(extension);
			case "audio" -> this.mediaConversionService.isAvailable() && AUDIO_SOURCE_EXTENSIONS.contains(extension);
			default -> false;
		};
	}

	public CompressionResult process(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request,
		CompressionProgressReporter progressReporter
	) {
		var family = ProcessingFileFamilyResolver.detectFamily(upload);
		return switch (family) {
			case "image" -> processImage(jobId, upload, request, progressReporter);
			case "media" -> processVideo(jobId, upload, request, progressReporter);
			case "audio" -> processAudio(jobId, upload, request, progressReporter);
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Compression route пока поддерживает только image, video и audio uploads."
			);
		};
	}

	private CompressionResult processImage(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request,
		CompressionProgressReporter progressReporter
	) {
		if (!this.imageProcessingService.isAvailable()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Image compression требует доступного backend IMAGE_CONVERT runtime."
			);
		}

		var sourceExtension = normalizeExtension(upload.extension());
		if (!IMAGE_SOURCE_EXTENSIONS.contains(sourceExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Compression route пока не поддерживает image source %s.".formatted(sourceExtension)
			);
		}

		var plans = buildImagePlans(upload, request);
		return runPlans(jobId, upload, request, "image", plans, progressReporter, this::runImageCandidate);
	}

	private CompressionResult processVideo(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request,
		CompressionProgressReporter progressReporter
	) {
		if (!this.mediaConversionService.isAvailable()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Video compression требует доступного backend MEDIA_CONVERT runtime."
			);
		}

		var sourceExtension = normalizeExtension(upload.extension());
		if (!VIDEO_SOURCE_EXTENSIONS.contains(sourceExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Compression route пока не поддерживает video source %s.".formatted(sourceExtension)
			);
		}

		var plans = buildVideoPlans(upload, request);
		return runPlans(jobId, upload, request, "media", plans, progressReporter, this::runMediaCandidate);
	}

	private CompressionResult processAudio(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request,
		CompressionProgressReporter progressReporter
	) {
		if (!this.mediaConversionService.isAvailable()) {
			throw new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Audio compression требует доступного backend MEDIA_CONVERT runtime."
			);
		}

		var sourceExtension = normalizeExtension(upload.extension());
		if (!AUDIO_SOURCE_EXTENSIONS.contains(sourceExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Compression route пока не поддерживает audio source %s.".formatted(sourceExtension)
			);
		}

		var plans = buildAudioPlans(upload, request);
		return runPlans(jobId, upload, request, "audio", plans, progressReporter, this::runMediaCandidate);
	}

	private CompressionResult runPlans(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request,
		String family,
		List<CompressionAttemptPlan> plans,
		CompressionProgressReporter progressReporter,
		CandidateRunner candidateRunner
	) {
		if (plans.isEmpty()) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Compression route не смог собрать ни одного валидного compression candidate."
			);
		}

		var candidates = new ArrayList<CompressionCandidate>();
		try {
			for (int index = 0; index < plans.size(); index += 1) {
				var plan = plans.get(index);
				// Важный компромисс: compression не дублирует encode-логику, а гоняет
				// временные candidates через уже существующие image/media services.
				// Так size-first orchestration остаётся отдельным продуктовым route,
				// но сами file mutations продолжают жить в одном backend source of truth.
				var progress = 24 + Math.round(((index + 1) * 52f) / plans.size());
				progressReporter.report(
					progress,
					"Пробую вариант %s из %s: %s.".formatted(index + 1, plans.size(), plan.label())
				);
				var candidate = candidateRunner.run(upload, request, plan);
				candidates.add(candidate);
				if (request.mode() == CompressionRequest.Mode.TARGET_SIZE && candidate.attempt().targetMet()) {
					break;
				}
			}

			var selectedCandidate = selectCandidate(candidates, request.mode());
			progressReporter.report(84, "Лучший вариант выбран, собираю итоговый файл и сводку.");
			return finalizeCompression(jobId, upload, request, family, selectedCandidate, candidates);
		}
		finally {
			for (CompressionCandidate candidate : candidates) {
				deleteRecursively(candidate.candidateDirectory());
			}
		}
	}

	private CompressionCandidate runImageCandidate(
		StoredUpload upload,
		CompressionRequest request,
		CompressionAttemptPlan plan
	) {
		var candidateJobId = UUID.randomUUID();
		var result = this.imageProcessingService.process(
			candidateJobId,
			upload,
			new ImageProcessingRequest(
				"convert",
				plan.targetExtension(),
				plan.maxWidth(),
				plan.maxHeight(),
				plan.quality(),
				plan.backgroundColor(),
				plan.presetLabel()
			)
		);
		var manifestArtifact = requireArtifact(result.artifacts(), "image-convert-manifest");
		var resultArtifact = requireArtifact(result.artifacts(), "image-convert-binary");
		var previewArtifact = requireArtifact(result.artifacts(), "image-convert-preview");
		var manifest = readJsonArtifact(manifestArtifact.storagePath(), ImageProcessingService.ImageProcessingManifest.class);
		var targetMet = request.targetSizeBytes() != null && resultArtifact.sizeBytes() <= request.targetSizeBytes();

		return new CompressionCandidate(
			candidateJobId,
			manifestArtifact.storagePath().getParent(),
			resultArtifact,
			previewArtifact,
			manifest.outputExtension(),
			manifest.resultMediaType(),
			manifest.previewMediaType(),
			"image",
			manifest.sourceAdapterLabel(),
			manifest.targetAdapterLabel(),
			manifest.runtimeLabel(),
			buildImageSourceFacts(upload, manifest),
			buildImageResultFacts(manifest),
			deduplicateWarnings(manifest.warnings()),
			plan.toAttempt(resultArtifact.sizeBytes(), targetMet, manifest.runtimeLabel())
		);
	}

	private CompressionCandidate runMediaCandidate(
		StoredUpload upload,
		CompressionRequest request,
		CompressionAttemptPlan plan
	) {
		var candidateJobId = UUID.randomUUID();
		var result = this.mediaConversionService.process(
			candidateJobId,
			upload,
			new MediaConversionRequest(
				plan.targetExtension(),
				plan.videoCodec(),
				plan.audioCodec(),
				plan.maxWidth(),
				plan.maxHeight(),
				plan.targetFps(),
				plan.videoBitrateKbps(),
				plan.audioBitrateKbps(),
				plan.presetLabel()
			)
		);
		var manifestArtifact = requireArtifact(result.artifacts(), "media-convert-manifest");
		var resultArtifact = requireArtifact(result.artifacts(), "media-convert-binary");
		var previewArtifact = requireArtifact(result.artifacts(), "media-convert-preview");
		var manifest = readJsonArtifact(manifestArtifact.storagePath(), MediaConversionService.MediaConvertManifest.class);
		var targetMet = request.targetSizeBytes() != null && resultArtifact.sizeBytes() <= request.targetSizeBytes();

		return new CompressionCandidate(
			candidateJobId,
			manifestArtifact.storagePath().getParent(),
			resultArtifact,
			previewArtifact,
			manifest.targetExtension(),
			manifest.resultMediaType(),
			manifest.previewMediaType(),
			manifest.previewKind(),
			manifest.sourceAdapterLabel(),
			manifest.targetAdapterLabel(),
			manifest.runtimeLabel(),
			manifest.sourceFacts(),
			manifest.resultFacts(),
			deduplicateWarnings(manifest.warnings()),
			plan.toAttempt(resultArtifact.sizeBytes(), targetMet, manifest.runtimeLabel())
		);
	}

	private CompressionCandidate selectCandidate(
		List<CompressionCandidate> candidates,
		CompressionRequest.Mode mode
	) {
		if (candidates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression candidate list оказалась пустой.");
		}

		if (mode == CompressionRequest.Mode.CUSTOM) {
			return candidates.get(0);
		}

		if (mode == CompressionRequest.Mode.TARGET_SIZE) {
			// Для target-size режима первый candidate, который уложился в лимит,
			// важнее абсолютного минимума: продуктовая цель здесь budget fit, а не рекорд compression ratio.
			for (CompressionCandidate candidate : candidates) {
				if (candidate.attempt().targetMet()) {
					return candidate;
				}
			}
		}

		return candidates.stream()
			.min((left, right) -> Long.compare(left.resultArtifact().sizeBytes(), right.resultArtifact().sizeBytes()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выбрать compression candidate."));
	}

	private CompressionResult finalizeCompression(
		UUID jobId,
		StoredUpload upload,
		CompressionRequest request,
		String family,
		CompressionCandidate selectedCandidate,
		List<CompressionCandidate> candidates
	) {
		List<String> warnings = new ArrayList<>(selectedCandidate.warnings());
		if (request.mode() == CompressionRequest.Mode.TARGET_SIZE && !selectedCandidate.attempt().targetMet()) {
			warnings.add(
				"Не удалось уложиться в заданный размер. Jack сохранил самый компактный найденный вариант."
			);
		}
		if (selectedCandidate.resultArtifact().sizeBytes() >= upload.sizeBytes()) {
			warnings.add(
				"Итоговый файл не оказался меньше исходного. Jack сохранил лучший найденный вариант без потери результата."
			);
		}
		warnings = deduplicateWarnings(warnings);

		var manifest = new CompressionManifest(
			upload.id(),
			upload.originalFileName(),
			normalizeExtension(upload.extension()),
			family,
			request.mode().name(),
			upload.sizeBytes(),
			request.targetSizeBytes(),
			selectedCandidate.attempt().targetMet(),
			selectedCandidate.resultArtifact().sizeBytes(),
			selectedCandidate.resultArtifact().fileName(),
			selectedCandidate.previewArtifact().fileName(),
			selectedCandidate.targetExtension(),
			selectedCandidate.resultMediaType(),
			selectedCandidate.previewMediaType(),
			selectedCandidate.previewKind(),
			selectedCandidate.sourceAdapterLabel(),
			selectedCandidate.targetAdapterLabel(),
			selectedCandidate.runtimeLabel(),
			selectedCandidate.sourceFacts(),
			selectedCandidate.resultFacts(),
			buildCompressionFacts(upload.sizeBytes(), selectedCandidate.resultArtifact().sizeBytes(), request, selectedCandidate.attempt().targetMet()),
			candidates.stream().map(CompressionCandidate::attempt).toList(),
			warnings,
			Instant.now()
		);

		var manifestArtifact = this.artifactStorageService.storeJsonArtifact(
			jobId,
			"compression-manifest",
			"compression-manifest.json",
			manifest
		);
		var resultArtifact = this.artifactStorageService.storeFileArtifact(
			jobId,
			"compression-binary",
			selectedCandidate.resultArtifact().fileName(),
			selectedCandidate.resultMediaType(),
			selectedCandidate.resultArtifact().storagePath()
		);
		var previewArtifact = this.artifactStorageService.storeFileArtifact(
			jobId,
			"compression-preview",
			selectedCandidate.previewArtifact().fileName(),
			selectedCandidate.previewMediaType(),
			selectedCandidate.previewArtifact().storagePath()
		);

		return new CompressionResult(
			List.of(manifestArtifact, resultArtifact, previewArtifact),
			selectedCandidate.runtimeLabel(),
			warnings
		);
	}

	private List<CompressionAttemptPlan> buildImagePlans(StoredUpload upload, CompressionRequest request) {
		var normalizedTarget = normalizeTargetExtension(request.targetExtension());
		var sourceExtension = normalizeExtension(upload.extension());
		var targets = normalizedTarget == null
			? resolveImageTargets(sourceExtension, request.mode())
			: List.of(requireSupportedTarget(normalizedTarget, IMAGE_TARGET_EXTENSIONS, "image"));

		if (request.mode() == CompressionRequest.Mode.CUSTOM) {
			var targetExtension = normalizedTarget != null
				? normalizedTarget
				: resolveCustomImageTarget(sourceExtension);
			return List.of(
				new CompressionAttemptPlan(
					"Ручной профиль изображения",
					targetExtension,
					request.maxWidth(),
					request.maxHeight(),
					request.quality() == null ? 0.82 : request.quality(),
					request.backgroundColor(),
					null,
					null,
					null,
					null,
					null,
					request.presetLabel() == null ? "Ручной профиль изображения" : request.presetLabel()
				)
			);
		}

		if (request.mode() == CompressionRequest.Mode.MAX_REDUCTION) {
			var plans = new ArrayList<CompressionAttemptPlan>();
			for (String target : targets) {
				plans.add(imagePlan("Максимальное уменьшение · " + target.toUpperCase(Locale.ROOT), target, 1600, 1600, 0.62, request.backgroundColor(), "Максимальное уменьшение"));
			}
			plans.add(imagePlan("Компактный резервный вариант", targets.get(0), 960, 960, 0.54, request.backgroundColor(), "Максимальное уменьшение"));
			return plans;
		}

		var pressure = resolveTargetPressure(upload.sizeBytes(), request.targetSizeBytes());
		var dimensions = pressure >= 0.78d
			? List.of(
				new DimensionLimit(null, null),
				new DimensionLimit(2560, 2560),
				new DimensionLimit(1920, 1920)
			)
			: pressure >= 0.52d
				? List.of(
					new DimensionLimit(2560, 2560),
					new DimensionLimit(1920, 1920),
					new DimensionLimit(1600, 1600)
				)
				: List.of(
					new DimensionLimit(1920, 1920),
					new DimensionLimit(1600, 1600),
					new DimensionLimit(1280, 1280),
					new DimensionLimit(960, 960)
				);
		var qualities = pressure >= 0.78d
			? List.of(0.88d, 0.8d, 0.74d)
			: pressure >= 0.52d
				? List.of(0.78d, 0.7d, 0.64d)
				: List.of(0.7d, 0.62d, 0.56d, 0.5d);
		var plans = new ArrayList<CompressionAttemptPlan>();
		for (String target : targets) {
			for (int index = 0; index < Math.min(dimensions.size(), qualities.size()); index += 1) {
				var limit = dimensions.get(index);
				var quality = "png".equals(target) || "tiff".equals(target) ? null : qualities.get(index);
				plans.add(
					imagePlan(
						"%s · проход %s".formatted(target.toUpperCase(Locale.ROOT), index + 1),
						target,
						limit.maxWidth(),
						limit.maxHeight(),
						quality,
						request.backgroundColor(),
						"Лимит размера"
					)
				);
			}
		}
		return plans;
	}

	private List<CompressionAttemptPlan> buildVideoPlans(StoredUpload upload, CompressionRequest request) {
		var normalizedTarget = normalizeTargetExtension(request.targetExtension());
		var sourceExtension = normalizeExtension(upload.extension());
		var targets = normalizedTarget == null
			? resolveVideoTargets(sourceExtension, request.mode())
			: List.of(requireSupportedTarget(normalizedTarget, VIDEO_TARGET_EXTENSIONS, "video"));

		if (request.mode() == CompressionRequest.Mode.CUSTOM) {
			var targetExtension = normalizedTarget != null
				? normalizedTarget
				: resolveCustomVideoTarget(sourceExtension);
			return List.of(
				new CompressionAttemptPlan(
					"Ручной профиль видео",
					targetExtension,
					request.maxWidth(),
					request.maxHeight(),
					null,
					null,
					resolveVideoCodec(targetExtension),
					resolveAudioCodec(targetExtension),
					request.targetFps(),
					request.videoBitrateKbps() == null ? 2500 : request.videoBitrateKbps(),
					request.audioBitrateKbps() == null ? 160 : request.audioBitrateKbps(),
					request.presetLabel() == null ? "Ручной профиль видео" : request.presetLabel()
				)
			);
		}

		if (request.mode() == CompressionRequest.Mode.MAX_REDUCTION) {
			return List.of(
				videoPlan("Максимальное уменьшение", targets.get(0), 1280, 720, 24, 1800, 128, "Максимальное уменьшение"),
				videoPlan("Компактная версия", targets.get(0), 854, 480, 15, 1200, 96, "Максимальное уменьшение"),
				videoPlan("Минимальный резервный вариант", targets.get(0), 640, 360, 12, 800, 64, "Максимальное уменьшение")
			);
		}

		var pressure = resolveTargetPressure(upload.sizeBytes(), request.targetSizeBytes());
		var ladders = pressure >= 0.78d
			? List.of(
				videoPlan("Мягкий проход", targets.get(0), 1920, 1080, 30, 3500, 160, "Лимит размера"),
				videoPlan("Сбалансированный проход", targets.get(0), 1280, 720, 24, 2500, 128, "Лимит размера"),
				videoPlan("Компактный проход", targets.get(0), 854, 480, 15, 1200, 96, "Лимит размера")
			)
			: List.of(
				videoPlan("Сбалансированный проход", targets.get(0), 1280, 720, 24, 2500, 128, "Лимит размера"),
				videoPlan("Компактный проход", targets.get(0), 854, 480, 15, 1200, 96, "Лимит размера"),
				videoPlan("Жёсткий проход", targets.get(0), 640, 360, 12, 800, 64, "Лимит размера")
			);
		if (targets.size() == 1) {
			return ladders;
		}

		var plans = new ArrayList<>(ladders);
		plans.add(videoPlan("Запасной формат", targets.get(1), 854, 480, 15, 1200, 96, "Лимит размера"));
		return plans;
	}

	private List<CompressionAttemptPlan> buildAudioPlans(StoredUpload upload, CompressionRequest request) {
		var normalizedTarget = normalizeTargetExtension(request.targetExtension());
		var sourceExtension = normalizeExtension(upload.extension());
		var targets = normalizedTarget == null
			? resolveAudioTargets(sourceExtension, request.mode())
			: List.of(requireSupportedTarget(normalizedTarget, AUDIO_TARGET_EXTENSIONS, "audio"));

		if (request.mode() == CompressionRequest.Mode.CUSTOM) {
			var targetExtension = normalizedTarget != null
				? normalizedTarget
				: resolveCustomAudioTarget(sourceExtension);
			return List.of(
				new CompressionAttemptPlan(
					"Ручной профиль аудио",
					targetExtension,
					null,
					null,
					null,
					null,
					null,
					resolveAudioCodec(targetExtension),
					null,
					null,
					request.audioBitrateKbps() == null ? 160 : request.audioBitrateKbps(),
					request.presetLabel() == null ? "Ручной профиль аудио" : request.presetLabel()
				)
			);
		}

		if (request.mode() == CompressionRequest.Mode.MAX_REDUCTION) {
			return List.of(
				audioPlan("Компактный профиль", targets.get(0), 96, "Максимальное уменьшение"),
				audioPlan("Агрессивное уменьшение", targets.get(0), 64, "Максимальное уменьшение"),
				audioPlan("Минимальный резервный вариант", targets.get(Math.min(1, targets.size() - 1)), 64, "Максимальное уменьшение")
			);
		}

		var pressure = resolveTargetPressure(upload.sizeBytes(), request.targetSizeBytes());
		var bitrates = pressure >= 0.78d
			? List.of(192, 160, 128, 96)
			: List.of(160, 128, 96, 64);
		var plans = new ArrayList<CompressionAttemptPlan>();
		for (String target : targets) {
			for (Integer bitrate : bitrates) {
				plans.add(audioPlan("%s · %s kbps".formatted(target.toUpperCase(Locale.ROOT), bitrate), target, bitrate, "Лимит размера"));
			}
		}
		return plans;
	}

	private CompressionAttemptPlan imagePlan(
		String label,
		String targetExtension,
		Integer maxWidth,
		Integer maxHeight,
		Double quality,
		String backgroundColor,
		String presetLabel
	) {
		return new CompressionAttemptPlan(
			label,
			targetExtension,
			maxWidth,
			maxHeight,
			quality,
			backgroundColor,
			null,
			null,
			null,
			null,
			null,
			presetLabel
		);
	}

	private CompressionAttemptPlan videoPlan(
		String label,
		String targetExtension,
		Integer maxWidth,
		Integer maxHeight,
		Integer targetFps,
		Integer videoBitrateKbps,
		Integer audioBitrateKbps,
		String presetLabel
	) {
		return new CompressionAttemptPlan(
			label,
			targetExtension,
			maxWidth,
			maxHeight,
			null,
			null,
			resolveVideoCodec(targetExtension),
			resolveAudioCodec(targetExtension),
			targetFps,
			videoBitrateKbps,
			audioBitrateKbps,
			presetLabel
		);
	}

	private CompressionAttemptPlan audioPlan(
		String label,
		String targetExtension,
		Integer audioBitrateKbps,
		String presetLabel
	) {
		return new CompressionAttemptPlan(
			label,
			targetExtension,
			null,
			null,
			null,
			null,
			null,
			resolveAudioCodec(targetExtension),
			null,
			null,
			audioBitrateKbps,
			presetLabel
		);
	}

	private List<String> resolveImageTargets(String sourceExtension, CompressionRequest.Mode mode) {
		var normalizedSource = normalizeExtension(sourceExtension);
		if (mode == CompressionRequest.Mode.CUSTOM && IMAGE_TARGET_EXTENSIONS.contains(normalizedSource)) {
			return List.of(normalizedSource);
		}

		return switch (normalizedSource) {
			case "jpg", "jpeg" -> List.of("avif", "webp", "jpg");
			case "png" -> List.of("webp", "avif", "png", "jpg");
			case "webp" -> List.of("avif", "webp", "jpg");
			case "bmp" -> List.of("webp", "avif", "jpg", "png");
			case "svg" -> List.of("webp", "avif", "png", "jpg");
			case "tiff", "tif" -> List.of("avif", "webp", "jpg", "tiff");
			default -> List.of("avif", "webp", "jpg", "png");
		};
	}

	private String resolveCustomImageTarget(String sourceExtension) {
		var normalizedSource = normalizeExtension(sourceExtension);
		if (IMAGE_TARGET_EXTENSIONS.contains(normalizedSource)) {
			return normalizedSource;
		}
		return resolveImageTargets(normalizedSource, CompressionRequest.Mode.CUSTOM).get(0);
	}

	private List<String> resolveVideoTargets(String sourceExtension, CompressionRequest.Mode mode) {
		if (mode == CompressionRequest.Mode.CUSTOM && VIDEO_TARGET_EXTENSIONS.contains(normalizeExtension(sourceExtension))) {
			return List.of(normalizeExtension(sourceExtension));
		}
		return List.of("webm", "mp4");
	}

	private String resolveCustomVideoTarget(String sourceExtension) {
		var normalizedSource = normalizeExtension(sourceExtension);
		if (VIDEO_TARGET_EXTENSIONS.contains(normalizedSource)) {
			return normalizedSource;
		}
		return "mp4";
	}

	private List<String> resolveAudioTargets(String sourceExtension, CompressionRequest.Mode mode) {
		var normalizedSource = normalizeExtension(sourceExtension);
		if (mode == CompressionRequest.Mode.CUSTOM && AUDIO_TARGET_EXTENSIONS.contains(normalizedSource)) {
			return List.of(normalizedSource);
		}
		return List.of("m4a", "mp3", "aac");
	}

	private String resolveCustomAudioTarget(String sourceExtension) {
		var normalizedSource = normalizeExtension(sourceExtension);
		if (AUDIO_TARGET_EXTENSIONS.contains(normalizedSource)) {
			return normalizedSource;
		}
		return "m4a";
	}

	private String resolveVideoCodec(String targetExtension) {
		return "webm".equals(targetExtension) ? "vp9" : "h264";
	}

	private String resolveAudioCodec(String targetExtension) {
		return switch (targetExtension) {
			case "m4a", "aac", "mp4" -> "aac";
			case "mp3" -> "mp3";
			case "wav" -> "pcm_s16le";
			case "flac" -> "flac";
			case "webm" -> "opus";
			default -> "aac";
		};
	}

	private StoredArtifact requireArtifact(List<StoredArtifact> artifacts, String kind) {
		return artifacts.stream()
			.filter(artifact -> kind.equals(artifact.kind()))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Не удалось найти обязательный файл результата: %s.".formatted(kind)
			));
	}

	private <T> T readJsonArtifact(Path path, Class<T> payloadType) {
		try {
			return this.objectMapper.readValue(path.toFile(), payloadType);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Не удалось прочитать сводку по сжатию.",
				exception
			);
		}
	}

	private List<DocumentPreviewPayload.DocumentFact> buildImageSourceFacts(
		StoredUpload upload,
		ImageProcessingService.ImageProcessingManifest manifest
	) {
		var facts = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		facts.add(new DocumentPreviewPayload.DocumentFact("Файл", upload.originalFileName()));
		facts.add(new DocumentPreviewPayload.DocumentFact("Формат", normalizeExtension(upload.extension()).toUpperCase(Locale.ROOT)));
		if (manifest.sourceWidth() != null && manifest.sourceHeight() != null) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Размер кадра", manifest.sourceWidth() + " x " + manifest.sourceHeight()));
		}
		facts.add(new DocumentPreviewPayload.DocumentFact("Прозрачность", manifest.sourceHasTransparency() ? "Есть" : "Нет"));
		facts.add(new DocumentPreviewPayload.DocumentFact("Подготовка", manifest.sourceAdapterLabel()));
		return facts;
	}

	private List<DocumentPreviewPayload.DocumentFact> buildImageResultFacts(
		ImageProcessingService.ImageProcessingManifest manifest
	) {
		var facts = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		facts.add(new DocumentPreviewPayload.DocumentFact("Формат", manifest.outputExtension().toUpperCase(Locale.ROOT)));
		if (manifest.width() != null && manifest.height() != null) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Размер кадра", manifest.width() + " x " + manifest.height()));
		}
		facts.add(new DocumentPreviewPayload.DocumentFact("MIME-тип", manifest.resultMediaType()));
		facts.add(new DocumentPreviewPayload.DocumentFact("Подготовка", manifest.targetAdapterLabel()));
		return facts;
	}

	private List<DocumentPreviewPayload.DocumentFact> buildCompressionFacts(
		long sourceSizeBytes,
		long resultSizeBytes,
		CompressionRequest request,
		boolean targetMet
	) {
		var facts = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		facts.add(new DocumentPreviewPayload.DocumentFact("Режим", switch (request.mode()) {
			case MAX_REDUCTION -> "Максимальное уменьшение";
			case TARGET_SIZE -> "Лимит размера";
			case CUSTOM -> "Ручная настройка";
		}));
		facts.add(new DocumentPreviewPayload.DocumentFact("Исходный размер", formatBytes(sourceSizeBytes)));
		facts.add(new DocumentPreviewPayload.DocumentFact("Размер результата", formatBytes(resultSizeBytes)));
		var savedBytes = Math.max(0L, sourceSizeBytes - resultSizeBytes);
		facts.add(new DocumentPreviewPayload.DocumentFact("Экономия", formatBytes(savedBytes)));
		facts.add(new DocumentPreviewPayload.DocumentFact("Снижение", formatReduction(sourceSizeBytes, resultSizeBytes)));
		if (request.targetSizeBytes() != null) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Целевой размер", formatBytes(request.targetSizeBytes())));
			facts.add(new DocumentPreviewPayload.DocumentFact("Статус цели", targetMet ? "Достигнут" : "Лучший доступный вариант"));
		}
		return facts;
	}

	private String requireSupportedTarget(String targetExtension, Set<String> supportedTargets, String familyLabel) {
		if (!supportedTargets.contains(targetExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Сжатие не поддерживает тип результата %s для категории %s.".formatted(targetExtension, familyLabel)
			);
		}
		return targetExtension;
	}

	private String normalizeExtension(String rawExtension) {
		var normalized = rawExtension == null ? "" : rawExtension.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "jpeg" -> "jpg";
			case "tif" -> "tiff";
			case "heif" -> "heic";
			case "ps" -> "eps";
			default -> normalized;
		};
	}

	private String normalizeTargetExtension(String rawTargetExtension) {
		var normalized = normalizeExtension(rawTargetExtension);
		return normalized.isBlank() || "auto".equals(normalized) ? null : normalized;
	}

	private double resolveTargetPressure(long sourceSizeBytes, Long targetSizeBytes) {
		if (targetSizeBytes == null || targetSizeBytes <= 0L || sourceSizeBytes <= 0L) {
			return 1.0d;
		}
		return Math.max(0.12d, Math.min(1.05d, targetSizeBytes.doubleValue() / sourceSizeBytes));
	}

	private List<String> deduplicateWarnings(List<String> warnings) {
		var values = new LinkedHashSet<String>();
		for (String warning : warnings) {
			if (warning != null && !warning.isBlank()) {
				values.add(warning);
			}
		}
		return List.copyOf(values);
	}

	private String formatBytes(long value) {
		return "%s bytes".formatted(value);
	}

	private String formatReduction(long sourceSizeBytes, long resultSizeBytes) {
		if (sourceSizeBytes <= 0L) {
			return "0%";
		}
		var ratio = ((sourceSizeBytes - resultSizeBytes) / (double) sourceSizeBytes) * 100.0d;
		return String.format(Locale.ROOT, "%.1f%%", ratio);
	}

	private void deleteRecursively(Path root) {
		if (root == null || !Files.exists(root)) {
			return;
		}

		try (var paths = Files.walk(root)) {
			paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
				}
			});
		}
		catch (IOException ignored) {
		}
	}

	@FunctionalInterface
	public interface CompressionProgressReporter {
		void report(int progressPercent, String message);
	}

	@FunctionalInterface
	private interface CandidateRunner {
		CompressionCandidate run(StoredUpload upload, CompressionRequest request, CompressionAttemptPlan plan);
	}

	public record CompressionResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel,
		List<String> warnings
	) {
	}

	public record CompressionManifest(
		UUID uploadId,
		String originalFileName,
		String sourceExtension,
		String family,
		String mode,
		long sourceSizeBytes,
		Long targetSizeBytes,
		boolean targetMet,
		long resultSizeBytes,
		String resultFileName,
		String previewFileName,
		String targetExtension,
		String resultMediaType,
		String previewMediaType,
		String previewKind,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		List<DocumentPreviewPayload.DocumentFact> sourceFacts,
		List<DocumentPreviewPayload.DocumentFact> resultFacts,
		List<DocumentPreviewPayload.DocumentFact> compressionFacts,
		List<CompressionAttempt> attempts,
		List<String> warnings,
		Instant generatedAt
	) {
	}

	public record CompressionAttempt(
		String label,
		String targetExtension,
		long resultSizeBytes,
		boolean targetMet,
		Integer maxWidth,
		Integer maxHeight,
		Double quality,
		Integer targetFps,
		Integer videoBitrateKbps,
		Integer audioBitrateKbps,
		String runtimeLabel
	) {
	}

	private record CompressionAttemptPlan(
		String label,
		String targetExtension,
		Integer maxWidth,
		Integer maxHeight,
		Double quality,
		String backgroundColor,
		String videoCodec,
		String audioCodec,
		Integer targetFps,
		Integer videoBitrateKbps,
		Integer audioBitrateKbps,
		String presetLabel
	) {

		private CompressionAttempt toAttempt(long resultSizeBytes, boolean targetMet, String runtimeLabel) {
			return new CompressionAttempt(
				this.label,
				this.targetExtension,
				resultSizeBytes,
				targetMet,
				this.maxWidth,
				this.maxHeight,
				this.quality,
				this.targetFps,
				this.videoBitrateKbps,
				this.audioBitrateKbps,
				runtimeLabel
			);
		}
	}

	private record CompressionCandidate(
		UUID candidateJobId,
		Path candidateDirectory,
		StoredArtifact resultArtifact,
		StoredArtifact previewArtifact,
		String targetExtension,
		String resultMediaType,
		String previewMediaType,
		String previewKind,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		List<DocumentPreviewPayload.DocumentFact> sourceFacts,
		List<DocumentPreviewPayload.DocumentFact> resultFacts,
		List<String> warnings,
		CompressionAttempt attempt
	) {
	}

	private record DimensionLimit(Integer maxWidth, Integer maxHeight) {
	}

}
