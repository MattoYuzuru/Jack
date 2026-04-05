package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CapabilityCatalogService {

	private final MediaPreviewService mediaPreviewService;

	public CapabilityCatalogService(MediaPreviewService mediaPreviewService) {
		this.mediaPreviewService = mediaPreviewService;
	}

	public CapabilityScope viewerCapabilities() {
		var mediaPreviewAvailable = this.mediaPreviewService.isAvailable();

		return new CapabilityScope(
			"viewer",
			"media-foundation",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Backend уже умеет принять файл, создать job и собрать manifest artifact."),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Backend уже умеет собирать browser-friendly audio/video preview через ffmpeg/ffprobe."
						: "Media preview service требует доступных ffmpeg/ffprobe binaries в backend окружении."
				),
				new JobTypeCapability(ProcessingJobType.DOCUMENT_PREVIEW, false, "Document intelligence ещё не перенесён с фронтенда на backend."),
				new JobTypeCapability(ProcessingJobType.METADATA_EXPORT, false, "Metadata read/write пока остаётся на frontend runtime.")
			),
			List.of(
				"Viewer ещё не переведён на backend-first resolve pipeline.",
				"Foundation уже даёт upload/job/artifact flow без browser-side heavy processing."
			)
		);
	}

	public CapabilityScope converterCapabilities() {
		var mediaPreviewAvailable = this.mediaPreviewService.isAvailable();

		return new CapabilityScope(
			"converter",
			"media-foundation",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Можно использовать как preflight перед будущими heavy conversion jobs."),
				new JobTypeCapability(ProcessingJobType.IMAGE_CONVERT, false, "Image convert service появится после foundation и ffmpeg/media base."),
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
				"Converter route пока остаётся browser-first.",
				"Backend foundation уже готовит capability matrix и artifact workflow для server-assisted flip."
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
