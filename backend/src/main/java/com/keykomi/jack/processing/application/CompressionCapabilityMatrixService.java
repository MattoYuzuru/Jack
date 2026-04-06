package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CompressionCapabilityMatrixService {

	private static final List<CompressionSourceSpec> SOURCE_SPECS = List.of(
		source("jpg", List.of("jpeg"), "JPG", "image", List.of("image/jpeg"), List.of("jpg", "webp", "avif"), "jpg", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "JPEG уже можно сжимать без ухода в converter-матрицу: quality, target-size и smarter target choice собираются через dedicated compression route."),
		source("png", List.of(), "PNG", "image", List.of("image/png"), List.of("png", "webp", "avif", "jpg"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "PNG compression route умеет и lossless resize-путь, и delivery-target сценарии через WebP/AVIF."),
		source("webp", List.of(), "WebP", "image", List.of("image/webp"), List.of("webp", "avif", "jpg"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "WebP может идти либо в same-format recompress, либо в более агрессивный AVIF target."),
		source("bmp", List.of(), "BMP", "image", List.of("image/bmp"), List.of("jpg", "webp", "avif", "png"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "BMP compression route сразу ведёт в practical delivery targets, а не в browser-only downscale hacks."),
		source("svg", List.of(), "SVG", "image", List.of("image/svg+xml"), List.of("png", "webp", "avif", "jpg"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "SVG compression route сначала строит raster-intake и затем уже подбирает размер и target profile."),
		source("psd", List.of(), "PSD", "image", List.of(), List.of("jpg", "webp", "avif", "png"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "PSD compression route reuse'ит server composite rasterization и выдаёт уже delivery-friendly artifact."),
		source("ai", List.of(), "AI", "image", List.of(), List.of("jpg", "webp", "avif", "png"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "Illustration intake compress'ится как server-raster path поверх того же imaging stack."),
		source("eps", List.of("ps"), "EPS", "image", List.of(), List.of("jpg", "webp", "avif", "png"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "EPS compression идёт через тот же server illustration path и затем попадает в delivery targets."),
		source("heic", List.of("heif"), "HEIC", "image", List.of("image/heic", "image/heif"), List.of("jpg", "webp", "avif", "png"), "avif", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "HEIC compression reuse'ит backend decode и выбирает более компактный delivery target или same-size fit."),
		source("tiff", List.of("tif"), "TIFF", "image", List.of("image/tiff"), List.of("tiff", "jpg", "webp", "avif"), "webp", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "TIFF compression route умеет и same-format refresh, и delivery-oriented recompress."),
		source("raw", List.of("dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw"), "RAW", "image", List.of(), List.of("jpg", "webp", "avif", "tiff"), "jpg", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "RAW family сначала декодируется server-side, после чего compression route подбирает size-aware output."),
		source("mp4", List.of(), "MP4", "media", List.of("video/mp4"), List.of("mp4", "webm"), "mp4", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "Video compression route теперь управляет bitrate, resolution и FPS как size-first orchestration, а не как обычная target-conversion."),
		source("mov", List.of(), "MOV", "media", List.of("video/quicktime"), List.of("mp4", "webm"), "mp4", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "MOV compression reuse'ит MEDIA_CONVERT transcode и delivery profiles внутри отдельного compression workspace."),
		source("mkv", List.of(), "MKV", "media", List.of("video/x-matroska"), List.of("mp4", "webm"), "mp4", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "MKV compression route подбирает контейнер, bitrate и resolution под size budget."),
		source("avi", List.of(), "AVI", "media", List.of("video/x-msvideo"), List.of("mp4", "webm"), "mp4", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "AVI compression route закрывается поверх того же ffmpeg orchestration и preview contract."),
		source("webm", List.of(), "WebM", "media", List.of("video/webm"), List.of("webm", "mp4"), "webm", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "WebM compression route поддерживает same-format recompress и fallback в MP4 delivery target."),
		source("wav", List.of(), "WAV", "audio", List.of("audio/wav", "audio/x-wav"), List.of("m4a", "mp3", "aac", "flac", "wav"), "m4a", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "WAV compression route умеет уходить как в lossy delivery, так и в более компактный lossless FLAC."),
		source("flac", List.of(), "FLAC", "audio", List.of("audio/flac"), List.of("m4a", "mp3", "aac", "flac"), "m4a", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "FLAC compression route может сохранить lossless target или перейти в smaller delivery format."),
		source("mp3", List.of(), "MP3", "audio", List.of("audio/mpeg"), List.of("mp3", "m4a", "aac"), "mp3", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "MP3 compression route подбирает bitrate ladder или AAC/M4A target под нужный size budget."),
		source("m4a", List.of(), "M4A", "audio", List.of("audio/mp4", "audio/x-m4a"), List.of("m4a", "mp3", "aac"), "m4a", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "M4A compression route reuse'ит тот же audio delivery stack без выхода в converter matrix."),
		source("aac", List.of(), "AAC", "audio", List.of("audio/aac"), List.of("aac", "m4a", "mp3"), "m4a", List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "AAC compression route умеет и same-codec bitrate step-down, и container switch ради результата по размеру.")
	);

	private static final List<CompressionTargetSpec> TARGET_SPECS = List.of(
		target("jpg", "JPG", "image", true, false, true, false, false, 0.82, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "JPEG target держит quality slider и optional resize limit."),
		target("png", "PNG", "image", false, true, true, false, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "PNG target пригоден для same-format resize/lossless flows, когда transparency важнее абсолютного минимума."),
		target("webp", "WebP", "image", true, true, true, false, false, 0.78, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "WebP остаётся practical default delivery target для image compression."),
		target("avif", "AVIF", "image", true, true, true, false, false, 0.72, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "AVIF target даёт самый агрессивный image-size path в текущем product slice."),
		target("tiff", "TIFF", "image", false, true, true, false, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT), "TIFF target остаётся niche same-family option для archival-like image flows."),
		target("mp4", "MP4", "media", false, false, true, true, true, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "MP4 target поддерживает bitrate, resolution и FPS limits для practical delivery compression."),
		target("webm", "WebM", "media", false, false, true, true, true, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "WebM target пригоден для более агрессивного video size targeting."),
		target("m4a", "M4A", "audio", false, false, false, true, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "M4A target остаётся preferred AAC container для compact audio delivery."),
		target("mp3", "MP3", "audio", false, false, false, true, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "MP3 target сохраняет compatibility и bitrate-based compression control."),
		target("aac", "AAC", "audio", false, false, false, true, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "Raw AAC target доступен для transport-centric audio flows."),
		target("flac", "FLAC", "audio", false, false, false, true, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "FLAC target позволяет ужать PCM-class audio без ухода в lossy export."),
		target("wav", "WAV", "audio", false, false, false, true, false, null, List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.MEDIA_CONVERT), "WAV target доступен только для custom/preserve scenarios и не считается default compact output.")
	);

	private static final List<CompressionModeSpec> MODE_SPECS = List.of(
		new CompressionModeSpec("maximum", "Максимальное уменьшение", "Ищет самый компактный практический вариант среди доступных форматов и профилей.", List.of("Минимальный вес", "Автовыбор"), false, true, false),
		new CompressionModeSpec("target-size", "Лимит размера", "Подбирает варианты, пока файл не уложится в заданный лимит или не будет найден лучший доступный результат.", List.of("Лимит", "Best effort"), true, true, false),
		new CompressionModeSpec("custom", "Ручная настройка", "Даёт ручной выбор формата, качества, битрейта, разрешения и FPS без смешивания со сценарием обычной конвертации.", List.of("Вручную", "Ограничения"), false, true, true)
	);

	public CapabilityMatrixPayloads.CompressionCapabilityMatrix compressionMatrix(
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var sources = SOURCE_SPECS.stream()
			.map(spec -> toSource(spec, availabilityByJobType))
			.toList();
		var targets = TARGET_SPECS.stream()
			.map(spec -> toTarget(spec, availabilityByJobType))
			.toList();
		var modes = MODE_SPECS.stream()
			.map(spec -> new CapabilityMatrixPayloads.CompressionModeCapability(
				spec.id(),
				spec.label(),
				spec.detail(),
				spec.accents(),
				spec.requiresTargetSize(),
				spec.supportsTargetSelection(),
				spec.supportsCustomSettings()
			))
			.toList();

		return new CapabilityMatrixPayloads.CompressionCapabilityMatrix(
			buildAcceptAttribute(
				sources.stream()
					.filter(CapabilityMatrixPayloads.CompressionSourceCapability::available)
					.map(source -> new ExtensionAliases(source.extension(), source.aliases()))
					.toList()
			),
			sources,
			targets,
			modes
		);
	}

	private CapabilityMatrixPayloads.CompressionSourceCapability toSource(
		CompressionSourceSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var available = allRequiredJobsAvailable(spec.requiredJobTypes(), availabilityByJobType);
		return new CapabilityMatrixPayloads.CompressionSourceCapability(
			spec.extension(),
			spec.aliases(),
			spec.label(),
			spec.family(),
			spec.mimeTypes(),
			spec.targetExtensions(),
			spec.defaultTargetExtension(),
			available ? "Готово к сжатию" : "Временно недоступно",
			spec.notes(),
			List.of(spec.label(), spec.family()),
			available,
			available ? null : "Для сжатия %s нужны доступные сценарии %s."
				.formatted(spec.label(), spec.requiredJobTypes().stream().map(Enum::name).collect(Collectors.joining(", "))),
			spec.requiredJobTypes()
		);
	}

	private CapabilityMatrixPayloads.CompressionTargetCapability toTarget(
		CompressionTargetSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var available = allRequiredJobsAvailable(spec.requiredJobTypes(), availabilityByJobType);
		return new CapabilityMatrixPayloads.CompressionTargetCapability(
			spec.extension(),
			spec.label(),
			spec.family(),
			spec.supportsQuality(),
			spec.supportsTransparency(),
			spec.supportsResolutionLimits(),
			spec.supportsBitrateControls(),
			spec.supportsFpsControl(),
			spec.defaultQuality(),
			available ? "Доступный результат" : "Временно недоступно",
			spec.notes(),
			List.of(spec.label(), spec.family()),
			available,
			available ? null : "Для формата %s нужны доступные сценарии %s."
				.formatted(spec.label(), spec.requiredJobTypes().stream().map(Enum::name).collect(Collectors.joining(", "))),
			spec.requiredJobTypes()
		);
	}

	private boolean allRequiredJobsAvailable(
		List<ProcessingJobType> requiredJobTypes,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		for (ProcessingJobType requiredJobType : requiredJobTypes) {
			if (!availabilityByJobType.getOrDefault(requiredJobType, false)) {
				return false;
			}
		}
		return true;
	}

	private String buildAcceptAttribute(List<ExtensionAliases> definitions) {
		return definitions.stream()
			.flatMap(definition -> definition.allExtensions().stream())
			.map(extension -> "." + extension)
			.collect(Collectors.joining(","));
	}

	private static CompressionSourceSpec source(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		List<String> targetExtensions,
		String defaultTargetExtension,
		List<ProcessingJobType> requiredJobTypes,
		String notes
	) {
		return new CompressionSourceSpec(
			extension,
			aliases,
			label,
			family,
			mimeTypes,
			targetExtensions,
			defaultTargetExtension,
			requiredJobTypes,
			notes
		);
	}

	private static CompressionTargetSpec target(
		String extension,
		String label,
		String family,
		boolean supportsQuality,
		boolean supportsTransparency,
		boolean supportsResolutionLimits,
		boolean supportsBitrateControls,
		boolean supportsFpsControl,
		Double defaultQuality,
		List<ProcessingJobType> requiredJobTypes,
		String notes
	) {
		return new CompressionTargetSpec(
			extension,
			label,
			family,
			supportsQuality,
			supportsTransparency,
			supportsResolutionLimits,
			supportsBitrateControls,
			supportsFpsControl,
			defaultQuality,
			requiredJobTypes,
			notes
		);
	}

	private record CompressionSourceSpec(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		List<String> targetExtensions,
		String defaultTargetExtension,
		List<ProcessingJobType> requiredJobTypes,
		String notes
	) {
	}

	private record CompressionTargetSpec(
		String extension,
		String label,
		String family,
		boolean supportsQuality,
		boolean supportsTransparency,
		boolean supportsResolutionLimits,
		boolean supportsBitrateControls,
		boolean supportsFpsControl,
		Double defaultQuality,
		List<ProcessingJobType> requiredJobTypes,
		String notes
	) {
	}

	private record CompressionModeSpec(
		String id,
		String label,
		String detail,
		List<String> accents,
		boolean requiresTargetSize,
		boolean supportsTargetSelection,
		boolean supportsCustomSettings
	) {
	}

	private record ExtensionAliases(String extension, List<String> aliases) {

		private List<String> allExtensions() {
			var values = new ArrayList<String>();
			values.add(this.extension);
			values.addAll(this.aliases);
			return values;
		}
	}

}
