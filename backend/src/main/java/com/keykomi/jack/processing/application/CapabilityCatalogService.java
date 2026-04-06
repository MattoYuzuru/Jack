package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CapabilityCatalogService {

	private final MediaPreviewService mediaPreviewService;
	private final MediaConversionService mediaConversionService;
	private final ImageProcessingService imageProcessingService;
	private final CompressionService compressionService;
	private final PdfToolkitService pdfToolkitService;
	private final OfficeConversionService officeConversionService;
	private final DocumentPreviewService documentPreviewService;
	private final MetadataProcessingService metadataProcessingService;
	private final ViewerResolveService viewerResolveService;
	private final CapabilityMatrixService capabilityMatrixService;
	private final CompressionCapabilityMatrixService compressionCapabilityMatrixService;
	private final PdfToolkitCapabilityMatrixService pdfToolkitCapabilityMatrixService;

	public CapabilityCatalogService(
		MediaPreviewService mediaPreviewService,
		MediaConversionService mediaConversionService,
		ImageProcessingService imageProcessingService,
		CompressionService compressionService,
		PdfToolkitService pdfToolkitService,
		OfficeConversionService officeConversionService,
		DocumentPreviewService documentPreviewService,
		MetadataProcessingService metadataProcessingService,
		ViewerResolveService viewerResolveService,
		CapabilityMatrixService capabilityMatrixService,
		CompressionCapabilityMatrixService compressionCapabilityMatrixService,
		PdfToolkitCapabilityMatrixService pdfToolkitCapabilityMatrixService
	) {
		this.mediaPreviewService = mediaPreviewService;
		this.mediaConversionService = mediaConversionService;
		this.imageProcessingService = imageProcessingService;
		this.compressionService = compressionService;
		this.pdfToolkitService = pdfToolkitService;
		this.officeConversionService = officeConversionService;
		this.documentPreviewService = documentPreviewService;
		this.metadataProcessingService = metadataProcessingService;
		this.viewerResolveService = viewerResolveService;
		this.capabilityMatrixService = capabilityMatrixService;
		this.compressionCapabilityMatrixService = compressionCapabilityMatrixService;
		this.pdfToolkitCapabilityMatrixService = pdfToolkitCapabilityMatrixService;
	}

	public CapabilityScope viewerCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var officeConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var metadataProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false);
		var viewerResolveAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false);

		return new CapabilityScope(
			"viewer",
			"viewer-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Backend уже умеет принять файл, создать job и собрать manifest artifact."),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Backend уже умеет собирать browser-friendly audio/video preview через ffmpeg/ffprobe."
						: "Media preview service требует доступных ffmpeg/ffprobe binaries в backend окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Backend уже умеет собирать HEIC/TIFF/RAW preview и heavy image conversion через convert/ffmpeg/potrace/libraw."
						: "Image processing service требует доступных convert/ffmpeg/potrace/raw-preview binaries в backend окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					officeConversionAvailable,
					officeConversionAvailable
						? "Backend уже умеет собирать office/pdf conversion artifacts для DOC/DOCX/RTF/ODT/XLSX/ODS/PDF/PPTX сценариев."
						: "Office conversion service пока недоступна в текущем backend окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Backend уже умеет собирать structured document payload для PDF/TXT/CSV/HTML/RTF/DOC/DOCX/ODT/XLS/XLSX/PPTX/EPUB/SQLite."
						: "Document intelligence service сейчас недоступна в текущем backend окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.METADATA_EXPORT,
					metadataProcessingAvailable,
					metadataProcessingAvailable
						? "Backend уже умеет читать image/audio metadata и собирать validated metadata export для image files."
						: "Metadata service сейчас недоступна в текущем backend окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.VIEWER_RESOLVE,
					viewerResolveAvailable,
					viewerResolveAvailable
						? "Backend viewer route теперь сводит server-assisted non-native formats к единому VIEWER_RESOLVE manifest и reuse existing artifacts."
						: "VIEWER_RESOLVE сейчас недоступен: для конкретных форматов не хватает media/image/document/metadata foundation services."
				)
			),
			List.of(
				"Viewer route теперь backend-first для всех server-assisted non-native formats: frontend запрашивает единый VIEWER_RESOLVE payload и рендерит уже готовый contract.",
				"Backend отдаёт format matrix как источник правды, а browser оставляет у себя только native rendering, state и interaction tooling."
			),
			this.capabilityMatrixService.viewerMatrix(availabilityByJobType),
			null,
			null,
			null,
			null
		);
	}

	public CapabilityScope converterCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var mediaConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var officeConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var metadataProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false);

		return new CapabilityScope(
			"converter",
			"converter-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Можно использовать как preflight перед backend-first conversion jobs и диагностикой intake stage."),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Converter backend-first route уже гонит supported scenarios через IMAGE_CONVERT jobs с preview/result artifacts."
						: "Image processing service требует доступных convert/ffmpeg/potrace/raw-preview binaries и пока не активна в текущем окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					officeConversionAvailable,
					officeConversionAvailable
						? "Converter route теперь может гнать office/pdf scenarios через OFFICE_CONVERT jobs с preview/result artifacts."
						: "Office conversion service пока недоступна и office/pdf scenarios не могут быть включены в converter matrix."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Media preview foundation уже поднята и будет переиспользована для converter/compression flows."
						: "Media preview foundation требует доступных ffmpeg/ffprobe binaries и пока не активна в текущем окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_CONVERT,
					mediaConversionAvailable,
					mediaConversionAvailable
						? "Converter route уже умеет гнать video/audio delivery-сценарии через MEDIA_CONVERT jobs с preview/result artifacts."
						: "Media conversion foundation требует доступных ffmpeg/ffprobe binaries и пока не активна в текущем окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Document preview contract уже поднят и готов к переиспользованию в PDF toolkit/editor/converter flows."
						: "Document intelligence service пока недоступна и не может переиспользоваться в converter-related сценариях."
				),
				new JobTypeCapability(
					ProcessingJobType.METADATA_EXPORT,
					metadataProcessingAvailable,
					metadataProcessingAvailable
						? "Metadata inspect/export service уже поднята и может переиспользоваться в следующих editor/converter сценариях."
						: "Metadata service пока недоступна и не может переиспользоваться в converter-related сценариях."
				)
			),
			List.of(
				"Converter route теперь backend-first: image scenarios идут через IMAGE_CONVERT, office/pdf scenarios через OFFICE_CONVERT, а video/audio scenarios через MEDIA_CONVERT; browser остаётся orchestration/preview слоем.",
				"Workspace может строить progress, retry, cancel и artifact reuse поверх единых job status и capability rules от backend."
			),
			null,
			null,
			this.capabilityMatrixService.converterMatrix(availabilityByJobType),
			null,
			null
		);
	}

	public CapabilityScope compressionCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var compressionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.FILE_COMPRESS, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var mediaConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false);
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);

		return new CapabilityScope(
			"compression",
			"compression-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Upload intake уже даёт общий foundation для size-first jobs и artifact reuse."),
				new JobTypeCapability(
					ProcessingJobType.FILE_COMPRESS,
					compressionAvailable,
					compressionAvailable
						? "Compression route уже поднимает maximum reduction, target-size и custom limit orchestration поверх existing image/media services."
						: "Compression route сейчас недоступен: для поддержанных file families не хватает backend image/media foundation."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Compression reuse'ит IMAGE_CONVERT как внутренний candidate builder для image formats."
						: "Image processing foundation сейчас недоступна и блокирует image compression scenarios."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_CONVERT,
					mediaConversionAvailable,
					mediaConversionAvailable
						? "Compression reuse'ит MEDIA_CONVERT для video/audio bitrate targeting и финальных delivery artifacts."
						: "Media conversion foundation сейчас недоступна и блокирует video/audio compression scenarios."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Media preview foundation остаётся доступной для preview-aware media reuse и связанных future slices."
						: "Media preview foundation сейчас недоступна и ограничивает media-side reuse."
				)
			),
			List.of(
				"Compression route отделён от converter: пользователь формулирует size goal и quality limits, а backend сам подбирает candidate ladder и возвращает единый compression manifest.",
				"Внутри route reuse'ит IMAGE_CONVERT и MEDIA_CONVERT как временные candidates, но наружу отдаёт только один result/preview contract без дублирования job history в UI."
			),
			null,
			this.compressionCapabilityMatrixService.compressionMatrix(availabilityByJobType),
			null,
			null,
			null
		);
	}

	public CapabilityScope pdfToolkitCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var pdfToolkitAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.PDF_TOOLKIT, false);
		var viewerResolveAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var officeConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false);

		return new CapabilityScope(
			"pdf-toolkit",
			"pdf-toolkit-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Upload intake остаётся общим foundation для PDF import, merge stack и follow-up reuse."),
				new JobTypeCapability(
					ProcessingJobType.PDF_TOOLKIT,
					pdfToolkitAvailable,
					pdfToolkitAvailable
						? "Backend уже умеет merge/split/rotate/reorder/OCR/sign/redact/protect/unlock операции как отдельный PDF_TOOLKIT route."
						: "PDF toolkit route сейчас недоступен в текущем backend окружении."
				),
				new JobTypeCapability(
					ProcessingJobType.VIEWER_RESOLVE,
					viewerResolveAvailable,
					viewerResolveAvailable
						? "PDF toolkit reuse'ит VIEWER_RESOLVE для preview и page-aware viewing flow."
						: "VIEWER_RESOLVE сейчас недоступен и PDF viewer stage не сможет загрузить unified preview contract."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Document preview foundation уже даёт page count, search layer и warnings для PDF workspace."
						: "DOCUMENT_PREVIEW сейчас недоступен и PDF summary/search stage будет ограничен."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Image convert foundation уже может заводить image-family sources в import-to-PDF flow перед pdf-toolkit."
						: "IMAGE_CONVERT сейчас недоступен и image -> PDF intake будет выключен."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					officeConversionAvailable,
					officeConversionAvailable
						? "Office convert foundation уже может заводить office/PDF-compatible document flows в pdf-toolkit workspace."
						: "OFFICE_CONVERT сейчас недоступен и office -> PDF intake будет выключен."
				)
			),
			List.of(
				"PDF toolkit теперь отдельный backend-first route: page-aware операции, OCR, redaction и protection живут в PDF_TOOLKIT job вместо browser-side mutation.",
				"Workspace reuse'ит converter routes для import-to-PDF, VIEWER_RESOLVE для preview и upload/job/artifact foundation для follow-up editing flows."
			),
			null,
			null,
			null,
			this.pdfToolkitCapabilityMatrixService.pdfToolkitMatrix(availabilityByJobType, this.pdfToolkitService.isOcrAvailable()),
			null
		);
	}

	public CapabilityScope platformCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		return new CapabilityScope(
			"platform",
			"processing-platform",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Upload intake уже даёт общий foundation для новых thin features поверх processing platform."),
				new JobTypeCapability(
					ProcessingJobType.FILE_COMPRESS,
					availabilityByJobType.getOrDefault(ProcessingJobType.FILE_COMPRESS, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.FILE_COMPRESS, false)
						? "Dedicated compression orchestration уже поднята и закрывает size-first product route поверх image/media services."
						: "Compression orchestration ещё не поднята как отдельный product route поверх existing processing foundation."
				),
				new JobTypeCapability(
					ProcessingJobType.PDF_TOOLKIT,
					availabilityByJobType.getOrDefault(ProcessingJobType.PDF_TOOLKIT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.PDF_TOOLKIT, false)
						? "Dedicated PDF toolkit route уже поднят и закрывает page editing, OCR, redact и protection flows поверх processing platform."
						: "PDF toolkit route ещё не поднят как отдельный product route поверх document/viewer foundation."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false)
						? "Media preview foundation уже готова к reuse в compression, batch и future delivery flows."
						: "Для reuse media foundation нужны доступные ffmpeg/ffprobe binaries."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false)
						? "Media conversion foundation уже готова к reuse в converter, compression и future delivery/export flows."
						: "Для reuse media conversion foundation нужны доступные ffmpeg/ffprobe binaries."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false)
						? "Image processing foundation уже готова к reuse в compression, OCR, PDF toolkit и batch conversion."
						: "Для reuse imaging foundation нужны доступные convert/ffmpeg/potrace/raw-preview binaries."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false)
						? "Office conversion foundation уже готова к reuse в converter, PDF toolkit и future delivery/export flows."
						: "Office conversion foundation сейчас недоступна и блокирует document-roundtrip reuse."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false)
						? "Document intelligence foundation уже готова к reuse в PDF toolkit, editor, OCR и office conversion."
						: "Document intelligence foundation сейчас недоступна и блокирует document-centric reuse."
				),
				new JobTypeCapability(
					ProcessingJobType.METADATA_EXPORT,
					availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false)
						? "Metadata foundation уже готова к reuse в editor/export и quality-aware processing flows."
						: "Metadata foundation сейчас недоступна и ограничивает export/validation reuse."
				),
				new JobTypeCapability(
					ProcessingJobType.VIEWER_RESOLVE,
					availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false)
						? "Unified viewer route уже даёт reusable file-resolve contract для новых product modules."
						: "VIEWER_RESOLVE сейчас недоступен и часть future module entry points останется fragmented."
				)
			),
			List.of(
				"Финальный platform-срез закрывается не новым browser runtime, а reusable processing platform contract для следующих roadmap-модулей.",
				"Новые модули должны стартовать как thin features поверх upload/job/artifact/capability foundation и добавлять только свою product-specific orchestration."
			),
			null,
			null,
			null,
			null,
			this.capabilityMatrixService.platformMatrix(availabilityByJobType)
		);
	}

	private Map<ProcessingJobType, Boolean> availabilityByJobType() {
		// Backend matrix вычисляется от живых processing services, чтобы frontend видел
		// не абстрактный roadmap, а реальную доступность конкретных format/scenario routes.
		var availabilityByJobType = new LinkedHashMap<ProcessingJobType, Boolean>();
		availabilityByJobType.put(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true);
		availabilityByJobType.put(ProcessingJobType.FILE_COMPRESS, this.compressionService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.PDF_TOOLKIT, this.pdfToolkitService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.MEDIA_PREVIEW, this.mediaPreviewService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.MEDIA_CONVERT, this.mediaConversionService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.IMAGE_CONVERT, this.imageProcessingService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.OFFICE_CONVERT, this.officeConversionService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.DOCUMENT_PREVIEW, this.documentPreviewService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.METADATA_EXPORT, this.metadataProcessingService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.VIEWER_RESOLVE, this.viewerResolveService.isAvailable());
		return availabilityByJobType;
	}

	public record CapabilityScope(
		String scope,
		String phase,
		List<JobTypeCapability> jobTypes,
		List<String> notes,
		CapabilityMatrixPayloads.ViewerCapabilityMatrix viewerMatrix,
		CapabilityMatrixPayloads.CompressionCapabilityMatrix compressionMatrix,
		CapabilityMatrixPayloads.ConverterCapabilityMatrix converterMatrix,
		CapabilityMatrixPayloads.PdfToolkitCapabilityMatrix pdfToolkitMatrix,
		CapabilityMatrixPayloads.PlatformCapabilityMatrix platformMatrix
	) {
	}

	public record JobTypeCapability(
		ProcessingJobType jobType,
		boolean implemented,
		String detail
	) {
	}

}
