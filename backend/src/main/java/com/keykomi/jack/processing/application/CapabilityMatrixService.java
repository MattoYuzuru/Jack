package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
		sourceFormat("raw", List.of("dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw"), "RAW", "image", List.of(), "raw-raster", "Server rasterization", "RAW family идёт через backend preview extraction.", List.of(ProcessingJobType.IMAGE_CONVERT), "RAW source требует доступного backend IMAGE_CONVERT capability."),
		sourceFormat("pdf", List.of(), "PDF", "document", List.of("application/pdf"), "pdf-document", "Office route", "PDF source теперь идёт в OFFICE_CONVERT для text/table/image/presentation roundtrip сценариев.", List.of(ProcessingJobType.OFFICE_CONVERT), "PDF source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("doc", List.of(), "DOC", "document", List.of("application/msword"), "office-document", "Office route", "Legacy Word document использует OFFICE_CONVERT поверх backend text extraction и structured export.", List.of(ProcessingJobType.OFFICE_CONVERT), "DOC source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("docx", List.of(), "DOCX", "document", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), "office-document", "Office route", "DOCX source теперь идёт в OFFICE_CONVERT для PDF/TXT/HTML/RTF/ODT roundtrip сценариев.", List.of(ProcessingJobType.OFFICE_CONVERT), "DOCX source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("rtf", List.of(), "RTF", "document", List.of("application/rtf", "text/rtf"), "office-document", "Office route", "RTF source использует OFFICE_CONVERT для DOCX compatibility export.", List.of(ProcessingJobType.OFFICE_CONVERT), "RTF source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("odt", List.of(), "ODT", "document", List.of("application/vnd.oasis.opendocument.text"), "office-document", "Office route", "ODT source использует OFFICE_CONVERT для DOCX roundtrip и structured export.", List.of(ProcessingJobType.OFFICE_CONVERT), "ODT source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("csv", List.of(), "CSV", "document", List.of("text/csv", "application/csv"), "spreadsheet-document", "Office route", "CSV source теперь идёт в OFFICE_CONVERT для workbook export.", List.of(ProcessingJobType.OFFICE_CONVERT), "CSV source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("xlsx", List.of(), "XLSX", "document", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "spreadsheet-document", "Office route", "XLSX source идёт в OFFICE_CONVERT для CSV/PDF/ODS export.", List.of(ProcessingJobType.OFFICE_CONVERT), "XLSX source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("ods", List.of(), "ODS", "document", List.of("application/vnd.oasis.opendocument.spreadsheet"), "spreadsheet-document", "Office route", "ODS source идёт в OFFICE_CONVERT для XLSX roundtrip.", List.of(ProcessingJobType.OFFICE_CONVERT), "ODS source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("pptx", List.of(), "PPTX", "document", List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"), "presentation-document", "Office route", "PPTX source идёт в OFFICE_CONVERT для PDF/image/video export.", List.of(ProcessingJobType.OFFICE_CONVERT), "PPTX source требует доступного backend OFFICE_CONVERT capability."),
		sourceFormat("mp4", List.of(), "MP4", "media", List.of("video/mp4"), "video-media", "Media route", "MP4 source идёт в MEDIA_CONVERT для container/codec/fps/resolution transcode и audio extract export.", List.of(ProcessingJobType.MEDIA_CONVERT), "MP4 source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("mov", List.of(), "MOV", "media", List.of("video/quicktime"), "video-media", "Media route", "MOV source идёт в MEDIA_CONVERT для MP4 normalize, GIF export и audio extraction.", List.of(ProcessingJobType.MEDIA_CONVERT), "MOV source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("mkv", List.of(), "MKV", "media", List.of("video/x-matroska"), "video-media", "Media route", "MKV source идёт в MEDIA_CONVERT для MP4 normalize и delivery-friendly transcode.", List.of(ProcessingJobType.MEDIA_CONVERT), "MKV source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("avi", List.of(), "AVI", "media", List.of("video/x-msvideo"), "video-media", "Media route", "AVI source идёт в MEDIA_CONVERT для MP4 normalize и audio export.", List.of(ProcessingJobType.MEDIA_CONVERT), "AVI source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("webm", List.of(), "WebM", "media", List.of("video/webm"), "video-media", "Media route", "WebM source идёт в MEDIA_CONVERT для MP4 transcode, GIF export и audio extraction.", List.of(ProcessingJobType.MEDIA_CONVERT), "WebM source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("wav", List.of(), "WAV", "media", List.of("audio/wav", "audio/x-wav"), "audio-media", "Media route", "WAV source идёт в MEDIA_CONVERT для MP3/FLAC/AAC delivery export.", List.of(ProcessingJobType.MEDIA_CONVERT), "WAV source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("flac", List.of(), "FLAC", "media", List.of("audio/flac"), "audio-media", "Media route", "FLAC source идёт в MEDIA_CONVERT для MP3/WAV delivery export.", List.of(ProcessingJobType.MEDIA_CONVERT), "FLAC source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("mp3", List.of(), "MP3", "media", List.of("audio/mpeg"), "audio-media", "Media route", "MP3 source идёт в MEDIA_CONVERT для M4A/AAC compatibility export.", List.of(ProcessingJobType.MEDIA_CONVERT), "MP3 source требует доступного backend MEDIA_CONVERT capability."),
		sourceFormat("m4a", List.of(), "M4A", "media", List.of("audio/mp4", "audio/x-m4a"), "audio-media", "Media route", "M4A source идёт в MEDIA_CONVERT для MP3 roundtrip и bitrate normalization.", List.of(ProcessingJobType.MEDIA_CONVERT), "M4A source требует доступного backend MEDIA_CONVERT capability.")
	);

	private static final List<ConverterTargetSpec> CONVERTER_TARGET_SPECS = List.of(
		targetFormat("jpg", "JPG", "image", "image/jpeg", "jpeg-encoder", true, false, 0.9, "Server target", "Практичный raster target теперь используется и в image, и в office contact-sheet сценариях.", List.of()),
		targetFormat("png", "PNG", "image", "image/png", "png-encoder", false, true, null, "Server target", "Lossless raster target теперь используется и для office page/slide exports.", List.of()),
		targetFormat("webp", "WebP", "image", "image/webp", "webp-encoder", true, true, 0.9, "Server target", "Компактный modern raster target собирается через backend conversion routes.", List.of()),
		targetFormat("avif", "AVIF", "image", "image/avif", "avif-encoder", true, true, 0.78, "Server target", "AVIF target собирается через backend conversion routes.", List.of()),
		targetFormat("svg", "SVG", "image", "image/svg+xml", "svg-vectorizer", false, true, null, "Server target", "SVG target собирается через backend trace path.", List.of()),
		targetFormat("ico", "ICO", "image", "image/x-icon", "ico-image", false, true, null, "Server target", "ICO target собирается через backend multi-size icon path.", List.of()),
		targetFormat("pdf", "PDF", "document", "application/pdf", "pdf-document", false, false, null, "Server target", "PDF target теперь покрывает и image raster export, и office document export.", List.of()),
		targetFormat("tiff", "TIFF", "image", "image/tiff", "tiff-image", false, true, null, "Server target", "TIFF target собирается через backend archive-friendly encode path.", List.of()),
		targetFormat("docx", "DOCX", "document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx-document", false, true, null, "Office export", "DOCX target собирается через OFFICE_CONVERT как structured narrative export.", List.of()),
		targetFormat("txt", "TXT", "document", "text/plain", "txt-document", false, true, null, "Office export", "TXT target собирается через OFFICE_CONVERT из extracted text layer.", List.of()),
		targetFormat("html", "HTML", "document", "text/html", "html-document", false, true, null, "Office export", "HTML target собирается через OFFICE_CONVERT как safe preview/export document.", List.of()),
		targetFormat("rtf", "RTF", "document", "application/rtf", "rtf-document", false, true, null, "Office export", "RTF target собирается через OFFICE_CONVERT как compatibility export.", List.of()),
		targetFormat("odt", "ODT", "document", "application/vnd.oasis.opendocument.text", "odt-document", false, true, null, "Office export", "ODT target собирается через OFFICE_CONVERT как OpenDocument text export.", List.of()),
		targetFormat("xlsx", "XLSX", "document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx-document", false, true, null, "Office export", "XLSX target собирается через OFFICE_CONVERT как workbook export.", List.of()),
		targetFormat("csv", "CSV", "document", "text/csv", "csv-document", false, true, null, "Office export", "CSV target собирается через OFFICE_CONVERT как flattened table export без formulas/styles и с single-sheet ограничением.", List.of()),
		targetFormat("ods", "ODS", "document", "application/vnd.oasis.opendocument.spreadsheet", "ods-document", false, true, null, "Office export", "ODS target собирается через OFFICE_CONVERT как OpenDocument spreadsheet export.", List.of()),
		targetFormat("pptx", "PPTX", "document", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx-document", false, true, null, "Office export", "PPTX target собирается через OFFICE_CONVERT как image-slide deck export.", List.of()),
		targetFormat("mp4", "MP4", "media", "video/mp4", "mp4-video", false, true, null, "Media export", "MP4 target теперь используется и для OFFICE_CONVERT slide-reel video export, и для MEDIA_CONVERT container/codec transcode.", List.of()),
		targetFormat("webm", "WebM", "media", "video/webm", "webm-video", false, true, null, "Media export", "WebM target собирается через MEDIA_CONVERT с раздельным container/codec/fps/bitrate control.", List.of()),
		targetFormat("gif", "GIF", "image", "image/gif", "gif-image", false, true, null, "Media export", "GIF target собирается через MEDIA_CONVERT как reduced-fps animated export без audio track.", List.of()),
		targetFormat("mp3", "MP3", "media", "audio/mpeg", "mp3-audio", false, true, null, "Media export", "MP3 target собирается через MEDIA_CONVERT как lossy delivery audio export.", List.of()),
		targetFormat("wav", "WAV", "media", "audio/wav", "wav-audio", false, true, null, "Media export", "WAV target собирается через MEDIA_CONVERT как PCM audio export.", List.of()),
		targetFormat("aac", "AAC", "media", "audio/aac", "aac-audio", false, true, null, "Media export", "AAC target собирается через MEDIA_CONVERT как compact audio delivery export.", List.of()),
		targetFormat("m4a", "M4A", "media", "audio/mp4", "m4a-audio", false, true, null, "Media export", "M4A target собирается через MEDIA_CONVERT как AAC-in-M4A compatibility export.", List.of()),
		targetFormat("flac", "FLAC", "media", "audio/flac", "flac-audio", false, true, null, "Media export", "FLAC target собирается через MEDIA_CONVERT как lossless audio export.", List.of())
	);

	private static final List<ConverterPresetSpec> CONVERTER_PRESET_SPECS = List.of(
		new ConverterPresetSpec("original", "Original", "Не меняет размерность и оставляет runtime только target-specific encode решения.", "No resize", List.of("Original size", "Safe base"), null, null, null, "#ffffff"),
		new ConverterPresetSpec("web-balanced", "Web Balanced", "Практичный пресет для веба с мягким ограничением крупных изображений.", "2560 px cap", List.of("Web", "Balanced"), 2560, 2560, 0.86, "#ffffff"),
		new ConverterPresetSpec("email-attachment", "Email Attachment", "Агрессивнее ограничивает размерность и bitrate под вложения.", "1600 px cap", List.of("Email", "Compact"), 1600, 1600, 0.78, "#fffaf0"),
		new ConverterPresetSpec("thumbnail", "Thumbnail", "Миниатюрный профиль для карточек и лёгких превью.", "512 px cap", List.of("Preview", "Small"), 512, 512, 0.72, "#f3ede3")
	);

	private static final List<PlatformModuleSpec> PLATFORM_MODULE_SPECS = List.of(
		platformModule(
			"compression",
			"Compression",
			"Compression уже поднят как отдельный backend-first route поверх existing image/media jobs и общего artifact lifecycle.",
			"Compression workspace больше не живёт как queued placeholder: dedicated FILE_COMPRESS orchestration reuse'ит IMAGE_CONVERT и MEDIA_CONVERT как внутренние candidate builders и отдаёт единый size-first manifest/result contract.",
			List.of("Target size", "Quality", "Batch"),
			List.of("image-processing", "media-processing", "artifact-storage", "capabilities"),
			List.of(ProcessingJobType.FILE_COMPRESS, ProcessingJobType.IMAGE_CONVERT, ProcessingJobType.MEDIA_CONVERT),
			List.of("image quality presets", "video/audio bitrate targeting", "batch compression"),
			"Compression module требует доступных FILE_COMPRESS, IMAGE_CONVERT и MEDIA_CONVERT capabilities."
		),
		platformModule(
			"pdf-toolkit",
			"PDF Toolkit",
			"PDF toolkit может расти поверх document intelligence, viewer artifacts и image/document processing без нового browser-first runtime.",
			"Preview, page-aware payload, extraction и server raster artifacts уже существуют. Merge/split/rotate/e-sign/redaction должны использовать тот же job/artifact lifecycle, а не разрозненные клиентские пайплайны.",
			List.of("Merge", "Rotate", "Protect"),
			List.of("document-processing", "image-processing", "viewer-resolve", "artifact-storage"),
			List.of(ProcessingJobType.DOCUMENT_PREVIEW, ProcessingJobType.IMAGE_CONVERT, ProcessingJobType.VIEWER_RESOLVE),
			List.of("merge/split/rotate", "page reorder", "protected PDF flows"),
			"PDF toolkit требует доступных DOCUMENT_PREVIEW, IMAGE_CONVERT и VIEWER_RESOLVE capabilities."
		),
		platformModule(
			"multi-format-editor",
			"Multi-Format Editor",
			"Editor может использовать backend document contracts как safe inspect/validate/export base, а frontend оставить за interaction и live preview.",
			"Structured document payload, HTML sanitization и file artifact lifecycle уже готовы. Новый editor должен держать editing UX на фронте, а validation, conversion и risky file mutation выносить в processing platform.",
			List.of("Preview", "Validate", "Export"),
			List.of("document-processing", "metadata-processing", "artifact-storage"),
			List.of(ProcessingJobType.DOCUMENT_PREVIEW, ProcessingJobType.METADATA_EXPORT),
			List.of("markdown/html preview", "safe export", "format-specific validation"),
			"Editor module требует доступных DOCUMENT_PREVIEW и METADATA_EXPORT capabilities."
		),
		platformModule(
			"batch-conversion",
			"Batch Conversion",
			"Batch conversion теперь не нужно строить с нуля: upload/job/artifact foundation уже умеет orchestrate несколько backend-first scenarios.",
			"Ключевой следующий шаг для batch-модуля не новый runtime, а поверх existing IMAGE_CONVERT и capability matrix добавить пакетный UX, queue visibility и reuse уже собранных artifacts.",
			List.of("Queue", "Reuse", "Artifacts"),
			List.of("job-orchestration", "image-processing", "artifact-storage", "capabilities"),
			List.of(ProcessingJobType.IMAGE_CONVERT),
			List.of("multi-file submit", "queue progress", "bulk download"),
			"Batch conversion требует доступного IMAGE_CONVERT capability."
		),
		platformModule(
			"ocr",
			"OCR",
			"OCR-слой может стартовать поверх существующих document/image intake contracts: ingest, preview, warnings и artifacts уже готовы.",
			"Нового OCR runtime пока нет, но это уже не причина строить отдельный модуль browser-first. OCR должен reuse'ить DOCUMENT_PREVIEW для document context, IMAGE_CONVERT для page/image preprocessing и общий job/artifact lifecycle.",
			List.of("Extract", "Search", "Scan"),
			List.of("document-processing", "image-processing", "artifact-storage"),
			List.of(ProcessingJobType.DOCUMENT_PREVIEW, ProcessingJobType.IMAGE_CONVERT),
			List.of("scan preprocessing", "searchable PDF", "OCR text layer"),
			"OCR module требует доступных DOCUMENT_PREVIEW и IMAGE_CONVERT capabilities."
		),
		platformModule(
			"office-pdf-conversion",
			"Office/PDF Conversion",
			"Office/PDF conversion теперь уже живёт как отдельный backend-first route поверх processing platform.",
			"OFFICE_CONVERT reuse'ит existing upload/job/artifact contracts и document intelligence, а browser держит только orchestration, preview и retry/cancel UX без нового browser-heavy runtime.",
			List.of("DOCX", "PDF", "Office"),
			List.of("document-processing", "job-orchestration", "artifact-storage", "capabilities", "media-processing"),
			List.of(ProcessingJobType.OFFICE_CONVERT, ProcessingJobType.DOCUMENT_PREVIEW),
			List.of("docx/pdf roundtrip", "xlsx/csv/ods flows", "presentation export"),
			"Office/PDF conversion foundation требует доступных OFFICE_CONVERT и DOCUMENT_PREVIEW capabilities."
		)
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

	public CapabilityMatrixPayloads.PlatformCapabilityMatrix platformMatrix(
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var modules = PLATFORM_MODULE_SPECS.stream()
			.map(spec -> toPlatformModule(spec, availabilityByJobType))
			.toList();

		return new CapabilityMatrixPayloads.PlatformCapabilityMatrix(modules);
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
			buildAccents(spec.accents(), "server-assisted"),
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
		var requiredJobTypes = new ArrayList<>(spec.requiredJobTypes());
		if (requiredJobTypes.isEmpty()) {
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
		}
		var available = allRequiredJobsAvailable(requiredJobTypes, availabilityByJobType);
		var officeScenario = requiredJobTypes.contains(ProcessingJobType.OFFICE_CONVERT);
		var mediaScenario = requiredJobTypes.contains(ProcessingJobType.MEDIA_CONVERT);
		var detail = officeScenario
			? "Сценарий идёт через backend OFFICE_CONVERT jobs: frontend держит progress/retry/cancel/reuse UX и получает preview/result artifacts."
			: mediaScenario
				? "Сценарий идёт через backend MEDIA_CONVERT jobs: контейнер, codec, bitrate, resolution и FPS собираются server-side в одном artifact contract."
				: "Сценарий идёт через backend IMAGE_CONVERT jobs: frontend держит progress/retry/cancel/reuse UX и получает preview/result artifacts.";

		return new CapabilityMatrixPayloads.ConverterScenarioCapability(
			buildScenarioKey(spec.sourceExtension(), spec.targetExtension()),
			spec.family(),
			spec.label(),
			spec.sourceExtension(),
			spec.targetExtension(),
			available ? ("server-assisted".equals(spec.executionMode()) ? "Server-assisted" : "Browser-native") : "Capability unavailable",
			"server-assisted".equals(spec.executionMode())
				? resolveScenarioNotes(spec, requiredJobTypes, detail)
				: "Сценарий закрывается локально через browser-native raster pipeline без backend round-trip.",
			List.of(spec.sourceExtension().toUpperCase(), spec.targetExtension().toUpperCase()),
			spec.executionMode(),
			available,
			available ? null : spec.unavailableDetail(),
			requiredJobTypes
		);
	}

	private CapabilityMatrixPayloads.PlatformModuleCapability toPlatformModule(
		PlatformModuleSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var foundationReady = allRequiredJobsAvailable(spec.reusedJobTypes(), availabilityByJobType);
		return new CapabilityMatrixPayloads.PlatformModuleCapability(
			spec.id(),
			spec.label(),
			spec.summary(),
			spec.detail(),
			foundationReady ? "Foundation ready" : "Needs extra runtime",
			spec.accents(),
			spec.reusedDomains(),
			spec.reusedJobTypes(),
			spec.nextSlices(),
			foundationReady,
			foundationReady ? null : spec.unavailableDetail()
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
			scenario("heic", "jpg", "HEIC decode -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("heic", "avif", "HEIC -> AVIF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("heic", "tiff", "HEIC -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "jpg", "PNG -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "webp", "PNG -> WebP", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "avif", "PNG -> AVIF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "svg", "PNG -> SVG trace", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "ico", "PNG -> ICO", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "tiff", "PNG -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("jpg", "png", "JPG -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("jpg", "webp", "JPG -> WebP", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("jpg", "avif", "JPG -> AVIF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("jpg", "tiff", "JPG -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("webp", "jpg", "WebP -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("webp", "png", "WebP -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("webp", "tiff", "WebP -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("bmp", "jpg", "BMP -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("bmp", "png", "BMP -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("bmp", "tiff", "BMP -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("psd", "jpg", "PSD -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("psd", "png", "PSD -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("psd", "webp", "PSD -> WebP", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("tiff", "jpg", "TIFF -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("tiff", "pdf", "TIFF -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("tiff", "tiff", "TIFF -> TIFF refresh", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("raw", "jpg", "RAW -> JPG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("raw", "pdf", "RAW -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("raw", "tiff", "RAW -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("jpg", "pdf", "JPG -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("png", "pdf", "PNG -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("webp", "pdf", "WebP -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("bmp", "pdf", "BMP -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("heic", "pdf", "HEIC -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("svg", "png", "SVG -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("svg", "ico", "SVG -> ICO", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("svg", "tiff", "SVG -> TIFF", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("svg", "pdf", "SVG -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("ai", "png", "AI -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("ai", "pdf", "AI -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("eps", "png", "EPS -> PNG", "image", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("eps", "pdf", "EPS -> PDF", "document", List.of(ProcessingJobType.IMAGE_CONVERT)),
			scenario("doc", "docx", "DOC -> DOCX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("docx", "pdf", "DOCX -> PDF", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "docx", "PDF -> DOCX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("docx", "txt", "DOCX -> TXT", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("docx", "html", "DOCX -> HTML", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("rtf", "docx", "RTF -> DOCX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("docx", "rtf", "DOCX -> RTF", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("odt", "docx", "ODT -> DOCX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("docx", "odt", "DOCX -> ODT", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "jpg", "PDF -> JPG", "image", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "png", "PDF -> PNG", "image", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "txt", "PDF -> TXT", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "xlsx", "PDF -> XLSX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "csv", "PDF -> CSV", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pdf", "pptx", "PDF -> PPTX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("xlsx", "csv", "XLSX -> CSV", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("csv", "xlsx", "CSV -> XLSX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("xlsx", "pdf", "XLSX -> PDF", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("ods", "xlsx", "ODS -> XLSX", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("xlsx", "ods", "XLSX -> ODS", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pptx", "pdf", "PPTX -> PDF", "document", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pptx", "jpg", "PPTX -> JPG", "image", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pptx", "png", "PPTX -> PNG", "image", List.of(ProcessingJobType.OFFICE_CONVERT)),
			scenario("pptx", "mp4", "PPTX -> MP4 video", "media", List.of(ProcessingJobType.OFFICE_CONVERT, ProcessingJobType.MEDIA_PREVIEW)),
			scenario("mov", "mp4", "MOV -> MP4", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mkv", "mp4", "MKV -> MP4", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("avi", "mp4", "AVI -> MP4", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("webm", "mp4", "WebM -> MP4", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp4", "mp4", "MP4 -> MP4 transcode", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp4", "webm", "MP4 -> WebM", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mov", "gif", "MOV -> GIF", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mkv", "gif", "MKV -> GIF", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("avi", "gif", "AVI -> GIF", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("webm", "gif", "WebM -> GIF", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp4", "gif", "MP4 -> GIF", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mov", "mp3", "MOV -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mov", "wav", "MOV -> WAV", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mov", "aac", "MOV -> AAC", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mkv", "mp3", "MKV -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mkv", "wav", "MKV -> WAV", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mkv", "aac", "MKV -> AAC", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("avi", "mp3", "AVI -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("avi", "wav", "AVI -> WAV", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("avi", "aac", "AVI -> AAC", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("webm", "mp3", "WebM -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("webm", "wav", "WebM -> WAV", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("webm", "aac", "WebM -> AAC", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp4", "mp3", "MP4 -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp4", "wav", "MP4 -> WAV", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp4", "aac", "MP4 -> AAC", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("wav", "mp3", "WAV -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("wav", "flac", "WAV -> FLAC", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("flac", "mp3", "FLAC -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("flac", "wav", "FLAC -> WAV", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("m4a", "mp3", "M4A -> MP3", "media", List.of(ProcessingJobType.MEDIA_CONVERT)),
			scenario("mp3", "m4a", "MP3 -> M4A", "media", List.of(ProcessingJobType.MEDIA_CONVERT))
		);
	}

	private static ConverterScenarioSpec scenario(String sourceExtension, String targetExtension, String label) {
		return scenario(sourceExtension, targetExtension, label, "image", List.of());
	}

	private static ConverterScenarioSpec scenario(
		String sourceExtension,
		String targetExtension,
		String label,
		String family
	) {
		return scenario(sourceExtension, targetExtension, label, family, List.of());
	}

	private static ConverterScenarioSpec scenario(
		String sourceExtension,
		String targetExtension,
		String label,
		String family,
		List<ProcessingJobType> requiredJobTypes
	) {
		var executionMode = isServerScenario(sourceExtension, targetExtension) ? "server-assisted" : "browser-native";
		return new ConverterScenarioSpec(
			sourceExtension,
			targetExtension,
			label,
			family,
			executionMode,
			unavailableDetail(sourceExtension, targetExtension, requiredJobTypes),
			requiredJobTypes
		);
	}

	private static String unavailableDetail(
		String sourceExtension,
		String targetExtension,
		List<ProcessingJobType> requiredJobTypes
	) {
		if (requiredJobTypes.contains(ProcessingJobType.MEDIA_CONVERT)) {
			return "%s -> %s требует доступного backend MEDIA_CONVERT capability."
				.formatted(sourceExtension.toUpperCase(), targetExtension.toUpperCase());
		}
		if (requiredJobTypes.contains(ProcessingJobType.OFFICE_CONVERT)) {
			return "%s -> %s требует доступного backend OFFICE_CONVERT capability."
				.formatted(sourceExtension.toUpperCase(), targetExtension.toUpperCase());
		}
		return "%s -> %s требует доступного backend IMAGE_CONVERT capability."
			.formatted(sourceExtension.toUpperCase(), targetExtension.toUpperCase());
	}

	private static boolean isServerScenario(String sourceExtension, String targetExtension) {
		// После converter route flip браузер больше не выбирает отдельные encode/decode ветки
		// для "простых" форматов: любой supported сценарий идёт через единый job/artifact contract.
		return true;
	}

	private static String resolveScenarioNotes(
		ConverterScenarioSpec spec,
		List<ProcessingJobType> requiredJobTypes,
		String defaultDetail
	) {
		if (requiredJobTypes.contains(ProcessingJobType.MEDIA_CONVERT)) {
			if ("gif".equals(spec.targetExtension())) {
				return defaultDetail + " GIF target отдельно от контейнера фиксирует palette/fps constraints и всегда отбрасывает audio track.";
			}
			if (Set.of("mp3", "wav", "aac", "m4a", "flac").contains(spec.targetExtension())) {
				return defaultDetail + " Audio export отделяет container от bitrate rules: video stream отбрасывается, а browser получает preview уже готового audio artifact.";
			}
			return defaultDetail + " Target format выбирает контейнер, а codec/bitrate/resolution/FPS настраиваются отдельно в media controls этого же workspace.";
		}
		if ("pdf".equals(spec.sourceExtension()) && "docx".equals(spec.targetExtension())) {
			return defaultDetail + " PDF -> DOCX reconstructs text flow из доступного text layer, поэтому сложная верстка, колонки и positioned blocks могут поехать.";
		}
		if ("pdf".equals(spec.sourceExtension()) && Set.of("docx", "txt", "xlsx", "csv", "pptx").contains(spec.targetExtension())) {
			return defaultDetail + " Если source PDF scanned и без text layer, сначала потребуется OCR: текущий export честно предупредит об этом и не притворяется точным reconstruction.";
		}
		if ("csv".equals(spec.targetExtension())) {
			return defaultDetail + " CSV target остаётся flattened table export: formulas, styling, comments и multi-sheet structure не переносятся полностью.";
		}
		return defaultDetail;
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
		String unavailableDetail,
		List<ProcessingJobType> requiredJobTypes
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

	private record PlatformModuleSpec(
		String id,
		String label,
		String summary,
		String detail,
		List<String> accents,
		List<String> reusedDomains,
		List<ProcessingJobType> reusedJobTypes,
		List<String> nextSlices,
		String unavailableDetail
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

	private static PlatformModuleSpec platformModule(
		String id,
		String label,
		String summary,
		String detail,
		List<String> accents,
		List<String> reusedDomains,
		List<ProcessingJobType> reusedJobTypes,
		List<String> nextSlices,
		String unavailableDetail
	) {
		return new PlatformModuleSpec(
			id,
			label,
			summary,
			detail,
			accents,
			reusedDomains,
			reusedJobTypes,
			nextSlices,
			unavailableDetail
		);
	}

}
