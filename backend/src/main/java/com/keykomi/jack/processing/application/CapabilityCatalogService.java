package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CapabilityCatalogService {

	public CapabilityScope viewerCapabilities() {
		return new CapabilityScope(
			"viewer",
			"foundation",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Backend уже умеет принять файл, создать job и собрать manifest artifact."),
				new JobTypeCapability(ProcessingJobType.MEDIA_PREVIEW, false, "Следующий backend-срез для legacy audio/video preview поверх ffmpeg/ffprobe."),
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
		return new CapabilityScope(
			"converter",
			"foundation",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Можно использовать как preflight перед будущими heavy conversion jobs."),
				new JobTypeCapability(ProcessingJobType.IMAGE_CONVERT, false, "Image convert service появится после foundation и ffmpeg/media base."),
				new JobTypeCapability(ProcessingJobType.MEDIA_PREVIEW, false, "Media processing foundation ещё не заведена в backend."),
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
