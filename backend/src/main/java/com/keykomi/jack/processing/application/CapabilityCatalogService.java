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
	private final CapabilityMatrixService capabilityMatrixService;

	public CapabilityCatalogService(
		MediaPreviewService mediaPreviewService,
		ImageProcessingService imageProcessingService,
		DocumentPreviewService documentPreviewService,
		MetadataProcessingService metadataProcessingService,
		CapabilityMatrixService capabilityMatrixService
	) {
		this.mediaPreviewService = mediaPreviewService;
		this.imageProcessingService = imageProcessingService;
		this.documentPreviewService = documentPreviewService;
		this.metadataProcessingService = metadataProcessingService;
		this.capabilityMatrixService = capabilityMatrixService;
	}

	public CapabilityScope viewerCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var metadataProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false);

		return new CapabilityScope(
			"viewer",
			"server-capability-matrix",
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
				)
			),
			List.of(
				"Viewer уже использует server-assisted preview для legacy media, heavy imaging, document intelligence и metadata operations.",
				"Backend теперь отдаёт format matrix как источник правды: frontend больше не держит локальный registry как единственный canonical source."
			),
			this.capabilityMatrixService.viewerMatrix(availabilityByJobType),
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
			"server-capability-matrix",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Можно использовать как preflight перед будущими heavy conversion jobs."),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Heavy imaging scenarios уже уходят в backend IMAGE_CONVERT jobs с preview/result artifacts."
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
				"Converter route теперь hybrid: browser-native fast paths остаются локальными, heavy imaging идёт через backend job pipeline.",
				"Backend теперь отдаёт source/target/scenario/preset matrix и убирает расхождение между UI и processing rules."
			),
			null,
			this.capabilityMatrixService.converterMatrix(availabilityByJobType)
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
		return availabilityByJobType;
	}

	public record CapabilityScope(
		String scope,
		String phase,
		List<JobTypeCapability> jobTypes,
		List<String> notes,
		CapabilityMatrixPayloads.ViewerCapabilityMatrix viewerMatrix,
		CapabilityMatrixPayloads.ConverterCapabilityMatrix converterMatrix
	) {
	}

	public record JobTypeCapability(
		ProcessingJobType jobType,
		boolean implemented,
		String detail
	) {
	}

}
