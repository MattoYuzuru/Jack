package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CapabilityCatalogService {

	private final MediaPreviewService mediaPreviewService;
	private final ImageProcessingService imageProcessingService;

	public CapabilityCatalogService(
		MediaPreviewService mediaPreviewService,
		ImageProcessingService imageProcessingService
	) {
		this.mediaPreviewService = mediaPreviewService;
		this.imageProcessingService = imageProcessingService;
	}

	public CapabilityScope viewerCapabilities() {
		var mediaPreviewAvailable = this.mediaPreviewService.isAvailable();
		var imageProcessingAvailable = this.imageProcessingService.isAvailable();

		return new CapabilityScope(
			"viewer",
			"imaging-foundation",
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
				new JobTypeCapability(ProcessingJobType.DOCUMENT_PREVIEW, false, "Document intelligence ещё не перенесён с фронтенда на backend."),
				new JobTypeCapability(ProcessingJobType.METADATA_EXPORT, false, "Metadata read/write пока остаётся на frontend runtime.")
			),
			List.of(
				"Viewer уже использует server-assisted preview для legacy media и heavy image decode.",
				"Document parsing и metadata mutation всё ещё ждут следующих backend phase."
			)
		);
	}

	public CapabilityScope converterCapabilities() {
		var mediaPreviewAvailable = this.mediaPreviewService.isAvailable();
		var imageProcessingAvailable = this.imageProcessingService.isAvailable();

		return new CapabilityScope(
			"converter",
			"imaging-foundation",
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
				new JobTypeCapability(ProcessingJobType.DOCUMENT_PREVIEW, false, "Document extract/preview contract пока живёт во frontend runtime.")
			),
			List.of(
				"Converter route теперь hybrid: browser-native fast paths остаются локальными, heavy imaging идёт через backend job pipeline.",
				"Следующая большая backend цель после imaging — document intelligence и metadata mutation."
			)
		);
	}

	public record CapabilityScope(
		String scope,
		String phase,
		List<JobTypeCapability> jobTypes,
		List<String> notes
	) {
	}

	public record JobTypeCapability(
		ProcessingJobType jobType,
		boolean implemented,
		String detail
	) {
	}

}
