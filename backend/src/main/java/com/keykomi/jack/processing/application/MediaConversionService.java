package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.MediaConversionRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaConversionService {

	private static final Set<String> VIDEO_SOURCE_EXTENSIONS = Set.of("mp4", "mov", "mkv", "avi", "webm");
	private static final Set<String> AUDIO_SOURCE_EXTENSIONS = Set.of("mp3", "wav", "aac", "m4a", "flac");
	private static final Set<String> VIDEO_TARGET_EXTENSIONS = Set.of("mp4", "webm");
	private static final Set<String> AUDIO_TARGET_EXTENSIONS = Set.of("mp3", "wav", "aac", "m4a", "flac");
	private static final Set<String> LOSSY_AUDIO_TARGET_EXTENSIONS = Set.of("mp3", "aac", "m4a");

	private final ProcessingProperties processingProperties;
	private final ArtifactStorageService artifactStorageService;
	private final ObjectMapper objectMapper;

	public MediaConversionService(
		ProcessingProperties processingProperties,
		ArtifactStorageService artifactStorageService,
		ObjectMapper objectMapper
	) {
		this.processingProperties = processingProperties;
		this.artifactStorageService = artifactStorageService;
		this.objectMapper = objectMapper;
	}

	public boolean isAvailable() {
		return resolveExecutable(this.processingProperties.getFfmpegExecutable()).isPresent()
			&& resolveExecutable(this.processingProperties.getFfprobeExecutable()).isPresent();
	}

	public MediaConversionResult process(UUID jobId, StoredUpload upload, MediaConversionRequest request) {
		var family = ProcessingFileFamilyResolver.detectFamily(upload);
		if (!"media".equals(family) && !"audio".equals(family)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MEDIA_CONVERT job принимает только audio/video uploads.");
		}

		ensureAvailability();

		var targetExtension = normalizeExtension(request.targetExtension());
		if (targetExtension.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MEDIA_CONVERT требует targetExtension.");
		}

		Path workingDirectory = null;
		try {
			workingDirectory = Files.createTempDirectory(this.processingProperties.getStorageRoot(), "media-convert-");
			var output = convert(upload, request, targetExtension, family, workingDirectory);
			var artifacts = storeArtifacts(jobId, upload, targetExtension, output);
			return new MediaConversionResult(artifacts, output.runtimeLabel(), output.warnings());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить media conversion workspace.", exception);
		}
		finally {
			deleteRecursively(workingDirectory);
		}
	}

	private MediaConversionOutput convert(
		StoredUpload upload,
		MediaConversionRequest request,
		String targetExtension,
		String family,
		Path workingDirectory
	) {
		var sourceExtension = normalizeExtension(upload.extension());
		var sourceProbe = probeMedia(upload.storagePath(), family, workingDirectory);

		if ("media".equals(family)) {
			return convertVideoSource(upload, request, sourceExtension, targetExtension, sourceProbe, workingDirectory);
		}
		return convertAudioSource(upload, request, sourceExtension, targetExtension, sourceProbe, workingDirectory);
	}

	private MediaConversionOutput convertVideoSource(
		StoredUpload upload,
		MediaConversionRequest request,
		String sourceExtension,
		String targetExtension,
		MediaProbe sourceProbe,
		Path workingDirectory
	) {
		if (!VIDEO_SOURCE_EXTENSIONS.contains(sourceExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"MEDIA_CONVERT пока не поддерживает video source %s.".formatted(sourceExtension)
			);
		}

		if (VIDEO_TARGET_EXTENSIONS.contains(targetExtension)) {
			return convertVideoContainer(upload, request, targetExtension, sourceProbe, workingDirectory);
		}
		if ("gif".equals(targetExtension)) {
			return convertVideoToGif(upload, request, sourceProbe, workingDirectory);
		}
		if (Set.of("mp3", "wav", "aac", "m4a").contains(targetExtension)) {
			return convertToAudio(upload, request, targetExtension, sourceProbe, workingDirectory, true);
		}

		throw new ResponseStatusException(
			HttpStatus.BAD_REQUEST,
			"MEDIA_CONVERT пока не поддерживает сценарий %s -> %s.".formatted(sourceExtension, targetExtension)
		);
	}

	private MediaConversionOutput convertAudioSource(
		StoredUpload upload,
		MediaConversionRequest request,
		String sourceExtension,
		String targetExtension,
		MediaProbe sourceProbe,
		Path workingDirectory
	) {
		if (!AUDIO_SOURCE_EXTENSIONS.contains(sourceExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"MEDIA_CONVERT пока не поддерживает audio source %s.".formatted(sourceExtension)
			);
		}

		if (!AUDIO_TARGET_EXTENSIONS.contains(targetExtension)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"MEDIA_CONVERT пока не поддерживает audio сценарий %s -> %s.".formatted(sourceExtension, targetExtension)
			);
		}

		return convertToAudio(upload, request, targetExtension, sourceProbe, workingDirectory, false);
	}

	private MediaConversionOutput convertVideoContainer(
		StoredUpload upload,
		MediaConversionRequest request,
		String targetExtension,
		MediaProbe sourceProbe,
		Path workingDirectory
	) {
		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var resultPath = workingDirectory.resolve(resultFileName);
		execute(buildVideoCommand(upload, request, targetExtension, sourceProbe, resultPath), workingDirectory, timeout());
		ensureArtifact(resultPath, "media video transcode");

		var resultProbe = probeMedia(resultPath, "media", workingDirectory);
		var preview = preparePreviewArtifact(upload, resultPath, targetExtension, workingDirectory, resultProbe);
		var warnings = new ArrayList<String>();
		appendVideoWarnings(warnings, sourceProbe, request, resultProbe);
		warnings.addAll(preview.warnings());

		var sourceAdapterLabel = "FFmpeg video intake";
		var targetAdapterLabel = resolveVideoTargetLabel(targetExtension, request.videoCodec());

		return new MediaConversionOutput(
			resultPath,
			resultFileName,
			resolveMediaType(targetExtension),
			preview.path(),
			preview.fileName(),
			preview.mediaType(),
			preview.previewKind(),
			buildSourceFacts(upload, sourceProbe),
			buildResultFacts(targetExtension, resultProbe),
			sourceAdapterLabel,
			targetAdapterLabel,
			sourceAdapterLabel + " -> " + targetAdapterLabel,
			warnings
		);
	}

	private MediaConversionOutput convertVideoToGif(
		StoredUpload upload,
		MediaConversionRequest request,
		MediaProbe sourceProbe,
		Path workingDirectory
	) {
		var resultFileName = replaceExtension(upload.originalFileName(), "gif");
		var resultPath = workingDirectory.resolve(resultFileName);
		execute(buildGifCommand(upload, request, sourceProbe, resultPath), workingDirectory, timeout());
		ensureArtifact(resultPath, "animated gif export");

		var resultProbe = probeMedia(resultPath, "media", workingDirectory);
		var warnings = new ArrayList<String>();
		warnings.add("GIF export убирает аудио и снижает цветовую глубину до palette-based animation.");
		appendResizeAndFpsWarnings(warnings, sourceProbe, request, resultProbe);

		var sourceAdapterLabel = "FFmpeg video intake";
		var targetAdapterLabel = "Animated GIF export";

		return new MediaConversionOutput(
			resultPath,
			resultFileName,
			"image/gif",
			resultPath,
			derivedPreviewFileName(upload.originalFileName(), "gif"),
			"image/gif",
			"image",
			buildSourceFacts(upload, sourceProbe),
			buildResultFacts("gif", resultProbe),
			sourceAdapterLabel,
			targetAdapterLabel,
			sourceAdapterLabel + " -> " + targetAdapterLabel,
			warnings
		);
	}

	private MediaConversionOutput convertToAudio(
		StoredUpload upload,
		MediaConversionRequest request,
		String targetExtension,
		MediaProbe sourceProbe,
		Path workingDirectory,
		boolean discardVideo
	) {
		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var resultPath = workingDirectory.resolve(resultFileName);
		execute(buildAudioCommand(upload, request, targetExtension, resultPath), workingDirectory, timeout());
		ensureArtifact(resultPath, "audio export");

		var resultProbe = probeMedia(resultPath, "audio", workingDirectory);
		var preview = preparePreviewArtifact(upload, resultPath, targetExtension, workingDirectory, resultProbe);
		var warnings = new ArrayList<String>();
		if (discardVideo) {
			warnings.add("Видео-дорожка отброшена: audio export сохраняет только звуковой поток.");
		}
		if (LOSSY_AUDIO_TARGET_EXTENSIONS.contains(targetExtension)) {
			warnings.add("Lossy audio export меняет контейнер отдельно от bitrate-профиля: итоговое качество зависит от выбранного target bitrate.");
		}
		warnings.addAll(preview.warnings());

		var sourceAdapterLabel = "FFmpeg audio intake";
		var targetAdapterLabel = resolveAudioTargetLabel(targetExtension);

		return new MediaConversionOutput(
			resultPath,
			resultFileName,
			resolveMediaType(targetExtension),
			preview.path(),
			preview.fileName(),
			preview.mediaType(),
			"media",
			buildSourceFacts(upload, sourceProbe),
			buildResultFacts(targetExtension, resultProbe),
			sourceAdapterLabel,
			targetAdapterLabel,
			sourceAdapterLabel + " -> " + targetAdapterLabel,
			warnings
		);
	}

	private PreviewArtifact preparePreviewArtifact(
		StoredUpload upload,
		Path resultPath,
		String targetExtension,
		Path workingDirectory,
		MediaProbe resultProbe
	) {
		if ("gif".equals(targetExtension)) {
			return new PreviewArtifact(
				resultPath,
				derivedPreviewFileName(upload.originalFileName(), "gif"),
				"image/gif",
				"image",
				List.of()
			);
		}

		if (Set.of("mp4", "webm", "mp3", "wav").contains(targetExtension)) {
			return new PreviewArtifact(
				resultPath,
				derivedPreviewFileName(upload.originalFileName(), targetExtension),
				resolveMediaType(targetExtension),
				"media",
				List.of()
			);
		}

		var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "mp3"));
		execute(
			List.of(
				ffmpegExecutable(),
				"-v",
				"error",
				"-y",
				"-i",
				resultPath.toString(),
				"-map",
				"0:a:0",
				"-vn",
				"-c:a",
				"libmp3lame",
				"-b:a",
				requestAudioPreviewBitrate(resultProbe) + "k",
				previewPath.toString()
			),
			workingDirectory,
			timeout()
		);
		ensureArtifact(previewPath, "audio preview fallback");

		return new PreviewArtifact(
			previewPath,
			derivedPreviewFileName(upload.originalFileName(), "mp3"),
			"audio/mpeg",
			"media",
			List.of("Для удобного прослушивания дополнительно подготовлен MP3-предпросмотр.")
		);
	}

	private int requestAudioPreviewBitrate(MediaProbe resultProbe) {
		if (resultProbe.audioBitrateKbps() != null) {
			return Math.max(96, Math.min(resultProbe.audioBitrateKbps(), 192));
		}
		return 160;
	}

	private List<String> buildVideoCommand(
		StoredUpload upload,
		MediaConversionRequest request,
		String targetExtension,
		MediaProbe sourceProbe,
		Path outputPath
	) {
		var command = new ArrayList<>(List.of(
			ffmpegExecutable(),
			"-v",
			"error",
			"-y",
			"-i",
			upload.storagePath().toString(),
			"-map",
			"0:v:0",
			"-map",
			"0:a?",
			"-sn"
		));
		appendVideoFilter(command, sourceProbe, request);

		if ("mp4".equals(targetExtension)) {
			var videoCodec = normalizeCodec(request.videoCodec());
			if ("av1".equals(videoCodec)) {
				command.addAll(List.of("-c:v", "libaom-av1", "-row-mt", "1", "-cpu-used", "8"));
				if (request.videoBitrateKbps() == null) {
					command.addAll(List.of("-crf", "34", "-b:v", "0"));
				}
			}
			else {
				command.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-movflags", "+faststart"));
				if (request.videoBitrateKbps() == null) {
					command.addAll(List.of("-crf", "23"));
				}
			}
			if (request.videoBitrateKbps() != null) {
				command.addAll(List.of("-b:v", resolveVideoBitrate(request.videoBitrateKbps()) + "k"));
			}
			command.addAll(List.of("-c:a", "aac", "-b:a", resolveAudioBitrate(request.audioBitrateKbps()) + "k"));
		}
		else if ("webm".equals(targetExtension)) {
			var videoCodec = normalizeCodec(request.videoCodec());
			if ("av1".equals(videoCodec)) {
				command.addAll(List.of("-c:v", "libaom-av1", "-row-mt", "1", "-cpu-used", "8"));
				if (request.videoBitrateKbps() == null) {
					command.addAll(List.of("-crf", "36", "-b:v", "0"));
				}
			}
			else {
				command.addAll(List.of("-c:v", "libvpx-vp9", "-row-mt", "1", "-deadline", "good", "-cpu-used", "4"));
				if (request.videoBitrateKbps() == null) {
					command.addAll(List.of("-b:v", "0", "-crf", "33"));
				}
			}
			if (request.videoBitrateKbps() != null) {
				command.addAll(List.of("-b:v", resolveVideoBitrate(request.videoBitrateKbps()) + "k"));
			}
			command.addAll(List.of("-c:a", "libopus", "-b:a", resolveAudioBitrate(request.audioBitrateKbps()) + "k"));
		}
		else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video transcode не поддерживает target %s.".formatted(targetExtension));
		}

		command.add(outputPath.toString());
		return command;
	}

	private void appendVideoFilter(
		List<String> command,
		MediaProbe sourceProbe,
		MediaConversionRequest request
	) {
		var filters = new ArrayList<String>();
		var targetDimensions = resolveTargetDimensions(sourceProbe, request.maxWidth(), request.maxHeight());

		if (targetDimensions != null) {
			filters.add("scale=%d:%d".formatted(targetDimensions.width(), targetDimensions.height()));
		}
		else {
			// Видео-энкодеры любят чётные размеры кадра. Даже без downscale нормализуем canvas,
			// чтобы transcode не падал на odd-width / odd-height источниках.
			filters.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
		}

		if (request.targetFps() != null) {
			filters.add("fps=" + request.targetFps());
		}

		if (!filters.isEmpty()) {
			command.addAll(List.of("-vf", String.join(",", filters)));
		}
	}

	private List<String> buildGifCommand(
		StoredUpload upload,
		MediaConversionRequest request,
		MediaProbe sourceProbe,
		Path outputPath
	) {
		var framesPerSecond = request.targetFps() != null ? request.targetFps() : 12;
		var targetDimensions = resolveTargetDimensions(sourceProbe, request.maxWidth(), request.maxHeight());
		var scaleFilter = targetDimensions != null
			? "scale=%d:%d:flags=lanczos".formatted(targetDimensions.width(), targetDimensions.height())
			: "scale=trunc(iw/2)*2:trunc(ih/2)*2:flags=lanczos";
		var filter = "fps=%s,%s,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse".formatted(framesPerSecond, scaleFilter);

		return List.of(
			ffmpegExecutable(),
			"-v",
			"error",
			"-y",
			"-i",
			upload.storagePath().toString(),
			"-map",
			"0:v:0",
			"-vf",
			filter,
			"-loop",
			"0",
			outputPath.toString()
		);
	}

	private List<String> buildAudioCommand(
		StoredUpload upload,
		MediaConversionRequest request,
		String targetExtension,
		Path outputPath
	) {
		var command = new ArrayList<>(List.of(
			ffmpegExecutable(),
			"-v",
			"error",
			"-y",
			"-i",
			upload.storagePath().toString(),
			"-map",
			"0:a:0",
			"-vn"
		));

		switch (targetExtension) {
			case "mp3" -> command.addAll(List.of("-c:a", "libmp3lame", "-b:a", resolveAudioBitrate(request.audioBitrateKbps()) + "k"));
			case "wav" -> command.addAll(List.of("-c:a", "pcm_s16le", "-ar", "44100", "-ac", "2"));
			case "aac" -> command.addAll(List.of("-c:a", "aac", "-b:a", resolveAudioBitrate(request.audioBitrateKbps()) + "k"));
			case "m4a" -> command.addAll(List.of("-c:a", "aac", "-b:a", resolveAudioBitrate(request.audioBitrateKbps()) + "k", "-movflags", "+faststart"));
			case "flac" -> command.addAll(List.of("-c:a", "flac"));
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Audio export не поддерживает target %s.".formatted(targetExtension)
			);
		}

		command.add(outputPath.toString());
		return command;
	}

	private MediaProbe probeMedia(Path sourcePath, String family, Path workingDirectory) {
		var command = new ArrayList<>(List.of(
			ffprobeExecutable(),
			"-v",
			"error",
			"-show_entries",
			"format=format_name,duration,bit_rate:stream=codec_type,codec_name,width,height,avg_frame_rate,r_frame_rate,bit_rate,sample_rate,channels",
			"-of",
			"json",
			sourcePath.toString()
		));

		var output = execute(command, workingDirectory, timeout());

		try {
			var root = this.objectMapper.readTree(output.stdout());
			var format = root.path("format");
			JsonNode videoStream = null;
			JsonNode audioStream = null;

			for (JsonNode stream : root.path("streams")) {
				var codecType = normalizeNullable(stream.path("codec_type").asText()).orElse(null);
				if ("video".equals(codecType) && videoStream == null) {
					videoStream = stream;
				}
				if ("audio".equals(codecType) && audioStream == null) {
					audioStream = stream;
				}
			}

			return new MediaProbe(
				normalizeNullable(format.path("format_name").asText()).orElse(null),
				parseDouble(format.path("duration").asText()),
				videoStream != null,
				audioStream != null,
				videoStream == null ? null : normalizeNullable(videoStream.path("codec_name").asText()).orElse(null),
				audioStream == null ? null : normalizeNullable(audioStream.path("codec_name").asText()).orElse(null),
				videoStream == null || !videoStream.path("width").isNumber() ? null : videoStream.path("width").asInt(),
				videoStream == null || !videoStream.path("height").isNumber() ? null : videoStream.path("height").asInt(),
				videoStream == null ? null : parseFramesPerSecond(videoStream),
				audioStream == null ? null : parseInteger(audioStream.path("sample_rate").asText()),
				audioStream == null || !audioStream.path("channels").isNumber() ? null : audioStream.path("channels").asInt(),
				videoStream == null ? null : parseKbps(videoStream.path("bit_rate").asText()),
				audioStream == null ? null : parseKbps(audioStream.path("bit_rate").asText()),
				parseKbps(format.path("bit_rate").asText())
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ffprobe вернул неподдерживаемый JSON probe output.", exception);
		}
	}

	private List<StoredArtifact> storeArtifacts(
		UUID jobId,
		StoredUpload upload,
		String targetExtension,
		MediaConversionOutput output
	) {
		var manifest = new MediaConvertManifest(
			upload.id(),
			upload.originalFileName(),
			normalizeExtension(upload.extension()),
			targetExtension,
			output.resultMediaType(),
			output.previewMediaType(),
			output.previewKind(),
			output.sourceAdapterLabel(),
			output.targetAdapterLabel(),
			output.runtimeLabel(),
			output.sourceFacts(),
			output.resultFacts(),
			output.warnings()
		);

		return List.of(
			this.artifactStorageService.storeJsonArtifact(jobId, "media-convert-manifest", "media-convert-manifest.json", manifest),
			this.artifactStorageService.storeFileArtifact(jobId, "media-convert-binary", output.resultFileName(), output.resultMediaType(), output.resultPath()),
			this.artifactStorageService.storeFileArtifact(jobId, "media-convert-preview", output.previewFileName(), output.previewMediaType(), output.previewPath())
		);
	}

	private List<DocumentPreviewPayload.DocumentFact> buildSourceFacts(StoredUpload upload, MediaProbe probe) {
		var facts = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		facts.add(new DocumentPreviewPayload.DocumentFact("Контейнер", containerLabel(upload.extension(), probe.containerName())));

		if (probe.hasVideo()) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Video codec", codecLabel(probe.videoCodec())));
			facts.add(new DocumentPreviewPayload.DocumentFact("Resolution", sizeLabel(probe.width(), probe.height())));
			if (probe.framesPerSecond() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("FPS", fpsLabel(probe.framesPerSecond())));
			}
			if (probe.videoBitrateKbps() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Video bitrate", bitrateLabel(probe.videoBitrateKbps())));
			}
		}

		if (probe.hasAudio()) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Audio codec", codecLabel(probe.audioCodec())));
			if (probe.sampleRate() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Sample rate", probe.sampleRate() + " Hz"));
			}
			if (probe.channelCount() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Channels", probe.channelCount() + " ch"));
			}
			if (probe.audioBitrateKbps() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Audio bitrate", bitrateLabel(probe.audioBitrateKbps())));
			}
		}

		if (probe.durationSeconds() != null) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Duration", durationLabel(probe.durationSeconds())));
		}
		return List.copyOf(facts);
	}

	private List<DocumentPreviewPayload.DocumentFact> buildResultFacts(String targetExtension, MediaProbe probe) {
		var facts = new ArrayList<DocumentPreviewPayload.DocumentFact>();
		facts.add(new DocumentPreviewPayload.DocumentFact("Контейнер", targetExtension.toUpperCase(Locale.ROOT)));

		if (probe.hasVideo()) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Video codec", codecLabel(probe.videoCodec())));
			facts.add(new DocumentPreviewPayload.DocumentFact("Resolution", sizeLabel(probe.width(), probe.height())));
			if (probe.framesPerSecond() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("FPS", fpsLabel(probe.framesPerSecond())));
			}
			if (probe.videoBitrateKbps() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Video bitrate", bitrateLabel(probe.videoBitrateKbps())));
			}
		}

		if (probe.hasAudio()) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Audio codec", codecLabel(probe.audioCodec())));
			if (probe.sampleRate() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Sample rate", probe.sampleRate() + " Hz"));
			}
			if (probe.channelCount() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Channels", probe.channelCount() + " ch"));
			}
			if (probe.audioBitrateKbps() != null) {
				facts.add(new DocumentPreviewPayload.DocumentFact("Audio bitrate", bitrateLabel(probe.audioBitrateKbps())));
			}
		}

		if (probe.durationSeconds() != null) {
			facts.add(new DocumentPreviewPayload.DocumentFact("Duration", durationLabel(probe.durationSeconds())));
		}
		return List.copyOf(facts);
	}

	private void appendVideoWarnings(
		List<String> warnings,
		MediaProbe sourceProbe,
		MediaConversionRequest request,
		MediaProbe resultProbe
	) {
		appendResizeAndFpsWarnings(warnings, sourceProbe, request, resultProbe);

		var targetCodec = normalizeCodec(request.videoCodec());
		if (sourceProbe.videoCodec() != null && targetCodec != null && !targetCodec.equals(normalizeCodec(sourceProbe.videoCodec()))) {
			warnings.add("Video codec изменён отдельно от контейнера: source %s -> target %s.".formatted(codecLabel(sourceProbe.videoCodec()), codecLabel(targetCodec)));
		}
		if (request.videoBitrateKbps() != null) {
			warnings.add("Target bitrate зафиксирован отдельно от контейнера/codec и ограничен до %s.".formatted(bitrateLabel(request.videoBitrateKbps())));
		}
		if (request.audioBitrateKbps() != null && resultProbe.hasAudio()) {
			warnings.add("Audio bitrate также управляется отдельно и собран с ограничением %s.".formatted(bitrateLabel(request.audioBitrateKbps())));
		}
	}

	private void appendResizeAndFpsWarnings(
		List<String> warnings,
		MediaProbe sourceProbe,
		MediaConversionRequest request,
		MediaProbe resultProbe
	) {
		if (
			sourceProbe.width() != null &&
			sourceProbe.height() != null &&
			resultProbe.width() != null &&
			resultProbe.height() != null &&
			(!sourceProbe.width().equals(resultProbe.width()) || !sourceProbe.height().equals(resultProbe.height()))
		) {
			warnings.add(
				"Resolution изменена отдельно от контейнера: %s -> %s."
					.formatted(sizeLabel(sourceProbe.width(), sourceProbe.height()), sizeLabel(resultProbe.width(), resultProbe.height()))
			);
		}
		if (request.targetFps() != null && resultProbe.framesPerSecond() != null) {
			warnings.add("Target FPS зафиксирован отдельно и собран как %s.".formatted(fpsLabel(resultProbe.framesPerSecond())));
		}
	}

	private ResolvedDimensions resolveTargetDimensions(MediaProbe probe, Integer maxWidth, Integer maxHeight) {
		if (probe.width() == null || probe.height() == null) {
			return null;
		}

		double scale = 1.0d;
		if (maxWidth != null && maxWidth > 0) {
			scale = Math.min(scale, maxWidth / (double) probe.width());
		}
		if (maxHeight != null && maxHeight > 0) {
			scale = Math.min(scale, maxHeight / (double) probe.height());
		}

		if (scale >= 1.0d) {
			return new ResolvedDimensions(makeEven(probe.width()), makeEven(probe.height()));
		}

		return new ResolvedDimensions(
			makeEven((int) Math.floor(probe.width() * scale)),
			makeEven((int) Math.floor(probe.height() * scale))
		);
	}

	private int makeEven(int value) {
		if (value <= 2) {
			return Math.max(2, value);
		}
		return value % 2 == 0 ? value : value - 1;
	}

	private int resolveVideoBitrate(Integer requestedBitrateKbps) {
		if (requestedBitrateKbps == null || requestedBitrateKbps <= 0) {
			return 5_000;
		}
		return requestedBitrateKbps;
	}

	private int resolveAudioBitrate(Integer requestedBitrateKbps) {
		if (requestedBitrateKbps == null || requestedBitrateKbps <= 0) {
			return 192;
		}
		return requestedBitrateKbps;
	}

	private String resolveVideoTargetLabel(String targetExtension, String requestedCodec) {
		var codec = normalizeCodec(requestedCodec);
		if ("mp4".equals(targetExtension)) {
			return "MP4 %s transcode".formatted("av1".equals(codec) ? "AV1" : "H.264");
		}
		if ("webm".equals(targetExtension)) {
			return "WebM %s transcode".formatted("av1".equals(codec) ? "AV1" : "VP9");
		}
		return targetExtension.toUpperCase(Locale.ROOT) + " transcode";
	}

	private String resolveAudioTargetLabel(String targetExtension) {
		return switch (targetExtension) {
			case "mp3" -> "MP3 audio export";
			case "wav" -> "WAV PCM export";
			case "aac" -> "AAC audio export";
			case "m4a" -> "M4A AAC export";
			case "flac" -> "FLAC audio export";
			default -> targetExtension.toUpperCase(Locale.ROOT) + " audio export";
		};
	}

	private String resolveMediaType(String extension) {
		return switch (extension) {
			case "mp4" -> "video/mp4";
			case "webm" -> "video/webm";
			case "gif" -> "image/gif";
			case "mp3" -> "audio/mpeg";
			case "wav" -> "audio/wav";
			case "aac" -> "audio/aac";
			case "m4a" -> "audio/mp4";
			case "flac" -> "audio/flac";
			default -> "application/octet-stream";
		};
	}

	private String containerLabel(String extension, String containerName) {
		if (containerName != null && !containerName.isBlank()) {
			return containerName.toUpperCase(Locale.ROOT);
		}
		return extension.toUpperCase(Locale.ROOT);
	}

	private String codecLabel(String codec) {
		if (codec == null || codec.isBlank()) {
			return "Unknown";
		}

		return switch (normalizeCodec(codec)) {
			case "h264", "avc1", "libx264" -> "H.264";
			case "hevc", "h265" -> "H.265 / HEVC";
			case "av1", "libaom-av1" -> "AV1";
			case "vp9", "libvpx-vp9" -> "VP9";
			case "aac" -> "AAC";
			case "mp3", "libmp3lame" -> "MP3";
			case "flac" -> "FLAC";
			case "pcm_s16le" -> "PCM 16-bit";
			case "opus", "libopus" -> "Opus";
			default -> codec.toUpperCase(Locale.ROOT);
		};
	}

	private String sizeLabel(Integer width, Integer height) {
		if (width == null || height == null) {
			return "Unknown";
		}
		return width + " x " + height;
	}

	private String bitrateLabel(Integer bitrateKbps) {
		return bitrateKbps + " kbps";
	}

	private String fpsLabel(Double framesPerSecond) {
		return String.format(Locale.US, "%.2f", framesPerSecond).replaceAll("0+$", "").replaceAll("\\.$", "") + " fps";
	}

	private String durationLabel(Double durationSeconds) {
		return String.format(Locale.US, "%.1f", durationSeconds) + " sec";
	}

	private Integer parseInteger(String value) {
		try {
			return Integer.valueOf(value);
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private Double parseDouble(String value) {
		try {
			return Double.valueOf(value);
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private Integer parseKbps(String value) {
		var rawValue = parseDouble(value);
		if (rawValue == null) {
			return null;
		}
		return (int) Math.round(rawValue / 1_000d);
	}

	private Double parseFramesPerSecond(JsonNode stream) {
		var avgFrameRate = normalizeNullable(stream.path("avg_frame_rate").asText()).orElse(null);
		var rawFrameRate = avgFrameRate != null && !"0/0".equals(avgFrameRate)
			? avgFrameRate
			: normalizeNullable(stream.path("r_frame_rate").asText()).orElse(null);
		if (rawFrameRate == null || rawFrameRate.isBlank() || "0/0".equals(rawFrameRate)) {
			return null;
		}

		var parts = rawFrameRate.split("/");
		if (parts.length == 2) {
			try {
				var numerator = Double.parseDouble(parts[0]);
				var denominator = Double.parseDouble(parts[1]);
				if (denominator == 0d) {
					return null;
				}
				return numerator / denominator;
			}
			catch (NumberFormatException exception) {
				return null;
			}
		}

		return parseDouble(rawFrameRate);
	}

	private CommandResult execute(List<String> command, Path workingDirectory, Duration timeout) {
		Process process = null;
		Thread outputReader = null;
		var output = new StringBuilder();
		var outputFailure = new AtomicReference<IOException>();

		try {
			process = new ProcessBuilder(command)
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true)
				.start();

			var runningProcess = process;
			// stdout/stderr читаем в отдельном потоке, иначе blocking read сломает timeout
			// и backend зависнет на проблемном ffmpeg/ffprobe процессе.
			outputReader = Thread.ofVirtual()
				.name("jack-media-convert-output")
				.start(() -> {
					try (var inputStream = runningProcess.getInputStream()) {
						output.append(readFully(inputStream));
					}
					catch (IOException exception) {
						outputFailure.set(exception);
					}
				});

			var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

			if (!finished) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
				throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Media conversion превысил допустимый timeout.");
			}

			outputReader.join(1_000L);
			if (outputFailure.get() != null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать вывод внешнего media processor.", outputFailure.get());
			}

			var normalizedOutput = normalizeNullable(output.toString()).orElse("без stderr/stdout");
			if (process.exitValue() != 0) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Команда завершилась с кодом %s: %s".formatted(process.exitValue(), normalizedOutput)
				);
			}

			return new CommandResult(output.toString());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось запустить внешний media processor.", exception);
		}
		catch (InterruptedException exception) {
			if (process != null) {
				process.destroy();
				try {
					if (!process.waitFor(5, TimeUnit.SECONDS)) {
						process.destroyForcibly();
						process.waitFor(5, TimeUnit.SECONDS);
					}
				}
				catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			}
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Media conversion была прервана.", exception);
		}
	}

	private void ensureArtifact(Path outputPath, String label) {
		try {
			if (!Files.exists(outputPath) || Files.size(outputPath) <= 0L) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"ffmpeg завершился без итогового artifact для %s.".formatted(label)
				);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать итоговый media artifact.", exception);
		}
	}

	private Duration timeout() {
		return Duration.ofSeconds(this.processingProperties.getMediaPreviewTimeoutSeconds());
	}

	private String ffmpegExecutable() {
		return resolveExecutable(this.processingProperties.getFfmpegExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ffmpeg executable не найден в текущем backend окружении."));
	}

	private String ffprobeExecutable() {
		return resolveExecutable(this.processingProperties.getFfprobeExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ffprobe executable не найден в текущем backend окружении."));
	}

	private void ensureAvailability() {
		ffmpegExecutable();
		ffprobeExecutable();
	}

	private java.util.Optional<Path> resolveExecutable(String executable) {
		var candidate = Path.of(executable);
		if (candidate.isAbsolute()) {
			return Files.isExecutable(candidate) ? java.util.Optional.of(candidate) : java.util.Optional.empty();
		}

		var path = System.getenv("PATH");
		if (path == null || path.isBlank()) {
			return java.util.Optional.empty();
		}

		for (String directory : path.split(java.io.File.pathSeparator)) {
			if (directory.isBlank()) {
				continue;
			}

			var resolved = Path.of(directory).resolve(executable);
			if (Files.isExecutable(resolved)) {
				return java.util.Optional.of(resolved);
			}
		}

		return java.util.Optional.empty();
	}

	private String normalizeExtension(String extension) {
		return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeCodec(String codec) {
		return codec == null ? null : codec.trim().toLowerCase(Locale.ROOT);
	}

	private java.util.Optional<String> normalizeNullable(String value) {
		if (value == null) {
			return java.util.Optional.empty();
		}

		var normalized = value.trim();
		return normalized.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(normalized);
	}

	private String replaceExtension(String fileName, String targetExtension) {
		var dotIndex = fileName.lastIndexOf('.');
		if (dotIndex < 0) {
			return fileName + "." + targetExtension;
		}
		return fileName.substring(0, dotIndex + 1) + targetExtension;
	}

	private String derivedPreviewFileName(String originalFileName, String targetExtension) {
		var dotIndex = originalFileName.lastIndexOf('.');
		var baseName = dotIndex >= 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
		return baseName + ".preview." + targetExtension;
	}

	private void deleteRecursively(Path workingDirectory) {
		if (workingDirectory == null) {
			return;
		}

		try (var paths = Files.walk(workingDirectory)) {
			paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
				}
			});
		}
		catch (IOException ignored) {
		}
	}

	private String readFully(InputStream inputStream) throws IOException {
		var outputStream = new ByteArrayOutputStream();
		inputStream.transferTo(outputStream);
		return outputStream.toString(StandardCharsets.UTF_8);
	}

	public record MediaConversionResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel,
		List<String> warnings
	) {
	}

	public record MediaConvertManifest(
		UUID uploadId,
		String originalFileName,
		String sourceExtension,
		String targetExtension,
		String resultMediaType,
		String previewMediaType,
		String previewKind,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		List<DocumentPreviewPayload.DocumentFact> sourceFacts,
		List<DocumentPreviewPayload.DocumentFact> resultFacts,
		List<String> warnings
	) {
	}

	private record MediaConversionOutput(
		Path resultPath,
		String resultFileName,
		String resultMediaType,
		Path previewPath,
		String previewFileName,
		String previewMediaType,
		String previewKind,
		List<DocumentPreviewPayload.DocumentFact> sourceFacts,
		List<DocumentPreviewPayload.DocumentFact> resultFacts,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		List<String> warnings
	) {
	}

	private record PreviewArtifact(
		Path path,
		String fileName,
		String mediaType,
		String previewKind,
		List<String> warnings
	) {
	}

	private record MediaProbe(
		String containerName,
		Double durationSeconds,
		boolean hasVideo,
		boolean hasAudio,
		String videoCodec,
		String audioCodec,
		Integer width,
		Integer height,
		Double framesPerSecond,
		Integer sampleRate,
		Integer channelCount,
		Integer videoBitrateKbps,
		Integer audioBitrateKbps,
		Integer overallBitrateKbps
	) {
	}

	private record ResolvedDimensions(
		Integer width,
		Integer height
	) {
	}

	private record CommandResult(
		String stdout
	) {
	}

}
