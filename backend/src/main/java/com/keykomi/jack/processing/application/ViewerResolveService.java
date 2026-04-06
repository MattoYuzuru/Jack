package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.ImageProcessingRequest;
import com.keykomi.jack.processing.domain.MetadataPayloads;
import com.keykomi.jack.processing.domain.MetadataProcessingRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import com.keykomi.jack.processing.domain.ViewerPayloads;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ViewerResolveService {

	private static final int IMAGE_PREVIEW_BOUND = 4_096;
	private static final int AUDIO_WAVEFORM_BUCKET_COUNT = 56;
	private static final int AUDIO_WAVEFORM_SAMPLE_RATE = 22_050;

	private final ArtifactStorageService artifactStorageService;
	private final ImageProcessingService imageProcessingService;
	private final MediaPreviewService mediaPreviewService;
	private final DocumentPreviewService documentPreviewService;
	private final MetadataProcessingService metadataProcessingService;
	private final ProcessingProperties processingProperties;
	private final ObjectMapper objectMapper;

	public ViewerResolveService(
		ArtifactStorageService artifactStorageService,
		ImageProcessingService imageProcessingService,
		MediaPreviewService mediaPreviewService,
		DocumentPreviewService documentPreviewService,
		MetadataProcessingService metadataProcessingService,
		ProcessingProperties processingProperties,
		ObjectMapper objectMapper
	) {
		this.artifactStorageService = artifactStorageService;
		this.imageProcessingService = imageProcessingService;
		this.mediaPreviewService = mediaPreviewService;
		this.documentPreviewService = documentPreviewService;
		this.metadataProcessingService = metadataProcessingService;
		this.processingProperties = processingProperties;
		this.objectMapper = objectMapper;
	}

	public boolean isAvailable() {
		return this.mediaPreviewService.isAvailable()
			|| this.documentPreviewService.isAvailable()
			|| (this.imageProcessingService.isAvailable() && this.metadataProcessingService.isAvailable());
	}

	public boolean isAvailableFor(StoredUpload upload) {
		return switch (ProcessingFileFamilyResolver.detectFamily(upload)) {
			case "image" -> this.imageProcessingService.isAvailable() && this.metadataProcessingService.isAvailable();
			case "document" -> this.documentPreviewService.isAvailable();
			case "media" -> this.mediaPreviewService.isAvailable();
			case "audio" -> this.mediaPreviewService.isAvailable() && this.metadataProcessingService.isAvailable();
			default -> false;
		};
	}

	public ViewerResolveResult process(UUID jobId, StoredUpload upload) {
		return switch (ProcessingFileFamilyResolver.detectFamily(upload)) {
			case "image" -> processImage(jobId, upload);
			case "document" -> processDocument(jobId, upload);
			case "media" -> processVideo(jobId, upload);
			case "audio" -> processAudio(jobId, upload);
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"VIEWER_RESOLVE job поддерживает только server-assisted image/document/media/audio uploads."
			);
		};
	}

	private ViewerResolveResult processImage(UUID jobId, StoredUpload upload) {
		var imageResult = this.imageProcessingService.process(
			jobId,
			upload,
			new ImageProcessingRequest("preview", null, IMAGE_PREVIEW_BOUND, IMAGE_PREVIEW_BOUND, null, null, null)
		);
		var metadataResult = this.metadataProcessingService.process(
			jobId,
			upload,
			new MetadataProcessingRequest(MetadataProcessingRequest.Operation.INSPECT_IMAGE, null)
		);

		var imageManifestArtifact = requireArtifact(imageResult.artifacts(), "image-preview-manifest");
		var previewArtifact = requireArtifact(imageResult.artifacts(), "image-preview-binary");
		var metadataManifestArtifact = requireArtifact(metadataResult.artifacts(), "metadata-inspect-manifest");

		var imageManifest = readArtifact(imageManifestArtifact, ImageProcessingService.ImageProcessingManifest.class);
		var metadataManifest = readArtifact(metadataManifestArtifact, MetadataPayloads.MetadataInspectManifest.class);
		if (metadataManifest.imagePayload() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Metadata inspect не вернул image payload для VIEWER_RESOLVE.");
		}

		var viewerManifest = new ViewerPayloads.ViewerResolveManifest(
			upload.id(),
			upload.originalFileName(),
			"image",
			"image",
			"Server image preview",
			toBinaryArtifact(previewArtifact),
			new ViewerPayloads.ViewerImagePayload(
				imageManifest.width(),
				imageManifest.height(),
				metadataManifest.imagePayload(),
				deduplicateWarnings(imageManifest.warnings(), metadataManifest.warnings())
			),
			null,
			null,
			null
		);
		var viewerArtifact = storeViewerManifest(jobId, viewerManifest);

		return new ViewerResolveResult(
			mergeArtifacts(imageResult.artifacts(), metadataResult.artifacts(), List.of(viewerArtifact)),
			"Server image preview"
		);
	}

	private ViewerResolveResult processDocument(UUID jobId, StoredUpload upload) {
		var documentResult = this.documentPreviewService.process(jobId, upload);
		var documentManifestArtifact = requireArtifact(documentResult.artifacts(), "document-preview-manifest");
		var binaryArtifact = findArtifact(documentResult.artifacts(), "document-preview-binary").orElse(null);
		var documentPayload = readArtifact(documentManifestArtifact, DocumentPreviewPayload.class);

		var viewerManifest = new ViewerPayloads.ViewerResolveManifest(
			upload.id(),
			upload.originalFileName(),
			"document",
			"document",
			documentPayload.previewLabel(),
			binaryArtifact == null ? null : toBinaryArtifact(binaryArtifact),
			null,
			new ViewerPayloads.ViewerDocumentPayload(
				documentPayload.summary(),
				documentPayload.searchableText(),
				documentPayload.warnings(),
				documentPayload.layout()
			),
			null,
			null
		);
		var viewerArtifact = storeViewerManifest(jobId, viewerManifest);

		return new ViewerResolveResult(
			mergeArtifacts(documentResult.artifacts(), List.of(viewerArtifact)),
			documentPayload.previewLabel()
		);
	}

	private ViewerResolveResult processVideo(UUID jobId, StoredUpload upload) {
		var mediaResult = this.mediaPreviewService.buildPreview(jobId, upload);
		var mediaManifestArtifact = requireArtifact(mediaResult.artifacts(), "media-preview-manifest");
		var previewArtifact = requireArtifact(mediaResult.artifacts(), "media-preview-binary");
		var mediaManifest = readArtifact(mediaManifestArtifact, MediaPreviewService.MediaPreviewManifest.class);

		var metadata = new ViewerPayloads.ViewerVideoMetadata(
			previewArtifact.mediaType(),
			formatAspectRatio(mediaManifest.probe().width(), mediaManifest.probe().height()),
			resolveOrientation(mediaManifest.probe().width(), mediaManifest.probe().height()),
			estimateBitrateBitsPerSecond(previewArtifact.sizeBytes(), mediaManifest.probe().durationSeconds()),
			previewArtifact.sizeBytes()
		);
		var viewerManifest = new ViewerPayloads.ViewerResolveManifest(
			upload.id(),
			upload.originalFileName(),
			"media",
			"video",
			"Server video preview",
			toBinaryArtifact(previewArtifact),
			null,
			null,
			new ViewerPayloads.ViewerVideoPayload(
				buildVideoSummary(upload, mediaManifest, metadata),
				mediaManifest.warnings(),
				new ViewerPayloads.ViewerVideoLayout(
					"native",
					mediaManifest.probe().durationSeconds(),
					mediaManifest.probe().width(),
					mediaManifest.probe().height(),
					metadata
				)
			),
			null
		);
		var viewerArtifact = storeViewerManifest(jobId, viewerManifest);

		return new ViewerResolveResult(
			mergeArtifacts(mediaResult.artifacts(), List.of(viewerArtifact)),
			"Server video preview"
		);
	}

	private ViewerResolveResult processAudio(UUID jobId, StoredUpload upload) {
		var mediaResult = this.mediaPreviewService.buildPreview(jobId, upload);
		var metadataResult = this.metadataProcessingService.process(
			jobId,
			upload,
			new MetadataProcessingRequest(MetadataProcessingRequest.Operation.INSPECT_AUDIO, null)
		);

		var mediaManifestArtifact = requireArtifact(mediaResult.artifacts(), "media-preview-manifest");
		var previewArtifact = requireArtifact(mediaResult.artifacts(), "media-preview-binary");
		var metadataManifestArtifact = requireArtifact(metadataResult.artifacts(), "metadata-inspect-manifest");

		var mediaManifest = readArtifact(mediaManifestArtifact, MediaPreviewService.MediaPreviewManifest.class);
		var metadataManifest = readArtifact(metadataManifestArtifact, MetadataPayloads.MetadataInspectManifest.class);
		if (metadataManifest.audioPayload() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Metadata inspect не вернул audio payload для VIEWER_RESOLVE.");
		}

		// Waveform считаем уже из backend preview artifact, чтобы browser не декодировал
		// legacy/lossless контейнер повторно только ради visual rail.
		var waveform = buildAudioWaveform(previewArtifact.storagePath(), mediaManifest.probe().durationSeconds());
		var metadata = new ViewerPayloads.ViewerAudioMetadata(
			previewArtifact.mediaType(),
			estimateBitrateBitsPerSecond(previewArtifact.sizeBytes(), mediaManifest.probe().durationSeconds()),
			metadataManifest.audioPayload().technical() == null ? null : metadataManifest.audioPayload().technical().sampleRate(),
			metadataManifest.audioPayload().technical() == null ? null : metadataManifest.audioPayload().technical().channelCount(),
			metadataManifest.audioPayload().technical() == null ? null : metadataManifest.audioPayload().technical().codec(),
			metadataManifest.audioPayload().technical() == null ? null : metadataManifest.audioPayload().technical().container(),
			previewArtifact.sizeBytes()
		);
		var warnings = new ArrayList<>(deduplicateWarnings(mediaManifest.warnings(), metadataManifest.warnings(), waveform.warnings()));
		var viewerManifest = new ViewerPayloads.ViewerResolveManifest(
			upload.id(),
			upload.originalFileName(),
			"audio",
			"audio",
			"Server audio preview",
			toBinaryArtifact(previewArtifact),
			null,
			null,
			null,
			new ViewerPayloads.ViewerAudioPayload(
				buildAudioSummary(upload, mediaManifest, metadata),
				warnings,
				Optional.ofNullable(metadataManifest.audioPayload().searchableText()).orElse(""),
				metadataManifest.audioPayload().artworkDataUrl(),
				Optional.ofNullable(metadataManifest.audioPayload().groups()).orElse(List.of()),
				new ViewerPayloads.ViewerAudioLayout(
					"native",
					mediaManifest.probe().durationSeconds(),
					waveform.values(),
					metadata
				)
			)
		);
		var viewerArtifact = storeViewerManifest(jobId, viewerManifest);

		return new ViewerResolveResult(
			mergeArtifacts(mediaResult.artifacts(), metadataResult.artifacts(), List.of(viewerArtifact)),
			"Server audio preview"
		);
	}

	private StoredArtifact storeViewerManifest(UUID jobId, ViewerPayloads.ViewerResolveManifest manifest) {
		return this.artifactStorageService.storeJsonArtifact(
			jobId,
			"viewer-resolve-manifest",
			"viewer-resolve-manifest.json",
			manifest
		);
	}

	private List<StoredArtifact> mergeArtifacts(List<StoredArtifact>... artifactGroups) {
		var merged = new ArrayList<StoredArtifact>();
		var seenIds = new LinkedHashSet<UUID>();

		// VIEWER_RESOLVE собирает результат поверх уже существующих сервисов, поэтому здесь
		// важно не дублировать внутренние artifacts, а аккуратно свести их в один job output.
		for (List<StoredArtifact> artifactGroup : artifactGroups) {
			for (StoredArtifact artifact : artifactGroup) {
				if (seenIds.add(artifact.id())) {
					merged.add(artifact);
				}
			}
		}

		return List.copyOf(merged);
	}

	private StoredArtifact requireArtifact(List<StoredArtifact> artifacts, String kind) {
		return findArtifact(artifacts, kind)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Не удалось подготовить обязательный файл результата: %s.".formatted(kind)
			));
	}

	private Optional<StoredArtifact> findArtifact(List<StoredArtifact> artifacts, String kind) {
		return artifacts.stream().filter(artifact -> kind.equals(artifact.kind())).findFirst();
	}

	private <T> T readArtifact(StoredArtifact artifact, Class<T> payloadType) {
		try {
			return this.objectMapper.readValue(artifact.storagePath().toFile(), payloadType);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Не удалось прочитать подготовленный файл результата: %s.".formatted(artifact.kind()),
				exception
			);
		}
	}

	private ViewerPayloads.ViewerBinaryArtifact toBinaryArtifact(StoredArtifact artifact) {
		return new ViewerPayloads.ViewerBinaryArtifact(
			artifact.kind(),
			artifact.fileName(),
			artifact.mediaType(),
			artifact.sizeBytes(),
			artifactDownloadPath(artifact)
		);
	}

	private String artifactDownloadPath(StoredArtifact artifact) {
		return "/api/jobs/%s/artifacts/%s".formatted(artifact.jobId(), artifact.id());
	}

	private List<ViewerPayloads.ViewerFact> buildVideoSummary(
		StoredUpload upload,
		MediaPreviewService.MediaPreviewManifest mediaManifest,
		ViewerPayloads.ViewerVideoMetadata metadata
	) {
		var summary = new ArrayList<ViewerPayloads.ViewerFact>();
		summary.add(new ViewerPayloads.ViewerFact("Тип видео", normalizeExtension(upload.extension()).toUpperCase(Locale.ROOT)));
		summary.add(new ViewerPayloads.ViewerFact("Длительность", formatDuration(mediaManifest.probe().durationSeconds())));
		summary.add(new ViewerPayloads.ViewerFact("Кадр", formatFrame(mediaManifest.probe().width(), mediaManifest.probe().height())));
		summary.add(new ViewerPayloads.ViewerFact("Aspect Ratio", metadata.aspectRatio()));
		summary.add(new ViewerPayloads.ViewerFact("Orientation", metadata.orientation()));
		summary.add(new ViewerPayloads.ViewerFact("Estimated Bitrate", formatBitrate(metadata.estimatedBitrateBitsPerSecond())));
		summary.add(new ViewerPayloads.ViewerFact("Playback path", "Backend VIEWER_RESOLVE · %s".formatted(mediaManifest.runtimeLabel())));
		summary.add(new ViewerPayloads.ViewerFact("Source Codec", Optional.ofNullable(mediaManifest.probe().codecName()).orElse("n/a")));
		return List.copyOf(summary);
	}

	private List<ViewerPayloads.ViewerFact> buildAudioSummary(
		StoredUpload upload,
		MediaPreviewService.MediaPreviewManifest mediaManifest,
		ViewerPayloads.ViewerAudioMetadata metadata
	) {
		var summary = new ArrayList<ViewerPayloads.ViewerFact>();
		summary.add(new ViewerPayloads.ViewerFact("Тип аудио", normalizeExtension(upload.extension()).toUpperCase(Locale.ROOT)));
		summary.add(new ViewerPayloads.ViewerFact("Длительность", formatDuration(mediaManifest.probe().durationSeconds())));
		summary.add(new ViewerPayloads.ViewerFact("Estimated Bitrate", formatBitrate(metadata.estimatedBitrateBitsPerSecond())));
		summary.add(new ViewerPayloads.ViewerFact("Sample Rate", formatSampleRate(metadata.sampleRate())));
		summary.add(new ViewerPayloads.ViewerFact("Channels", formatChannelLayout(metadata.channelCount())));
		summary.add(new ViewerPayloads.ViewerFact("Codec", Optional.ofNullable(metadata.codec()).orElse("n/a")));
		summary.add(new ViewerPayloads.ViewerFact("Playback path", "Backend VIEWER_RESOLVE · %s".formatted(mediaManifest.runtimeLabel())));
		summary.add(new ViewerPayloads.ViewerFact("Source Codec", Optional.ofNullable(mediaManifest.probe().codecName()).orElse("n/a")));
		return List.copyOf(summary);
	}

	private AudioWaveformResult buildAudioWaveform(Path previewPath, Double durationSeconds) {
		if (durationSeconds == null || durationSeconds <= 0) {
			return new AudioWaveformResult(
				List.of(),
				List.of("Waveform не удалось подготовить server-side: backend не получил корректную длительность audio preview.")
			);
		}

		var executable = resolveExecutable(this.processingProperties.getFfmpegExecutable())
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"VIEWER_RESOLVE audio waveform требует доступного ffmpeg binary."
			));
		var command = List.of(
			executable.toString(),
			"-v",
			"error",
			"-i",
			previewPath.toString(),
			"-vn",
			"-ac",
			"1",
			"-ar",
			String.valueOf(AUDIO_WAVEFORM_SAMPLE_RATE),
			"-f",
			"s16le",
			"-acodec",
			"pcm_s16le",
			"-"
		);

		Process process;
		try {
			process = new ProcessBuilder(command)
				.directory(previewPath.getParent().toFile())
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
		}
		catch (IOException exception) {
			return new AudioWaveformResult(
				List.of(),
				List.of("Waveform не удалось подготовить server-side: ffmpeg waveform helper не стартовал.")
			);
		}

		var expectedSamples = Math.max(1L, Math.round(durationSeconds * AUDIO_WAVEFORM_SAMPLE_RATE));
		var samplesPerBucket = Math.max(1L, (long) Math.ceil(expectedSamples / (double) AUDIO_WAVEFORM_BUCKET_COUNT));
		var bucketPeaks = new double[AUDIO_WAVEFORM_BUCKET_COUNT];
		int bucketIndex = 0;
		long samplesInBucket = 0L;

		try (InputStream inputStream = process.getInputStream()) {
			while (bucketIndex < AUDIO_WAVEFORM_BUCKET_COUNT) {
				int low = inputStream.read();
				if (low == -1) {
					break;
				}

				int high = inputStream.read();
				if (high == -1) {
					break;
				}

				short sample = (short) (((high & 0xff) << 8) | (low & 0xff));
				double amplitude = Math.abs(sample / 32768d);
				if (amplitude > bucketPeaks[bucketIndex]) {
					bucketPeaks[bucketIndex] = amplitude;
				}

				samplesInBucket += 1;
				if (samplesInBucket >= samplesPerBucket && bucketIndex < AUDIO_WAVEFORM_BUCKET_COUNT - 1) {
					bucketIndex += 1;
					samplesInBucket = 0L;
				}
			}
		}
		catch (IOException exception) {
			process.destroyForcibly();
			return new AudioWaveformResult(
				List.of(),
				List.of("Waveform не удалось подготовить server-side: ffmpeg PCM stream оборвался с ошибкой.")
			);
		}

		try {
			if (!process.waitFor(timeout().toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				return new AudioWaveformResult(
					List.of(),
					List.of("Waveform не удалось подготовить server-side: ffmpeg waveform helper превысил timeout.")
				);
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return new AudioWaveformResult(
				List.of(),
				List.of("Waveform не удалось подготовить server-side: job был прерван.")
			);
		}

		if (process.exitValue() != 0) {
			return new AudioWaveformResult(
				List.of(),
				List.of("Waveform не удалось подготовить server-side: ffmpeg waveform helper завершился с ошибкой.")
			);
		}

		double peak = 0d;
		for (double bucketPeak : bucketPeaks) {
			if (bucketPeak > peak) {
				peak = bucketPeak;
			}
		}
		if (peak <= 0d) {
			return new AudioWaveformResult(
				List.of(),
				List.of("Waveform не удалось подготовить server-side: preview не дал различимый PCM signal.")
			);
		}

		var waveform = new ArrayList<Double>(AUDIO_WAVEFORM_BUCKET_COUNT);
		for (double bucketPeak : bucketPeaks) {
			waveform.add(roundWaveformValue(bucketPeak / peak));
		}
		return new AudioWaveformResult(List.copyOf(waveform), List.of());
	}

	private Duration timeout() {
		return Duration.ofSeconds(Math.max(5L, this.processingProperties.getMediaPreviewTimeoutSeconds()));
	}

	private Optional<Path> resolveExecutable(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return Optional.empty();
		}

		var path = Path.of(rawValue);
		return Files.isRegularFile(path) && Files.isExecutable(path) ? Optional.of(path) : Optional.empty();
	}

	private String formatFrame(Integer width, Integer height) {
		if (width == null || height == null || width <= 0 || height <= 0) {
			return "n/a";
		}
		return "%s x %s".formatted(width, height);
	}

	private String formatAspectRatio(Integer width, Integer height) {
		if (width == null || height == null || width <= 0 || height <= 0) {
			return "n/a";
		}
		int divisor = greatestCommonDivisor(width, height);
		return "%s:%s".formatted(width / divisor, height / divisor);
	}

	private String resolveOrientation(Integer width, Integer height) {
		if (width == null || height == null || width <= 0 || height <= 0) {
			return "Unknown";
		}
		if (width == height) {
			return "Square";
		}
		return width > height ? "Landscape" : "Portrait";
	}

	private int greatestCommonDivisor(int left, int right) {
		int a = Math.abs(left);
		int b = Math.abs(right);
		while (b != 0) {
			int remainder = a % b;
			a = b;
			b = remainder;
		}
		return a == 0 ? 1 : a;
	}

	private Long estimateBitrateBitsPerSecond(long sizeBytes, Double durationSeconds) {
		if (sizeBytes <= 0 || durationSeconds == null || durationSeconds <= 0 || !Double.isFinite(durationSeconds)) {
			return null;
		}
		return Math.round((sizeBytes * 8d) / durationSeconds);
	}

	private String formatBitrate(Long bitsPerSecond) {
		if (bitsPerSecond == null || bitsPerSecond <= 0) {
			return "n/a";
		}
		if (bitsPerSecond >= 1_000_000L) {
			return "%.2f Mbps".formatted(bitsPerSecond / 1_000_000d);
		}
		return "%s kbps".formatted(Math.round(bitsPerSecond / 1_000d));
	}

	private String formatDuration(Double durationSeconds) {
		if (durationSeconds == null || durationSeconds < 0 || !Double.isFinite(durationSeconds)) {
			return "00:00";
		}

		long roundedSeconds = (long) Math.floor(durationSeconds);
		long hours = roundedSeconds / 3_600L;
		long minutes = (roundedSeconds % 3_600L) / 60L;
		long seconds = roundedSeconds % 60L;

		if (hours > 0) {
			return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
		}
		return "%02d:%02d".formatted(minutes, seconds);
	}

	private String formatSampleRate(Integer sampleRate) {
		if (sampleRate == null || sampleRate <= 0) {
			return "n/a";
		}
		if (sampleRate >= 96_000) {
			return "%s kHz".formatted(Math.round(sampleRate / 1_000d));
		}
		return "%.1f kHz".formatted(sampleRate / 1_000d);
	}

	private String formatChannelLayout(Integer channelCount) {
		if (channelCount == null || channelCount <= 0) {
			return "n/a";
		}
		if (channelCount == 1) {
			return "Mono";
		}
		if (channelCount == 2) {
			return "Stereo";
		}
		return "%s ch".formatted(channelCount);
	}

	private Double roundWaveformValue(double value) {
		return Math.round(value * 10_000d) / 10_000d;
	}

	private String normalizeExtension(String extension) {
		if (extension == null) {
			return "";
		}

		var normalized = extension.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "jpeg" -> "jpg";
			case "tif" -> "tiff";
			default -> normalized;
		};
	}

	private List<String> deduplicateWarnings(List<String>... warningGroups) {
		var warnings = new LinkedHashSet<String>();
		for (List<String> warningGroup : warningGroups) {
			for (String warning : warningGroup) {
				if (warning != null && !warning.isBlank()) {
					warnings.add(warning);
				}
			}
		}
		return List.copyOf(warnings);
	}

	public record ViewerResolveResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel
	) {
	}

	private record AudioWaveformResult(
		List<Double> values,
		List<String> warnings
	) {
	}

}
