package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CapabilityMatrixService {

	private static final List<ViewerFormatSpec> VIEWER_FORMAT_SPECS = List.of(
		viewerFormat("jpg", List.of(), "JPG", "image", List.of("image/jpeg"), "browser-native", "native-image", "Browser preview", "Нативный raster path без промежуточной обработки.", List.of()),
		viewerFormat("jpeg", List.of(), "JPEG", "image", List.of("image/jpeg"), "browser-native", "native-image", "Browser preview", "Alias того же browser-first image path.", List.of()),
		viewerFormat("png", List.of(), "PNG", "image", List.of("image/png"), "browser-native", "native-image", "Browser preview", "Lossless image preview с alpha-каналом.", List.of()),
		viewerFormat("webp", List.of(), "WebP", "image", List.of("image/webp"), "browser-native", "native-image", "Browser preview", "Современный browser-supported raster format.", List.of()),
		viewerFormat("avif", List.of(), "AVIF", "image", List.of("image/avif"), "browser-native", "native-image", "Browser preview", "Открывается напрямую в современных браузерах.", List.of()),
		viewerFormat("gif", List.of(), "GIF", "image", List.of("image/gif"), "browser-native", "native-image", "Browser preview", "Анимированный preview остаётся в исходном контейнере.", List.of()),
		viewerFormat("bmp", List.of(), "BMP", "image", List.of("image/bmp"), "browser-native", "native-image", "Browser preview", "Большой bitmap остаётся в browser-native raster path.", List.of()),
		viewerFormat("svg", List.of(), "SVG", "image", List.of("image/svg+xml"), "browser-native", "native-image", "Browser preview", "Векторный preview без промежуточной растризации.", List.of()),
		viewerFormat("ico", List.of(), "ICO", "image", List.of("image/x-icon", "image/vnd.microsoft.icon"), "browser-native", "native-image", "Browser preview", "Icon container открывается напрямую в viewport.", List.of()),
		viewerFormat("heic", List.of("heif"), "HEIC", "image", List.of("image/heic", "image/heif"), "server-assisted", "server-viewer", "Server viewer route", "HEIC preview собирается через backend VIEWER_RESOLVE поверх IMAGE_CONVERT и metadata inspect.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.IMAGE_CONVERT, ProcessingJobType.METADATA_EXPORT), "HEIC preview требует доступных VIEWER_RESOLVE, IMAGE_CONVERT и METADATA_EXPORT capabilities."),
		viewerFormat("tiff", List.of("tif"), "TIFF", "image", List.of("image/tiff"), "server-assisted", "server-viewer", "Server viewer route", "TIFF preview собирается через backend VIEWER_RESOLVE поверх IMAGE_CONVERT и metadata inspect.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.IMAGE_CONVERT, ProcessingJobType.METADATA_EXPORT), "TIFF preview требует доступных VIEWER_RESOLVE, IMAGE_CONVERT и METADATA_EXPORT capabilities."),
		viewerFormat("raw", List.of("dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw"), "RAW", "image", List.of(), "server-assisted", "server-viewer", "Server viewer route", "RAW family использует backend VIEWER_RESOLVE поверх preview extraction и metadata pipeline.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.IMAGE_CONVERT, ProcessingJobType.METADATA_EXPORT), "RAW preview требует доступных VIEWER_RESOLVE, IMAGE_CONVERT и METADATA_EXPORT capabilities."),
		viewerFormat("pdf", List.of(), "PDF", "document", List.of("application/pdf"), "server-assisted", "server-viewer", "Server viewer route", "PDF preview и searchable layer собираются через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "PDF preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("txt", List.of(), "TXT", "document", List.of("text/plain"), "server-assisted", "server-viewer", "Server viewer route", "Text extraction и search summary идут через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "TXT preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("csv", List.of(), "CSV", "document", List.of("text/csv", "application/csv"), "server-assisted", "server-viewer", "Server viewer route", "CSV preview строится через backend VIEWER_RESOLVE как bounded table payload.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "CSV preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("html", List.of("htm"), "HTML", "document", List.of("text/html"), "server-assisted", "server-viewer", "Server viewer route", "HTML проходит через backend VIEWER_RESOLVE с sanitization и outline extraction.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "HTML preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("rtf", List.of(), "RTF", "document", List.of("application/rtf", "text/rtf"), "server-assisted", "server-viewer", "Server viewer route", "Legacy rich text извлекается через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "RTF preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("doc", List.of(), "DOC", "document", List.of("application/msword"), "server-assisted", "server-viewer", "Server viewer route", "Legacy Word document разбирается через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "DOC preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("docx", List.of(), "DOCX", "document", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), "server-assisted", "server-viewer", "Server viewer route", "OOXML word payload собирается через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "DOCX preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("odt", List.of(), "ODT", "document", List.of("application/vnd.oasis.opendocument.text"), "server-assisted", "server-viewer", "Server viewer route", "OpenDocument text разбирается через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "ODT preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("xls", List.of(), "XLS", "document", List.of("application/vnd.ms-excel"), "server-assisted", "server-viewer", "Server viewer route", "Legacy spreadsheet проходит через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "XLS preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("xlsx", List.of(), "XLSX", "document", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "server-assisted", "server-viewer", "Server viewer route", "OOXML spreadsheet проходит через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "XLSX preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("pptx", List.of(), "PPTX", "document", List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"), "server-assisted", "server-viewer", "Server viewer route", "Presentation outline и text summary идут через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "PPTX preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("epub", List.of(), "EPUB", "document", List.of("application/epub+zip"), "server-assisted", "server-viewer", "Server viewer route", "EPUB content и chapters разбираются через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "EPUB preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("db", List.of(), "DB", "document", List.of("application/octet-stream"), "server-assisted", "server-viewer", "Server viewer route", "SQLite schema/sample introspection идёт через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "DB preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("sqlite", List.of(), "SQLite", "document", List.of("application/x-sqlite3"), "server-assisted", "server-viewer", "Server viewer route", "SQLite schema/sample introspection идёт через backend VIEWER_RESOLVE поверх DOCUMENT_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.DOCUMENT_PREVIEW), "SQLite preview требует доступных VIEWER_RESOLVE и DOCUMENT_PREVIEW capabilities."),
		viewerFormat("mp4", List.of(), "MP4", "media", List.of("video/mp4"), "browser-native", "native-video", "Browser video preview", "MP4 воспроизводится напрямую через HTMLVideoElement.", List.of()),
		viewerFormat("mov", List.of(), "MOV", "media", List.of("video/quicktime"), "browser-native", "native-video", "Browser video preview", "MOV использует тот же browser-native playback path.", List.of()),
		viewerFormat("webm", List.of(), "WebM", "media", List.of("video/webm"), "browser-native", "native-video", "Browser video preview", "WebM воспроизводится напрямую без server transcode.", List.of()),
		viewerFormat("avi", List.of(), "AVI", "media", List.of("video/x-msvideo"), "server-assisted", "server-viewer", "Server viewer route", "Legacy video container нормализуется через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW), "AVI preview требует доступных VIEWER_RESOLVE и MEDIA_PREVIEW capabilities."),
		viewerFormat("mkv", List.of(), "MKV", "media", List.of("video/x-matroska"), "server-assisted", "server-viewer", "Server viewer route", "Matroska container нормализуется через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW), "MKV preview требует доступных VIEWER_RESOLVE и MEDIA_PREVIEW capabilities."),
		viewerFormat("wmv", List.of(), "WMV", "media", List.of("video/x-ms-wmv"), "server-assisted", "server-viewer", "Server viewer route", "WMV normalizes через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW), "WMV preview требует доступных VIEWER_RESOLVE и MEDIA_PREVIEW capabilities."),
		viewerFormat("flv", List.of(), "FLV", "media", List.of("video/x-flv"), "server-assisted", "server-viewer", "Server viewer route", "FLV normalizes через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW), "FLV preview требует доступных VIEWER_RESOLVE и MEDIA_PREVIEW capabilities."),
		viewerFormat("mp3", List.of(), "MP3", "audio", List.of("audio/mpeg"), "browser-native", "native-audio", "Browser audio preview", "MP3 playback и waveform идут через browser-native audio path.", List.of()),
		viewerFormat("wav", List.of(), "WAV", "audio", List.of("audio/wav", "audio/x-wav"), "browser-native", "native-audio", "Browser audio preview", "WAV воспроизводится напрямую в браузере.", List.of()),
		viewerFormat("ogg", List.of(), "OGG", "audio", List.of("audio/ogg"), "browser-native", "native-audio", "Browser audio preview", "OGG остаётся в browser-native audio path.", List.of()),
		viewerFormat("opus", List.of(), "OPUS", "audio", List.of("audio/ogg; codecs=opus", "audio/opus"), "browser-native", "native-audio", "Browser audio preview", "Opus playback остаётся browser-native.", List.of()),
		viewerFormat("aac", List.of(), "AAC", "audio", List.of("audio/aac"), "server-assisted", "server-viewer", "Server viewer route", "AAC normalized через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW и metadata inspect.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW, ProcessingJobType.METADATA_EXPORT), "AAC preview требует доступных VIEWER_RESOLVE, MEDIA_PREVIEW и METADATA_EXPORT capabilities."),
		viewerFormat("flac", List.of(), "FLAC", "audio", List.of("audio/flac"), "server-assisted", "server-viewer", "Server viewer route", "FLAC normalized через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW и metadata inspect.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW, ProcessingJobType.METADATA_EXPORT), "FLAC preview требует доступных VIEWER_RESOLVE, MEDIA_PREVIEW и METADATA_EXPORT capabilities."),
		viewerFormat("aiff", List.of("aif"), "AIFF", "audio", List.of("audio/aiff", "audio/x-aiff"), "server-assisted", "server-viewer", "Server viewer route", "AIFF normalized через backend VIEWER_RESOLVE поверх MEDIA_PREVIEW и metadata inspect.", List.of(ProcessingJobType.VIEWER_RESOLVE, ProcessingJobType.MEDIA_PREVIEW, ProcessingJobType.METADATA_EXPORT), "AIFF preview требует доступных VIEWER_RESOLVE, MEDIA_PREVIEW и METADATA_EXPORT capabilities.")
	);

	private static final List<ConverterSourceSpec> CONVERTER_SOURCE_SPECS = List.of(
		sourceFormat("jpg", List.of("jpeg"), "JPG", "image", List.of("image/jpeg"), "native-raster", "Backend intake", "Даже привычный raster source теперь идёт через единый backend IMAGE_CONVERT contract.", List.of(ProcessingJobType.IMAGE_CONVERT), "JPG source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("png", List.of(), "PNG", "image", List.of("image/png"), "native-raster", "Backend intake", "Lossless raster source теперь попадает в тот же backend-first conversion pipeline.", List.of(ProcessingJobType.IMAGE_CONVERT), "PNG source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("webp", List.of(), "WebP", "image", List.of("image/webp"), "native-raster", "Backend intake", "WebP source больше не замыкает быстрый browser-only encode path.", List.of(ProcessingJobType.IMAGE_CONVERT), "WebP source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("bmp", List.of(), "BMP", "image", List.of("image/bmp"), "native-raster", "Backend intake", "Большой bitmap source теперь проходит через backend-first pipeline ради единых retries/cache/artifacts.", List.of(ProcessingJobType.IMAGE_CONVERT), "BMP source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("svg", List.of(), "SVG", "image", List.of("image/svg+xml"), "native-raster", "Backend intake", "SVG source теперь тоже проходит через backend intake, а браузер остаётся только preview/UI слоем.", List.of(ProcessingJobType.IMAGE_CONVERT), "SVG source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("psd", List.of(), "PSD", "image", List.of("image/vnd.adobe.photoshop", "application/vnd.adobe.photoshop"), "psd-raster", "Server composite", "PSD source собирается через backend IMAGE_CONVERT composite path.", List.of(ProcessingJobType.IMAGE_CONVERT), "PSD source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("ai", List.of(), "AI", "image", List.of(), "illustration-raster", "Server illustration", "Illustrator source растеризуется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "AI source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("eps", List.of("ps"), "EPS", "image", List.of(), "illustration-raster", "Server illustration", "PostScript source растеризуется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "EPS source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("heic", List.of("heif"), "HEIC", "image", List.of("image/heic", "image/heif"), "heic-raster", "Server rasterization", "HEIC source декодируется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "HEIC source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("tiff", List.of("tif"), "TIFF", "image", List.of("image/tiff"), "tiff-raster", "Server rasterization", "TIFF source декодируется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "TIFF source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("raw", List.of("dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw"), "RAW", "image", List.of(), "raw-raster", "Server rasterization", "RAW family идёт через backend preview extraction.", List.of(ProcessingJobType.IMAGE_CONVERT), "RAW source требует доступного backend IMAGE_CONVERT capability.")
	);

	private static final List<ConverterTargetSpec> CONVERTER_TARGET_SPECS = List.of(
		targetFormat("jpg", "JPG", "image", "image/jpeg", "jpeg-encoder", true, false, 0.9, "Backend encode", "Практичный совместимый raster target теперь собирается через backend-first contract.", List.of(ProcessingJobType.IMAGE_CONVERT), "JPG target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("png", "PNG", "image", "image/png", "png-encoder", false, true, null, "Backend encode", "Lossless target с transparency теперь тоже собирается через backend jobs.", List.of(ProcessingJobType.IMAGE_CONVERT), "PNG target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("webp", "WebP", "image", "image/webp", "webp-encoder", true, true, 0.9, "Backend encode", "Компактный modern raster target собирается через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "WebP target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("avif", "AVIF", "image", "image/avif", "avif-encoder", true, true, 0.78, "Backend encode", "AVIF target собирается через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "AVIF target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("svg", "SVG", "image", "image/svg+xml", "svg-vectorizer", false, true, null, "Backend trace", "SVG target собирается через backend trace path.", List.of(ProcessingJobType.IMAGE_CONVERT), "SVG target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("ico", "ICO", "image", "image/x-icon", "ico-image", false, true, null, "Backend icon pack", "ICO target собирается через backend multi-size icon path.", List.of(ProcessingJobType.IMAGE_CONVERT), "ICO target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("pdf", "PDF", "document", "application/pdf", "pdf-document", true, false, 0.92, "Backend document", "PDF target собирается через backend document raster path.", List.of(ProcessingJobType.IMAGE_CONVERT), "PDF target требует доступного backend IMAGE_CONVERT capability."),
		targetFormat("tiff", "TIFF", "image", "image/tiff", "tiff-image", false, true, null, "Backend archive encode", "TIFF target собирается через backend archive-friendly encode path.", List.of(ProcessingJobType.IMAGE_CONVERT), "TIFF target требует доступного backend IMAGE_CONVERT capability.")
	);

	private static final List<ConverterPresetSpec> CONVERTER_PRESET_SPECS = List.of(
		new ConverterPresetSpec("original", "Original", "Не меняет размерность и оставляет runtime только target-specific encode решения.", "No resize", List.of("Original size", "Safe base"), null, null, null, "#ffffff"),
		new ConverterPresetSpec("web-balanced", "Web Balanced", "Практичный пресет для веба с мягким ограничением крупных изображений.", "2560 px cap", List.of("Web", "Balanced"), 2560, 2560, 0.86, "#ffffff"),
		new ConverterPresetSpec("email-attachment", "Email Attachment", "Агрессивнее ограничивает размерность и bitrate под вложения.", "1600 px cap", List.of("Email", "Compact"), 1600, 1600, 0.78, "#fffaf0"),
		new ConverterPresetSpec("thumbnail", "Thumbnail", "Миниатюрный профиль для карточек и лёгких превью.", "512 px cap", List.of("Preview", "Small"), 512, 512, 0.72, "#f3ede3")
	);

	public CapabilityMatrixPayloads.ViewerCapabilityMatrix viewerMatrix(
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var formats = VIEWER_FORMAT_SPECS.stream()
			.map(spec -> toViewerFormat(spec, availabilityByJobType))
			.toList();

		return new CapabilityMatrixPayloads.ViewerCapabilityMatrix(
			buildAcceptAttribute(
				formats.stream()
					.filter(CapabilityMatrixPayloads.ViewerFormatCapability::available)
					.map(format -> new ExtensionAliases(format.extension(), format.aliases()))
					.toList()
			),
			formats
		);
	}

	public CapabilityMatrixPayloads.ConverterCapabilityMatrix converterMatrix(
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var sources = CONVERTER_SOURCE_SPECS.stream()
			.map(spec -> toConverterSource(spec, availabilityByJobType))
			.toList();
		var targets = CONVERTER_TARGET_SPECS.stream()
			.map(spec -> toConverterTarget(spec, availabilityByJobType))
			.toList();
		var targetByExtension = targets.stream()
			.collect(Collectors.toMap(
				CapabilityMatrixPayloads.ConverterTargetCapability::extension,
				target -> target,
				(existing, replacement) -> existing,
				LinkedHashMap::new
			));
		var sourceByExtension = sources.stream()
			.collect(Collectors.toMap(
				CapabilityMatrixPayloads.ConverterSourceCapability::extension,
				source -> source,
				(existing, replacement) -> existing,
				LinkedHashMap::new
			));
		var scenarios = buildConverterScenarioSpecs().stream()
			.map(spec -> toConverterScenario(spec, sourceByExtension, targetByExtension, availabilityByJobType))
			.toList();
		var presets = CONVERTER_PRESET_SPECS.stream()
			.map(spec -> new CapabilityMatrixPayloads.ConverterPresetCapability(
				spec.id(),
				spec.label(),
				spec.detail(),
				spec.statusLabel(),
				spec.accents(),
				spec.maxWidth(),
				spec.maxHeight(),
				spec.preferredQuality(),
				spec.defaultBackgroundColor(),
				true,
				null
			))
			.toList();

		return new CapabilityMatrixPayloads.ConverterCapabilityMatrix(
			buildAcceptAttribute(
				sources.stream()
					.filter(CapabilityMatrixPayloads.ConverterSourceCapability::available)
					.map(source -> new ExtensionAliases(source.extension(), source.aliases()))
					.toList()
			),
			sources,
			targets,
			scenarios,
			presets
		);
	}

	private CapabilityMatrixPayloads.ViewerFormatCapability toViewerFormat(
		ViewerFormatSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var available = allRequiredJobsAvailable(spec.requiredJobTypes(), availabilityByJobType);
		return new CapabilityMatrixPayloads.ViewerFormatCapability(
			spec.extension(),
			spec.aliases(),
			spec.label(),
			spec.family(),
			spec.mimeTypes(),
			spec.previewPipeline(),
			spec.previewStrategyId(),
			available ? spec.statusLabel() : "Capability unavailable",
			spec.notes(),
			buildAccents(spec.accents(), spec.previewPipeline()),
			available,
			available ? null : spec.unavailableDetail(),
			spec.requiredJobTypes()
		);
	}

	private CapabilityMatrixPayloads.ConverterSourceCapability toConverterSource(
		ConverterSourceSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var available = allRequiredJobsAvailable(spec.requiredJobTypes(), availabilityByJobType);
		return new CapabilityMatrixPayloads.ConverterSourceCapability(
			spec.extension(),
			spec.aliases(),
			spec.label(),
			spec.family(),
			spec.mimeTypes(),
			spec.sourceStrategyId(),
			available ? spec.statusLabel() : "Capability unavailable",
			spec.notes(),
			buildAccents(spec.accents(), spec.sourceStrategyId().equals("native-raster") ? "browser-native" : "server-assisted"),
			available,
			available ? null : spec.unavailableDetail(),
			spec.requiredJobTypes()
		);
	}

	private CapabilityMatrixPayloads.ConverterTargetCapability toConverterTarget(
		ConverterTargetSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var available = allRequiredJobsAvailable(spec.requiredJobTypes(), availabilityByJobType);
		return new CapabilityMatrixPayloads.ConverterTargetCapability(
			spec.extension(),
			spec.label(),
			spec.family(),
			spec.mimeType(),
			spec.targetStrategyId(),
			spec.supportsQuality(),
			spec.supportsTransparency(),
			spec.defaultQuality(),
			available ? spec.statusLabel() : "Capability unavailable",
			spec.notes(),
			buildAccents(spec.accents(), spec.requiredJobTypes().isEmpty() ? "browser-native" : "server-assisted"),
			available,
			available ? null : spec.unavailableDetail(),
			spec.requiredJobTypes()
		);
	}

	private CapabilityMatrixPayloads.ConverterScenarioCapability toConverterScenario(
		ConverterScenarioSpec spec,
		Map<String, CapabilityMatrixPayloads.ConverterSourceCapability> sourceByExtension,
		Map<String, CapabilityMatrixPayloads.ConverterTargetCapability> targetByExtension,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var source = sourceByExtension.get(spec.sourceExtension());
		var target = targetByExtension.get(spec.targetExtension());
		var requiredJobTypes = new ArrayList<ProcessingJobType>();
		if (source != null) {
			requiredJobTypes.addAll(source.requiredJobTypes());
		}
		if (target != null) {
			for (ProcessingJobType requiredJobType : target.requiredJobTypes()) {
				if (!requiredJobTypes.contains(requiredJobType)) {
					requiredJobTypes.add(requiredJobType);
				}
			}
		}
		var available = allRequiredJobsAvailable(requiredJobTypes, availabilityByJobType);

		return new CapabilityMatrixPayloads.ConverterScenarioCapability(
			buildScenarioKey(spec.sourceExtension(), spec.targetExtension()),
			spec.family(),
			spec.label(),
			spec.sourceExtension(),
			spec.targetExtension(),
			available ? ("server-assisted".equals(spec.executionMode()) ? "Server-assisted" : "Browser-native") : "Capability unavailable",
			"server-assisted".equals(spec.executionMode())
				? "Сценарий идёт через backend IMAGE_CONVERT jobs: frontend держит progress/retry/cancel/reuse UX и получает preview/result artifacts."
				: "Сценарий закрывается локально через browser-native raster pipeline без backend round-trip.",
			List.of(spec.sourceExtension().toUpperCase(), spec.targetExtension().toUpperCase()),
			spec.executionMode(),
			available,
			available ? null : spec.unavailableDetail(),
			requiredJobTypes
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

	private List<String> buildAccents(List<String> accents, String executionMode) {
		if (accents.isEmpty()) {
			return List.of(executionMode.equals("browser-native") ? "Browser" : "Server");
		}
		var values = new ArrayList<>(accents);
		values.add(executionMode.equals("browser-native") ? "Browser" : "Server");
		return values;
	}

	private static List<ConverterScenarioSpec> buildConverterScenarioSpecs() {
		return List.of(
			scenario("heic", "jpg", "HEIC decode -> JPG"),
			scenario("heic", "avif", "HEIC -> AVIF"),
			scenario("heic", "tiff", "HEIC -> TIFF"),
			scenario("png", "jpg", "PNG -> JPG"),
			scenario("png", "webp", "PNG -> WebP"),
			scenario("png", "avif", "PNG -> AVIF"),
			scenario("png", "svg", "PNG -> SVG trace"),
			scenario("png", "ico", "PNG -> ICO"),
			scenario("png", "tiff", "PNG -> TIFF"),
			scenario("jpg", "png", "JPG -> PNG"),
			scenario("jpg", "webp", "JPG -> WebP"),
			scenario("jpg", "avif", "JPG -> AVIF"),
			scenario("jpg", "tiff", "JPG -> TIFF"),
			scenario("webp", "jpg", "WebP -> JPG"),
			scenario("webp", "png", "WebP -> PNG"),
			scenario("webp", "tiff", "WebP -> TIFF"),
			scenario("bmp", "jpg", "BMP -> JPG"),
			scenario("bmp", "png", "BMP -> PNG"),
			scenario("bmp", "tiff", "BMP -> TIFF"),
			scenario("psd", "jpg", "PSD -> JPG"),
			scenario("psd", "png", "PSD -> PNG"),
			scenario("psd", "webp", "PSD -> WebP"),
			scenario("tiff", "jpg", "TIFF -> JPG"),
			scenario("tiff", "pdf", "TIFF -> PDF", "document"),
			scenario("tiff", "tiff", "TIFF -> TIFF refresh"),
			scenario("raw", "jpg", "RAW -> JPG"),
			scenario("raw", "pdf", "RAW -> PDF", "document"),
			scenario("raw", "tiff", "RAW -> TIFF"),
			scenario("jpg", "pdf", "JPG -> PDF", "document"),
			scenario("png", "pdf", "PNG -> PDF", "document"),
			scenario("webp", "pdf", "WebP -> PDF", "document"),
			scenario("bmp", "pdf", "BMP -> PDF", "document"),
			scenario("heic", "pdf", "HEIC -> PDF", "document"),
			scenario("svg", "png", "SVG -> PNG"),
			scenario("svg", "ico", "SVG -> ICO"),
			scenario("svg", "tiff", "SVG -> TIFF"),
			scenario("svg", "pdf", "SVG -> PDF", "document"),
			scenario("ai", "png", "AI -> PNG"),
			scenario("ai", "pdf", "AI -> PDF", "document"),
			scenario("eps", "png", "EPS -> PNG"),
			scenario("eps", "pdf", "EPS -> PDF", "document")
		);
	}

	private static ConverterScenarioSpec scenario(String sourceExtension, String targetExtension, String label) {
		return scenario(sourceExtension, targetExtension, label, "image");
	}

	private static ConverterScenarioSpec scenario(
		String sourceExtension,
		String targetExtension,
		String label,
		String family
	) {
		var executionMode = isServerScenario(sourceExtension, targetExtension) ? "server-assisted" : "browser-native";
		return new ConverterScenarioSpec(
			sourceExtension,
			targetExtension,
			label,
			family,
			executionMode,
			"%s -> %s требует доступного backend IMAGE_CONVERT capability."
				.formatted(sourceExtension.toUpperCase(), targetExtension.toUpperCase())
		);
	}

	private static boolean isServerScenario(String sourceExtension, String targetExtension) {
		// После converter route flip браузер больше не выбирает отдельные encode/decode ветки
		// для "простых" форматов: любой supported сценарий идёт через единый job/artifact contract.
		return true;
	}

	private String buildAcceptAttribute(List<ExtensionAliases> definitions) {
		return definitions.stream()
			.flatMap(definition -> definition.allExtensions().stream())
			.map(extension -> "." + extension)
			.collect(Collectors.joining(","));
	}

	private static String buildScenarioKey(String sourceExtension, String targetExtension) {
		return sourceExtension + "->" + targetExtension;
	}

	private static ViewerFormatSpec viewerFormat(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String previewPipeline,
		String previewStrategyId,
		String statusLabel,
		String notes,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
		return new ViewerFormatSpec(
			extension,
			aliases,
			label,
			family,
			mimeTypes,
			previewPipeline,
			previewStrategyId,
			statusLabel,
			notes,
			List.of(extension.toUpperCase()),
			requiredJobTypes,
			unavailableDetail
		);
	}

	private static ViewerFormatSpec viewerFormat(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String previewPipeline,
		String previewStrategyId,
		String statusLabel,
		String notes,
		List<ProcessingJobType> requiredJobTypes
	) {
		return viewerFormat(
			extension,
			aliases,
			label,
			family,
			mimeTypes,
			previewPipeline,
			previewStrategyId,
			statusLabel,
			notes,
			requiredJobTypes,
			null
		);
	}

	private static ConverterSourceSpec sourceFormat(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String sourceStrategyId,
		String statusLabel,
		String notes,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
		return new ConverterSourceSpec(
			extension,
			aliases,
			label,
			family,
			mimeTypes,
			sourceStrategyId,
			statusLabel,
			notes,
			List.of(extension.toUpperCase()),
			requiredJobTypes,
			unavailableDetail
		);
	}

	private static ConverterSourceSpec sourceFormat(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String sourceStrategyId,
		String statusLabel,
		String notes,
		List<ProcessingJobType> requiredJobTypes
	) {
		return sourceFormat(
			extension,
			aliases,
			label,
			family,
			mimeTypes,
			sourceStrategyId,
			statusLabel,
			notes,
			requiredJobTypes,
			null
		);
	}

	private static ConverterTargetSpec targetFormat(
		String extension,
		String label,
		String family,
		String mimeType,
		String targetStrategyId,
		boolean supportsQuality,
		boolean supportsTransparency,
		Double defaultQuality,
		String statusLabel,
		String notes,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
		return new ConverterTargetSpec(
			extension,
			label,
			family,
			mimeType,
			targetStrategyId,
			supportsQuality,
			supportsTransparency,
			defaultQuality,
			statusLabel,
			notes,
			List.of(extension.toUpperCase()),
			requiredJobTypes,
			unavailableDetail
		);
	}

	private static ConverterTargetSpec targetFormat(
		String extension,
		String label,
		String family,
		String mimeType,
		String targetStrategyId,
		boolean supportsQuality,
		boolean supportsTransparency,
		Double defaultQuality,
		String statusLabel,
		String notes,
		List<ProcessingJobType> requiredJobTypes
	) {
		return targetFormat(
			extension,
			label,
			family,
			mimeType,
			targetStrategyId,
			supportsQuality,
			supportsTransparency,
			defaultQuality,
			statusLabel,
			notes,
			requiredJobTypes,
			null
		);
	}

	private record ViewerFormatSpec(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String previewPipeline,
		String previewStrategyId,
		String statusLabel,
		String notes,
		List<String> accents,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
	}

	private record ConverterSourceSpec(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String sourceStrategyId,
		String statusLabel,
		String notes,
		List<String> accents,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
	}

	private record ConverterTargetSpec(
		String extension,
		String label,
		String family,
		String mimeType,
		String targetStrategyId,
		boolean supportsQuality,
		boolean supportsTransparency,
		Double defaultQuality,
		String statusLabel,
		String notes,
		List<String> accents,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
	}

	private record ConverterScenarioSpec(
		String sourceExtension,
		String targetExtension,
		String label,
		String family,
		String executionMode,
		String unavailableDetail
	) {
	}

	private record ConverterPresetSpec(
		String id,
		String label,
		String detail,
		String statusLabel,
		List<String> accents,
		Integer maxWidth,
		Integer maxHeight,
		Double preferredQuality,
		String defaultBackgroundColor
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
