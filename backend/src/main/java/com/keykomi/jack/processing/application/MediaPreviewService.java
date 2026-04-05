package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaPreviewService {

	private final ProcessingProperties processingProperties;
	private final ArtifactStorageService artifactStorageService;
	private final ObjectMapper objectMapper;

	public MediaPreviewService(
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

	public MediaPreviewResult buildPreview(UUID jobId, StoredUpload upload) {
		var family = ProcessingFileFamilyResolver.detectFamily(upload);
		if (!"media".equals(family) && !"audio".equals(family)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MEDIA_PREVIEW job принимает только audio/video uploads.");
		}

		ensureAvailability();

		Path workingDirectory = null;
		try {
			workingDirectory = Files.createTempDirectory(this.processingProperties.getStorageRoot(), "media-preview-");

			var probe = probeSource(upload, family, workingDirectory);
			var preview = transcodePreview(upload, family, workingDirectory);

			var previewArtifact = this.artifactStorageService.storeFileArtifact(
				jobId,
				"media-preview-binary",
				preview.fileName(),
				preview.mediaType(),
				preview.outputPath()
			);
			var manifestArtifact = this.artifactStorageService.storeJsonArtifact(
				jobId,
				"media-preview-manifest",
				"media-preview-manifest.json",
				new MediaPreviewManifest(
					upload.id(),
					upload.originalFileName(),
					family,
					probe,
					preview.runtimeLabel(),
					preview.mediaType(),
					Instant.now(),
					preview.warnings()
				)
			);

			return new MediaPreviewResult(
				List.of(manifestArtifact, previewArtifact),
				preview.runtimeLabel(),
				preview.warnings(),
				probe
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить media preview workspace.", exception);
		}
		finally {
			if (workingDirectory != null) {
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
		}
	}

	private MediaPreviewProbe probeSource(StoredUpload upload, String family, Path workingDirectory) {
		var command = new ArrayList<>(List.of(
			ffprobeExecutable(),
			"-v",
			"error",
			"-show_entries",
			"format=duration:stream=codec_name,width,height,sample_rate,channels",
			"-of",
			"json",
			upload.storagePath().toString()
		));

		if ("media".equals(family)) {
			command.add(1, "-select_streams");
			command.add(2, "v:0");
		}
		if ("audio".equals(family)) {
			command.add(1, "-select_streams");
			command.add(2, "a:0");
		}

		var output = execute(command, workingDirectory, timeout());

		try {
			JsonNode root = this.objectMapper.readTree(output.stdout());
			JsonNode format = root.path("format");
			JsonNode stream = root.path("streams").path(0);

			return new MediaPreviewProbe(
				format.path("duration").isMissingNode() ? null : parseDouble(format.path("duration").asText()),
				stream.path("codec_name").isMissingNode() ? null : normalizeNullable(stream.path("codec_name").asText()).orElse(null),
				stream.path("width").isNumber() ? stream.path("width").asInt() : null,
				stream.path("height").isNumber() ? stream.path("height").asInt() : null,
				stream.path("sample_rate").isMissingNode() ? null : parseInteger(stream.path("sample_rate").asText()),
				stream.path("channels").isNumber() ? stream.path("channels").asInt() : null
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ffprobe вернул неподдерживаемый JSON probe output.", exception);
		}
	}

	private MediaPreviewOutput transcodePreview(StoredUpload upload, String family, Path workingDirectory) {
		return switch (family) {
			case "media" -> transcodeVideo(upload, workingDirectory);
			case "audio" -> transcodeAudio(upload, workingDirectory);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media preview поддерживает только audio/video uploads.");
		};
	}

	private MediaPreviewOutput transcodeVideo(StoredUpload upload, Path workingDirectory) {
		var profiles = List.of(
			new MediaPreviewProfile(
				"MP4 transcode",
				previewFileName(upload.originalFileName(), ".preview.mp4"),
				"video/mp4",
				List.of(
					ffmpegExecutable(),
					"-v",
					"error",
					"-i",
					upload.storagePath().toString(),
					"-map",
					"0:v:0",
					"-map",
					"0:a?",
					"-sn",
					"-vf",
					"scale=trunc(iw/2)*2:trunc(ih/2)*2",
					"-c:v",
					"libx264",
					"-preset",
					"ultrafast",
					"-pix_fmt",
					"yuv420p",
					"-movflags",
					"+faststart",
					"-c:a",
					"aac",
					"-b:a",
					"160k"
				),
				List.of()
			),
			new MediaPreviewProfile(
				"MP4 transcode (video only)",
				previewFileName(upload.originalFileName(), ".preview.silent.mp4"),
				"video/mp4",
				List.of(
					ffmpegExecutable(),
					"-v",
					"error",
					"-i",
					upload.storagePath().toString(),
					"-map",
					"0:v:0",
					"-sn",
					"-an",
					"-vf",
					"scale=trunc(iw/2)*2:trunc(ih/2)*2",
					"-c:v",
					"libx264",
					"-preset",
					"ultrafast",
					"-pix_fmt",
					"yuv420p",
					"-movflags",
					"+faststart"
				),
				List.of("Аудиодорожка была отброшена во время backend preview fallback.")
			),
			new MediaPreviewProfile(
				"WebM fallback transcode",
				previewFileName(upload.originalFileName(), ".preview.webm"),
				"video/webm",
				List.of(
					ffmpegExecutable(),
					"-v",
					"error",
					"-i",
					upload.storagePath().toString(),
					"-map",
					"0:v:0",
					"-map",
					"0:a?",
					"-sn",
					"-c:v",
					"libvpx-vp9",
					"-row-mt",
					"1",
					"-deadline",
					"realtime",
					"-cpu-used",
					"8",
					"-b:v",
					"0",
					"-crf",
					"32",
					"-c:a",
					"libopus",
					"-b:a",
					"128k"
				),
				List.of("MP4 backend transcode не собрался, поэтому использован WebM fallback.")
			)
		);

		return executeProfiles(workingDirectory, profiles, "video");
	}

	private MediaPreviewOutput transcodeAudio(StoredUpload upload, Path workingDirectory) {
		var profiles = List.of(
			new MediaPreviewProfile(
				"MP3 transcode",
				previewFileName(upload.originalFileName(), ".preview.mp3"),
				"audio/mpeg",
				List.of(
					ffmpegExecutable(),
					"-v",
					"error",
					"-i",
					upload.storagePath().toString(),
					"-map",
					"0:a:0",
					"-vn",
					"-c:a",
					"libmp3lame",
					"-b:a",
					"192k"
				),
				List.of()
			),
			new MediaPreviewProfile(
				"WAV fallback",
				previewFileName(upload.originalFileName(), ".preview.wav"),
				"audio/wav",
				List.of(
					ffmpegExecutable(),
					"-v",
					"error",
					"-i",
					upload.storagePath().toString(),
					"-map",
					"0:a:0",
					"-vn",
					"-c:a",
					"pcm_s16le",
					"-ar",
					"44100",
					"-ac",
					"2"
				),
				List.of("Playback собран через PCM fallback, поэтому preview может оказаться тяжелее исходника.")
			)
		);

		return executeProfiles(workingDirectory, profiles, "audio");
	}

	private MediaPreviewOutput executeProfiles(Path workingDirectory, List<MediaPreviewProfile> profiles, String familyLabel) {
		var failures = new ArrayList<String>();

		for (MediaPreviewProfile profile : profiles) {
			var outputPath = workingDirectory.resolve(profile.outputFileName());
			var command = new ArrayList<>(profile.command());
			command.add(outputPath.toString());

			try {
				execute(command, workingDirectory, timeout());
				if (!hasRenderableOutput(outputPath)) {
					throw new ResponseStatusException(
						HttpStatus.UNPROCESSABLE_ENTITY,
						"Команда завершилась без итогового media artifact: %s".formatted(profile.runtimeLabel())
					);
				}
				return new MediaPreviewOutput(
					outputPath,
					profile.outputFileName(),
					profile.mediaType(),
					profile.runtimeLabel(),
					profile.warnings()
				);
			}
			catch (ResponseStatusException exception) {
				failures.add(exception.getReason());
			}
		}

		throw new ResponseStatusException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"Backend ffmpeg не смог собрать browser-friendly %s preview. %s".formatted(familyLabel, String.join(" ", failures))
		);
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
				.name("jack-media-preview-output")
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
				throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Media processing превысил допустимый timeout.");
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
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Media processing был прерван.", exception);
		}
	}

	private Duration timeout() {
		return Duration.ofSeconds(this.processingProperties.getMediaPreviewTimeoutSeconds());
	}

	private boolean hasRenderableOutput(Path outputPath) {
		try {
			return Files.exists(outputPath) && Files.size(outputPath) > 0L;
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать итоговый media artifact.", exception);
		}
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

	private java.util.Optional<String> normalizeNullable(String value) {
		if (value == null) {
			return java.util.Optional.empty();
		}

		var normalized = value.trim();
		return normalized.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(normalized);
	}

	private String previewFileName(String originalFileName, String suffix) {
		int dotIndex = originalFileName.lastIndexOf('.');
		String baseName = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
		return baseName + suffix;
	}

	private String readFully(InputStream inputStream) throws IOException {
		var outputStream = new ByteArrayOutputStream();
		inputStream.transferTo(outputStream);
		return outputStream.toString(StandardCharsets.UTF_8);
	}

	public record MediaPreviewResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel,
		List<String> warnings,
		MediaPreviewProbe probe
	) {
	}

	public record MediaPreviewProbe(
		Double durationSeconds,
		String codecName,
		Integer width,
		Integer height,
		Integer sampleRate,
		Integer channelCount
	) {
	}

	public record MediaPreviewManifest(
		UUID uploadId,
		String originalFileName,
		String family,
		MediaPreviewProbe probe,
		String runtimeLabel,
		String previewMediaType,
		Instant generatedAt,
		List<String> warnings
	) {
	}

	private record MediaPreviewProfile(
		String runtimeLabel,
		String outputFileName,
		String mediaType,
		List<String> command,
		List<String> warnings
	) {
	}

	private record MediaPreviewOutput(
		Path outputPath,
		String fileName,
		String mediaType,
		String runtimeLabel,
		List<String> warnings
	) {
	}

	private record CommandResult(
		String stdout
	) {
	}

}
