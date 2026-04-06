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
	private final ImageProcessingService imageProcessingService;
	private final DocumentPreviewService documentPreviewService;
	private final MetadataProcessingService metadataProcessingService;
	private final ViewerResolveService viewerResolveService;
	private final CapabilityMatrixService capabilityMatrixService;

	public CapabilityCatalogService(
		MediaPreviewService mediaPreviewService,
		ImageProcessingService imageProcessingService,
		DocumentPreviewService documentPreviewService,
		MetadataProcessingService metadataProcessingService,
		ViewerResolveService viewerResolveService,
		CapabilityMatrixService capabilityMatrixService
	) {
		this.mediaPreviewService = mediaPreviewService;
		this.imageProcessingService = imageProcessingService;
		this.documentPreviewService = documentPreviewService;
		this.metadataProcessingService = metadataProcessingService;
		this.viewerResolveService = viewerResolveService;
		this.capabilityMatrixService = capabilityMatrixService;
	}

	public CapabilityScope viewerCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
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
			null
		);
	}

	public CapabilityScope converterCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
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
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Media preview foundation уже поднята и будет переиспользована для converter/compression flows."
						: "Media preview foundation требует доступных ffmpeg/ffprobe binaries и пока не активна в текущем окружении."
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
				"Converter route теперь backend-first: любой supported сценарий идёт через backend job/artifact contract, а browser остаётся orchestration/preview слоем.",
				"Workspace может строить progress, retry, cancel и artifact reuse поверх единых job status и capability rules от backend."
			),
			null,
			this.capabilityMatrixService.converterMatrix(availabilityByJobType),
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
					ProcessingJobType.MEDIA_PREVIEW,
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false)
						? "Media preview foundation уже готова к reuse в compression, batch и future delivery flows."
						: "Для reuse media foundation нужны доступные ffmpeg/ffprobe binaries."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false)
						? "Image processing foundation уже готова к reuse в compression, OCR, PDF toolkit и batch conversion."
						: "Для reuse imaging foundation нужны доступные convert/ffmpeg/potrace/raw-preview binaries."
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
			this.capabilityMatrixService.platformMatrix(availabilityByJobType)
		);
	}

	private Map<ProcessingJobType, Boolean> availabilityByJobType() {
		// Backend matrix вычисляется от живых processing services, чтобы frontend видел
		// не абстрактный roadmap, а реальную доступность конкретных format/scenario routes.
		var availabilityByJobType = new LinkedHashMap<ProcessingJobType, Boolean>();
		availabilityByJobType.put(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true);
		availabilityByJobType.put(ProcessingJobType.MEDIA_PREVIEW, this.mediaPreviewService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.IMAGE_CONVERT, this.imageProcessingService.isAvailable());
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
		CapabilityMatrixPayloads.ConverterCapabilityMatrix converterMatrix,
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
