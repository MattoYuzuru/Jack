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
		viewerFormat("heic", List.of("heif"), "HEIC", "image", List.of("image/heic", "image/heif"), "server-assisted", "heic-image", "Server image preview", "HEIC preview строится через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "HEIC preview требует доступного backend IMAGE_CONVERT capability."),
		viewerFormat("tiff", List.of("tif"), "TIFF", "image", List.of("image/tiff"), "server-assisted", "tiff-image", "Server image preview", "TIFF preview строится через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "TIFF preview требует доступного backend IMAGE_CONVERT capability."),
		viewerFormat("raw", List.of("dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw"), "RAW", "image", List.of(), "server-assisted", "raw-image", "Server image preview", "RAW family использует backend preview extraction и metadata pipeline.", List.of(ProcessingJobType.IMAGE_CONVERT), "RAW preview требует доступного backend IMAGE_CONVERT capability."),
		viewerFormat("pdf", List.of(), "PDF", "document", List.of("application/pdf"), "server-assisted", "pdf-document", "Server document preview", "PDF preview и searchable layer собираются через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "PDF preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("txt", List.of(), "TXT", "document", List.of("text/plain"), "server-assisted", "text-document", "Server text preview", "Text extraction и search summary идут через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "TXT preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("csv", List.of(), "CSV", "document", List.of("text/csv", "application/csv"), "server-assisted", "csv-document", "Server table preview", "CSV preview строится в backend как bounded table payload.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "CSV preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("html", List.of("htm"), "HTML", "document", List.of("text/html"), "server-assisted", "html-document", "Server sandbox preview", "HTML проходит через backend sanitization и outline extraction.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "HTML preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("rtf", List.of(), "RTF", "document", List.of("application/rtf", "text/rtf"), "server-assisted", "rtf-document", "Server text extraction", "Legacy rich text извлекается через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "RTF preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("doc", List.of(), "DOC", "document", List.of("application/msword"), "server-assisted", "doc-document", "Server document preview", "Legacy Word document разбирается через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "DOC preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("docx", List.of(), "DOCX", "document", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), "server-assisted", "docx-document", "Server document preview", "OOXML word payload собирается через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "DOCX preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("odt", List.of(), "ODT", "document", List.of("application/vnd.oasis.opendocument.text"), "server-assisted", "odt-document", "Server document preview", "OpenDocument text разбирается через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "ODT preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("xls", List.of(), "XLS", "document", List.of("application/vnd.ms-excel"), "server-assisted", "xls-document", "Server table preview", "Legacy spreadsheet проходит через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "XLS preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("xlsx", List.of(), "XLSX", "document", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "server-assisted", "xlsx-document", "Server table preview", "OOXML spreadsheet проходит через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "XLSX preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("pptx", List.of(), "PPTX", "document", List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"), "server-assisted", "pptx-document", "Server slide preview", "Presentation outline и text summary идут через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "PPTX preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("epub", List.of(), "EPUB", "document", List.of("application/epub+zip"), "server-assisted", "epub-document", "Server reading preview", "EPUB content и chapters разбираются через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "EPUB preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("db", List.of(), "DB", "document", List.of("application/octet-stream"), "server-assisted", "sqlite-document", "Server database preview", "SQLite schema/sample introspection идёт через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "DB preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("sqlite", List.of(), "SQLite", "document", List.of("application/x-sqlite3"), "server-assisted", "sqlite-document", "Server database preview", "SQLite schema/sample introspection идёт через backend DOCUMENT_PREVIEW.", List.of(ProcessingJobType.DOCUMENT_PREVIEW), "SQLite preview требует доступного backend DOCUMENT_PREVIEW capability."),
		viewerFormat("mp4", List.of(), "MP4", "media", List.of("video/mp4"), "browser-native", "native-video", "Browser video preview", "MP4 воспроизводится напрямую через HTMLVideoElement.", List.of()),
		viewerFormat("mov", List.of(), "MOV", "media", List.of("video/quicktime"), "browser-native", "native-video", "Browser video preview", "MOV использует тот же browser-native playback path.", List.of()),
		viewerFormat("webm", List.of(), "WebM", "media", List.of("video/webm"), "browser-native", "native-video", "Browser video preview", "WebM воспроизводится напрямую без server transcode.", List.of()),
		viewerFormat("avi", List.of(), "AVI", "media", List.of("video/x-msvideo"), "server-assisted", "legacy-video", "Server video preview", "Legacy video container нормализуется через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "AVI preview требует доступного backend MEDIA_PREVIEW capability."),
		viewerFormat("mkv", List.of(), "MKV", "media", List.of("video/x-matroska"), "server-assisted", "legacy-video", "Server video preview", "Matroska container нормализуется через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "MKV preview требует доступного backend MEDIA_PREVIEW capability."),
		viewerFormat("wmv", List.of(), "WMV", "media", List.of("video/x-ms-wmv"), "server-assisted", "legacy-video", "Server video preview", "WMV normalizes через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "WMV preview требует доступного backend MEDIA_PREVIEW capability."),
		viewerFormat("flv", List.of(), "FLV", "media", List.of("video/x-flv"), "server-assisted", "legacy-video", "Server video preview", "FLV normalizes через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "FLV preview требует доступного backend MEDIA_PREVIEW capability."),
		viewerFormat("mp3", List.of(), "MP3", "audio", List.of("audio/mpeg"), "browser-native", "native-audio", "Browser audio preview", "MP3 playback и waveform идут через browser-native audio path.", List.of()),
		viewerFormat("wav", List.of(), "WAV", "audio", List.of("audio/wav", "audio/x-wav"), "browser-native", "native-audio", "Browser audio preview", "WAV воспроизводится напрямую в браузере.", List.of()),
		viewerFormat("ogg", List.of(), "OGG", "audio", List.of("audio/ogg"), "browser-native", "native-audio", "Browser audio preview", "OGG остаётся в browser-native audio path.", List.of()),
		viewerFormat("opus", List.of(), "OPUS", "audio", List.of("audio/ogg; codecs=opus", "audio/opus"), "browser-native", "native-audio", "Browser audio preview", "Opus playback остаётся browser-native.", List.of()),
		viewerFormat("aac", List.of(), "AAC", "audio", List.of("audio/aac"), "server-assisted", "legacy-audio", "Server audio preview", "AAC normalized через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "AAC preview требует доступного backend MEDIA_PREVIEW capability."),
		viewerFormat("flac", List.of(), "FLAC", "audio", List.of("audio/flac"), "server-assisted", "legacy-audio", "Server audio preview", "FLAC normalized через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "FLAC preview требует доступного backend MEDIA_PREVIEW capability."),
		viewerFormat("aiff", List.of("aif"), "AIFF", "audio", List.of("audio/aiff", "audio/x-aiff"), "server-assisted", "legacy-audio", "Server audio preview", "AIFF normalized через backend MEDIA_PREVIEW.", List.of(ProcessingJobType.MEDIA_PREVIEW), "AIFF preview требует доступного backend MEDIA_PREVIEW capability.")
	);

	private static final List<ConverterSourceSpec> CONVERTER_SOURCE_SPECS = List.of(
		sourceFormat("jpg", List.of("jpeg"), "JPG", "image", List.of("image/jpeg"), "native-raster", "Browser raster", "Нативный raster source без промежуточного decode слоя.", List.of()),
		sourceFormat("png", List.of(), "PNG", "image", List.of("image/png"), "native-raster", "Browser raster", "Lossless raster source с alpha-каналом.", List.of()),
		sourceFormat("webp", List.of(), "WebP", "image", List.of("image/webp"), "native-raster", "Browser raster", "Современный browser-supported raster source.", List.of()),
		sourceFormat("bmp", List.of(), "BMP", "image", List.of("image/bmp"), "native-raster", "Browser raster", "Большой bitmap source остаётся browser-native.", List.of()),
		sourceFormat("svg", List.of(), "SVG", "image", List.of("image/svg+xml"), "native-raster", "Browser raster", "Вектор сначала rasterize в browser pipeline.", List.of()),
		sourceFormat("psd", List.of(), "PSD", "image", List.of("image/vnd.adobe.photoshop", "application/vnd.adobe.photoshop"), "psd-raster", "Server composite", "PSD source собирается через backend IMAGE_CONVERT composite path.", List.of(ProcessingJobType.IMAGE_CONVERT), "PSD source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("ai", List.of(), "AI", "image", List.of(), "illustration-raster", "Server illustration", "Illustrator source растеризуется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "AI source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("eps", List.of("ps"), "EPS", "image", List.of(), "illustration-raster", "Server illustration", "PostScript source растеризуется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "EPS source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("heic", List.of("heif"), "HEIC", "image", List.of("image/heic", "image/heif"), "heic-raster", "Server rasterization", "HEIC source декодируется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "HEIC source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("tiff", List.of("tif"), "TIFF", "image", List.of("image/tiff"), "tiff-raster", "Server rasterization", "TIFF source декодируется через backend IMAGE_CONVERT.", List.of(ProcessingJobType.IMAGE_CONVERT), "TIFF source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("raw", List.of("dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw"), "RAW", "image", List.of(), "raw-raster", "Server rasterization", "RAW family идёт через backend preview extraction.", List.of(ProcessingJobType.IMAGE_CONVERT), "RAW source требует доступного backend IMAGE_CONVERT capability.")
	);

	private static final List<ConverterTargetSpec> CONVERTER_TARGET_SPECS = List.of(
		targetFormat("jpg", "JPG", "image", "image/jpeg", "jpeg-encoder", true, false, 0.9, "Canvas encode", "Практичный совместимый raster target.", List.of()),
		targetFormat("png", "PNG", "image", "image/png", "png-encoder", false, true, null, "Canvas encode", "Lossless target с transparency.", List.of()),
		targetFormat("webp", "WebP", "image", "image/webp", "webp-encoder", true, true, 0.9, "Canvas encode", "Компактный modern raster target.", List.of()),
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
				? "Сценарий идёт через backend IMAGE_CONVERT jobs: frontend остаётся orchestration/UI слоем и получает preview/result artifacts."
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
		return List.of("heic", "tiff", "raw", "psd", "ai", "eps").contains(sourceExtension)
			|| List.of("avif", "svg", "ico", "tiff", "pdf").contains(targetExtension);
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
