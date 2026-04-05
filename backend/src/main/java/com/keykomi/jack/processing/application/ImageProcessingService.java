package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.ImageProcessingRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImageProcessingService {

	private static final Set<String> RAW_EXTENSIONS = Set.of(
		"raw",
		"dng",
		"cr2",
		"cr3",
		"nef",
		"arw",
		"raf",
		"rw2",
		"orf",
		"pef",
		"srw"
	);
	private static final Set<String> ILLUSTRATION_EXTENSIONS = Set.of("ai", "eps", "ps");
	private static final Set<String> LOSSY_ALPHA_UNSAFE_TARGETS = Set.of("jpg", "pdf");

	private final ProcessingProperties processingProperties;
	private final ArtifactStorageService artifactStorageService;

	public ImageProcessingService(
		ProcessingProperties processingProperties,
		ArtifactStorageService artifactStorageService
	) {
		this.processingProperties = processingProperties;
		this.artifactStorageService = artifactStorageService;
	}

	public boolean isAvailable() {
		return resolveExecutable(this.processingProperties.getImageConvertExecutable()).isPresent()
			&& resolveExecutable(this.processingProperties.getFfmpegExecutable()).isPresent()
			&& resolveExecutable(this.processingProperties.getPotraceExecutable()).isPresent()
			&& resolveExecutable(this.processingProperties.getRawPreviewExecutable()).isPresent();
	}

	public ImageProcessingResult process(UUID jobId, StoredUpload upload, ImageProcessingRequest request) {
		var family = ProcessingFileFamilyResolver.detectFamily(upload);
		if (!"image".equals(family)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_CONVERT job принимает только image uploads.");
		}

		ensureAvailability();

		Path workingDirectory = null;
		try {
			workingDirectory = Files.createTempDirectory(this.processingProperties.getStorageRoot(), "image-processing-");

			var sourceRaster = rasterizeSource(upload, workingDirectory);
			var sourceRasterInfo = inspectRaster(sourceRaster.rasterPath());
			var transformedRaster = transformRaster(sourceRaster.rasterPath(), sourceRasterInfo, request, workingDirectory);
			var encodedResult = encodeArtifacts(upload, request, transformedRaster, workingDirectory);
			var warnings = deduplicateWarnings(sourceRaster.warnings(), transformedRaster.warnings(), encodedResult.warnings());
			var runtimeLabel = "%s -> %s".formatted(sourceRaster.adapterLabel(), encodedResult.adapterLabel());

			var artifacts = storeArtifacts(
				jobId,
				upload,
				request,
				sourceRaster.adapterLabel(),
				sourceRasterInfo,
				transformedRaster,
				encodedResult,
				runtimeLabel,
				warnings
			);
			return new ImageProcessingResult(artifacts, runtimeLabel, warnings);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить image processing workspace.", exception);
		}
		finally {
			deleteRecursively(workingDirectory);
		}
	}

	private List<StoredArtifact> storeArtifacts(
		UUID jobId,
		StoredUpload upload,
		ImageProcessingRequest request,
		String sourceAdapterLabel,
		RasterInfo sourceRasterInfo,
		TransformedRaster transformedRaster,
		EncodedImageResult encodedResult,
		String runtimeLabel,
		List<String> warnings
	) {
		var operationPrefix = "preview".equals(request.operation()) ? "image-preview" : "image-convert";
		var manifestFileName = "%s-manifest.json".formatted(operationPrefix);
		var manifest = new ImageProcessingManifest(
			upload.id(),
			upload.originalFileName(),
			ProcessingFileFamilyResolver.detectFamily(upload),
			request.operation(),
			sourceRasterInfo.width(),
			sourceRasterInfo.height(),
			transformedRaster.rasterInfo().width(),
			transformedRaster.rasterInfo().height(),
			sourceRasterInfo.hasTransparency(),
			encodedResult.resultMediaType(),
			encodedResult.previewMediaType(),
			encodedResult.outputExtension(),
			sourceAdapterLabel,
			encodedResult.adapterLabel(),
			runtimeLabel,
			Instant.now(),
			warnings
		);

		var manifestArtifact = this.artifactStorageService.storeJsonArtifact(
			jobId,
			operationPrefix + "-manifest",
			manifestFileName,
			manifest
		);
		if ("preview".equals(request.operation())) {
			var previewArtifact = this.artifactStorageService.storeFileArtifact(
				jobId,
				"image-preview-binary",
				encodedResult.previewFileName(),
				encodedResult.previewMediaType(),
				encodedResult.previewPath()
			);
			return List.of(manifestArtifact, previewArtifact);
		}

		var resultArtifact = this.artifactStorageService.storeFileArtifact(
			jobId,
			"image-convert-binary",
			encodedResult.resultFileName(),
			encodedResult.resultMediaType(),
			encodedResult.resultPath()
		);
		var previewArtifact = this.artifactStorageService.storeFileArtifact(
			jobId,
			"image-convert-preview",
			encodedResult.previewFileName(),
			encodedResult.previewMediaType(),
			encodedResult.previewPath()
		);
		return List.of(manifestArtifact, resultArtifact, previewArtifact);
	}

	private SourceRasterization rasterizeSource(StoredUpload upload, Path workingDirectory) {
		var extension = normalizeExtension(upload.extension());
		var sourceRasterPath = workingDirectory.resolve("source-raster.png");

		if (RAW_EXTENSIONS.contains(extension)) {
			return rasterizeRawSource(upload, sourceRasterPath, workingDirectory);
		}

		if ("heic".equals(extension) || "heif".equals(extension)) {
			try {
				convertToPng(
					List.of(upload.storagePath().toString() + "[0]"),
					sourceRasterPath,
					workingDirectory
				);
				return new SourceRasterization(
					sourceRasterPath,
					"HEIC server rasterization",
					List.of("HEIC source декодирован server-side и больше не зависит от browser-side HEIC adapter.")
				);
			}
			catch (ResponseStatusException exception) {
				ffmpegFrameToPng(upload.storagePath(), sourceRasterPath, workingDirectory);
				return new SourceRasterization(
					sourceRasterPath,
					"FFmpeg HEIC fallback",
					List.of(
						"HEIC source декодирован через ffmpeg fallback, потому что convert path не дал стабильный raster."
					)
				);
			}
		}

		if ("psd".equals(extension)) {
			convertToPng(
				List.of(upload.storagePath().toString() + "[0]"),
				sourceRasterPath,
				workingDirectory
			);
			return new SourceRasterization(
				sourceRasterPath,
				"PSD composite rasterization",
				List.of("PSD source сведен в единый composite raster на backend и больше не тянет browser-side PSD parser.")
			);
		}

		if (ILLUSTRATION_EXTENSIONS.contains(extension)) {
			execute(
				List.of(
					convertExecutable(),
					"-quiet",
					"-density",
					"192",
					upload.storagePath().toString() + "[0]",
					sourceRasterPath.toString()
				),
				workingDirectory,
				timeout()
			);
			ensureRenderableOutput(sourceRasterPath, "illustration raster");
			return new SourceRasterization(
				sourceRasterPath,
				"Illustration rasterization",
				List.of("AI/EPS source растеризован через backend Ghostscript/ImageMagick path вместо browser-side preview extraction.")
			);
		}

		convertToPng(
			List.of(upload.storagePath().toString() + "[0]"),
			sourceRasterPath,
			workingDirectory
		);
		return new SourceRasterization(sourceRasterPath, "ImageMagick raster pipeline", List.of());
	}

	private SourceRasterization rasterizeRawSource(StoredUpload upload, Path sourceRasterPath, Path workingDirectory) {
		var extractedPreviewPath = workingDirectory.resolve("raw-preview.jpg");
		var rawPreviewBytes = executeBinary(
			List.of(rawPreviewExecutable(), "-e", "-c", upload.storagePath().toString()),
			workingDirectory,
			timeout()
		).stdout();

		try {
			Files.write(extractedPreviewPath, rawPreviewBytes);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить временный RAW preview artifact.", exception);
		}

		convertToPng(List.of(extractedPreviewPath.toString() + "[0]"), sourceRasterPath, workingDirectory);
		return new SourceRasterization(
			sourceRasterPath,
			"LibRaw preview extraction",
			List.of(
				"RAW source идёт через embedded preview extraction на backend, а не через browser-side TIFF-ish decode path."
			)
		);
	}

	private TransformedRaster transformRaster(
		Path sourceRasterPath,
		RasterInfo sourceRasterInfo,
		ImageProcessingRequest request,
		Path workingDirectory
	) {
		var maxWidth = resolveMaxBound(request.maxWidth(), request.operation());
		var maxHeight = resolveMaxBound(request.maxHeight(), request.operation());
		var targetWidth = sourceRasterInfo.width();
		var targetHeight = sourceRasterInfo.height();

		if (maxWidth != null || maxHeight != null) {
			var scaled = scaleToFit(sourceRasterInfo.width(), sourceRasterInfo.height(), maxWidth, maxHeight);
			targetWidth = scaled.width();
			targetHeight = scaled.height();
		}

		if (targetWidth == sourceRasterInfo.width() && targetHeight == sourceRasterInfo.height()) {
			return new TransformedRaster(sourceRasterPath, sourceRasterInfo, List.of());
		}

		var transformedRasterPath = workingDirectory.resolve("transformed-raster.png");
		execute(
			List.of(
				convertExecutable(),
				"-quiet",
				sourceRasterPath.toString(),
				"-resize",
				"%sx%s>".formatted(targetWidth, targetHeight),
				transformedRasterPath.toString()
			),
			workingDirectory,
			timeout()
		);
		ensureRenderableOutput(transformedRasterPath, "transformed raster");
		var transformedInfo = inspectRaster(transformedRasterPath);

		var resizeLabel = request.presetLabel() != null && !request.presetLabel().isBlank()
			? "Preset %s уменьшил размерность: %sx%s -> %sx%s.".formatted(
				request.presetLabel(),
				sourceRasterInfo.width(),
				sourceRasterInfo.height(),
				transformedInfo.width(),
				transformedInfo.height()
			)
			: "Server-side resize уменьшил размерность: %sx%s -> %sx%s.".formatted(
				sourceRasterInfo.width(),
				sourceRasterInfo.height(),
				transformedInfo.width(),
				transformedInfo.height()
			);

		return new TransformedRaster(
			transformedRasterPath,
			transformedInfo,
			List.of(resizeLabel)
		);
	}

	private EncodedImageResult encodeArtifacts(
		StoredUpload upload,
		ImageProcessingRequest request,
		TransformedRaster transformedRaster,
		Path workingDirectory
	) {
		if ("preview".equals(request.operation())) {
			return new EncodedImageResult(
				transformedRaster.rasterPath(),
				derivedFileName(upload.originalFileName(), ".preview.png"),
				"image/png",
				transformedRaster.rasterPath(),
				derivedFileName(upload.originalFileName(), ".preview.png"),
				"image/png",
				"Viewer preview PNG",
				"png",
				List.of()
			);
		}

		var targetExtension = normalizeExtension(request.targetExtension());
		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var backgroundColor = normalizedBackgroundColor(request.backgroundColor());
		var quality = resolvePercentQuality(request.quality());
		var warnings = new ArrayList<String>();
		var rasterPath = transformedRaster.rasterPath();
		var rasterInfo = transformedRaster.rasterInfo();

		if (LOSSY_ALPHA_UNSAFE_TARGETS.contains(targetExtension) && rasterInfo.hasTransparency()) {
			warnings.add(
				"pdf".equals(targetExtension)
					? "Прозрачные области переведены в сплошной фон перед сборкой PDF."
					: "Прозрачные области переведены в сплошной фон перед JPG encode."
			);
		}

		return switch (targetExtension) {
			case "jpg" -> new EncodedImageResult(
				encodeViaConvert(
					workingDirectory.resolve(resultFileName),
					workingDirectory,
					rasterPath,
					List.of(
						"-background",
						backgroundColor,
						"-alpha",
						"remove",
						"-alpha",
						"off",
						"-quality",
						String.valueOf(quality)
					)
				),
				resultFileName,
				"image/jpeg",
				workingDirectory.resolve(resultFileName),
				resultFileName,
				"image/jpeg",
				"JPEG backend encode",
				"jpg",
				warnings
			);
			case "png" -> new EncodedImageResult(
				rasterPath,
				resultFileName,
				"image/png",
				rasterPath,
				resultFileName,
				"image/png",
				"PNG backend export",
				"png",
				warnings
			);
			case "webp" -> new EncodedImageResult(
				encodeViaConvert(
					workingDirectory.resolve(resultFileName),
					workingDirectory,
					rasterPath,
					List.of("-quality", String.valueOf(quality))
				),
				resultFileName,
				"image/webp",
				workingDirectory.resolve(resultFileName),
				resultFileName,
				"image/webp",
				"WebP backend encode",
				"webp",
				warnings
			);
			case "avif" -> {
				var resultPath = workingDirectory.resolve(resultFileName);
				encodeAvif(rasterPath, resultPath, quality, workingDirectory);
				yield new EncodedImageResult(
					resultPath,
					resultFileName,
					"image/avif",
					rasterPath,
					derivedFileName(upload.originalFileName(), ".preview.png"),
					"image/png",
					"FFmpeg AVIF encode",
					"avif",
					warnings
				);
			}
			case "tiff" -> {
				var resultPath = encodeViaConvert(
					workingDirectory.resolve(resultFileName),
					workingDirectory,
					rasterPath,
					List.of()
				);
				warnings.add(
					"TIFF собран как single-frame backend image без исходных metadata-блоков и без multi-page контейнера."
				);
				yield new EncodedImageResult(
					resultPath,
					resultFileName,
					"image/tiff",
					rasterPath,
					derivedFileName(upload.originalFileName(), ".preview.png"),
					"image/png",
					"TIFF backend encode",
					"tiff",
					warnings
				);
			}
			case "ico" -> {
				var squareEdge = Math.max(rasterInfo.width(), rasterInfo.height());
				var iconPreviewPath = workingDirectory.resolve("icon-preview.png");
				execute(
					List.of(
						convertExecutable(),
						"-quiet",
						rasterPath.toString(),
						"-background",
						"none",
						"-gravity",
						"center",
						"-extent",
						"%sx%s".formatted(squareEdge, squareEdge),
						iconPreviewPath.toString()
					),
					workingDirectory,
					timeout()
				);
				ensureRenderableOutput(iconPreviewPath, "icon preview");
				var resultPath = workingDirectory.resolve(resultFileName);
				execute(
					List.of(
						convertExecutable(),
						"-quiet",
						iconPreviewPath.toString(),
						"-define",
						"icon:auto-resize=16,32,48,64,128,256",
						resultPath.toString()
					),
					workingDirectory,
					timeout()
				);
				ensureRenderableOutput(resultPath, "ico artifact");
				if (rasterInfo.width() != rasterInfo.height()) {
					warnings.add(
						"ICO target собран на квадратном canvas-слое: исходник центрирован с прозрачными полями."
					);
				}
				yield new EncodedImageResult(
					resultPath,
					resultFileName,
					"image/x-icon",
					iconPreviewPath,
					derivedFileName(upload.originalFileName(), ".preview.png"),
					"image/png",
					"ICO backend pack",
					"ico",
					warnings
				);
			}
			case "svg" -> {
				var traceInputPath = workingDirectory.resolve("trace-input.pgm");
				execute(
					List.of(
						convertExecutable(),
						"-quiet",
						rasterPath.toString(),
						"-background",
						"white",
						"-alpha",
						"remove",
						"-alpha",
						"off",
						"-colorspace",
						"Gray",
						"-threshold",
						"60%",
						traceInputPath.toString()
					),
					workingDirectory,
					timeout()
				);
				ensureRenderableOutput(traceInputPath, "potrace input");
				var resultPath = workingDirectory.resolve(resultFileName);
				execute(
					List.of(
						potraceExecutable(),
						traceInputPath.toString(),
						"-s",
						"-o",
						resultPath.toString()
					),
					workingDirectory,
					timeout()
				);
				ensureRenderableOutput(resultPath, "svg trace");
				warnings.add(
					"SVG target собран через backend bitmap tracing, поэтому результат остаётся approximation, а не исходной векторной сценой."
				);
				yield new EncodedImageResult(
					resultPath,
					resultFileName,
					"image/svg+xml",
					resultPath,
					resultFileName,
					"image/svg+xml",
					"Potrace SVG trace",
					"svg",
					warnings
				);
			}
			case "pdf" -> {
				var resultPath = encodeViaConvert(
					workingDirectory.resolve(resultFileName),
					workingDirectory,
					rasterPath,
					List.of(
						"-background",
						backgroundColor,
						"-alpha",
						"remove",
						"-alpha",
						"off"
					)
				);
				warnings.add(
					"PDF собран как single-page raster document без отдельного текстового или векторного слоя."
				);
				yield new EncodedImageResult(
					resultPath,
					resultFileName,
					"application/pdf",
					resultPath,
					resultFileName,
					"application/pdf",
					"Raster PDF export",
					"pdf",
					warnings
				);
			}
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"IMAGE_CONVERT пока не поддерживает targetExtension %s.".formatted(targetExtension)
			);
		};
	}

	private void ffmpegFrameToPng(Path sourcePath, Path outputPath, Path workingDirectory) {
		execute(
			List.of(
				ffmpegExecutable(),
				"-v",
				"error",
				"-y",
				"-i",
				sourcePath.toString(),
				"-frames:v",
				"1",
				outputPath.toString()
			),
			workingDirectory,
			timeout()
		);
		ensureRenderableOutput(outputPath, "ffmpeg fallback raster");
	}

	private Path encodeViaConvert(Path outputPath, Path workingDirectory, Path rasterPath, List<String> encodeArguments) {
		var command = new ArrayList<String>();
		command.add(convertExecutable());
		command.add("-quiet");
		command.add(rasterPath.toString());
		command.addAll(encodeArguments);
		command.add(outputPath.toString());
		execute(command, workingDirectory, timeout());
		ensureRenderableOutput(outputPath, outputPath.getFileName().toString());
		return outputPath;
	}

	private void encodeAvif(Path rasterPath, Path outputPath, int quality, Path workingDirectory) {
		var crf = Math.max(12, Math.min(45, 46 - Math.round(quality * 0.34f)));
		execute(
			List.of(
				ffmpegExecutable(),
				"-v",
				"error",
				"-y",
				"-i",
				rasterPath.toString(),
				"-frames:v",
				"1",
				"-still-picture",
				"1",
				"-c:v",
				"libaom-av1",
				"-crf",
				String.valueOf(crf),
				"-pix_fmt",
				"yuva420p",
				outputPath.toString()
			),
			workingDirectory,
			timeout()
		);
		ensureRenderableOutput(outputPath, "avif artifact");
	}

	private void convertToPng(List<String> inputs, Path outputPath, Path workingDirectory) {
		var command = new ArrayList<String>();
		command.add(convertExecutable());
		command.add("-quiet");
		command.addAll(inputs);
		command.add(outputPath.toString());
		execute(command, workingDirectory, timeout());
		ensureRenderableOutput(outputPath, "png raster");
	}

	private Integer resolveMaxBound(Integer requestedBound, String operation) {
		if (requestedBound != null && requestedBound > 0) {
			return requestedBound;
		}

		if ("preview".equals(operation)) {
			return 4096;
		}

		return null;
	}

	private ScaledSize scaleToFit(int width, int height, Integer maxWidth, Integer maxHeight) {
		if ((maxWidth == null || width <= maxWidth) && (maxHeight == null || height <= maxHeight)) {
			return new ScaledSize(width, height);
		}

		double widthRatio = maxWidth == null ? Double.POSITIVE_INFINITY : (double) maxWidth / width;
		double heightRatio = maxHeight == null ? Double.POSITIVE_INFINITY : (double) maxHeight / height;
		double scale = Math.min(widthRatio, heightRatio);
		int scaledWidth = Math.max(1, (int) Math.floor(width * scale));
		int scaledHeight = Math.max(1, (int) Math.floor(height * scale));
		return new ScaledSize(scaledWidth, scaledHeight);
	}

	private RasterInfo inspectRaster(Path rasterPath) {
		try (var inputStream = Files.newInputStream(rasterPath)) {
			var image = ImageIO.read(inputStream);
			if (image == null) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Не удалось прочитать raster dimensions у собранного image artifact.");
			}

			return new RasterInfo(image.getWidth(), image.getHeight(), hasTransparency(image));
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать raster metadata у image artifact.", exception);
		}
	}

	private boolean hasTransparency(BufferedImage image) {
		if (!image.getColorModel().hasAlpha()) {
			return false;
		}

		for (int y = 0; y < image.getHeight(); y += 1) {
			for (int x = 0; x < image.getWidth(); x += 1) {
				int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
				if (alpha < 255) {
					return true;
				}
			}
		}

		return false;
	}

	private void ensureRenderableOutput(Path outputPath, String label) {
		try {
			if (!Files.exists(outputPath) || Files.size(outputPath) == 0L) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Команда завершилась без итогового image artifact: %s".formatted(label)
				);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать итоговый image artifact.", exception);
		}
	}

	private BinaryCommandResult executeBinary(List<String> command, Path workingDirectory, Duration timeout) {
		Process process = null;
		Thread outputReader = null;
		var output = new ByteArrayOutputStream();
		var outputFailure = new AtomicReference<IOException>();

		try {
			process = new ProcessBuilder(command)
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true)
				.start();

			var runningProcess = process;
			// stdout/stderr читаем отдельно, чтобы не зависнуть на внешнем processor
			// и одинаково работать и с бинарным stdout, и с текстовыми ошибками.
			outputReader = Thread.ofVirtual()
				.name("jack-image-processing-output")
				.start(() -> {
					try (var inputStream = runningProcess.getInputStream()) {
						inputStream.transferTo(output);
					}
					catch (IOException exception) {
						outputFailure.set(exception);
					}
				});

			var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

			if (!finished) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
				throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Image processing превысил допустимый timeout.");
			}

			outputReader.join(1_000L);
			if (outputFailure.get() != null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать вывод внешнего image processor.", outputFailure.get());
			}

			var stdout = output.toByteArray();
			if (process.exitValue() != 0) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Команда завершилась с кодом %s: %s".formatted(process.exitValue(), normalizeCommandOutput(stdout))
				);
			}

			return new BinaryCommandResult(stdout);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось запустить внешний image processor.", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image processing был прерван.", exception);
		}
	}

	private void execute(List<String> command, Path workingDirectory, Duration timeout) {
		executeBinary(command, workingDirectory, timeout);
	}

	private Duration timeout() {
		return Duration.ofSeconds(this.processingProperties.getImageProcessingTimeoutSeconds());
	}

	private String convertExecutable() {
		return resolveExecutable(this.processingProperties.getImageConvertExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "convert executable не найден в текущем backend окружении."));
	}

	private String ffmpegExecutable() {
		return resolveExecutable(this.processingProperties.getFfmpegExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ffmpeg executable не найден в текущем backend окружении."));
	}

	private String potraceExecutable() {
		return resolveExecutable(this.processingProperties.getPotraceExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "potrace executable не найден в текущем backend окружении."));
	}

	private String rawPreviewExecutable() {
		return resolveExecutable(this.processingProperties.getRawPreviewExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAW preview executable не найден в текущем backend окружении."));
	}

	private void ensureAvailability() {
		convertExecutable();
		ffmpegExecutable();
		potraceExecutable();
		rawPreviewExecutable();
	}

	private Optional<Path> resolveExecutable(String executable) {
		var candidate = Path.of(executable);
		if (candidate.isAbsolute()) {
			return Files.isExecutable(candidate) ? Optional.of(candidate) : Optional.empty();
		}

		var path = System.getenv("PATH");
		if (path == null || path.isBlank()) {
			return Optional.empty();
		}

		for (String directory : path.split(java.io.File.pathSeparator)) {
			if (directory.isBlank()) {
				continue;
			}

			var resolved = Path.of(directory).resolve(executable);
			if (Files.isExecutable(resolved)) {
				return Optional.of(resolved);
			}
		}

		return Optional.empty();
	}

	private void deleteRecursively(Path path) {
		if (path == null) {
			return;
		}

		try (var paths = Files.walk(path)) {
			paths.sorted((left, right) -> right.compareTo(left)).forEach(entry -> {
				try {
					Files.deleteIfExists(entry);
				}
				catch (IOException ignored) {
				}
			});
		}
		catch (IOException ignored) {
		}
	}

	private List<String> deduplicateWarnings(List<String>... warnings) {
		var ordered = new LinkedHashSet<String>();
		for (List<String> warningList : warnings) {
			for (String warning : warningList) {
				if (warning != null && !warning.isBlank()) {
					ordered.add(warning);
				}
			}
		}
		return List.copyOf(ordered);
	}

	private String normalizeCommandOutput(byte[] stdout) {
		var text = new String(stdout, StandardCharsets.UTF_8).trim();
		return text.isBlank() ? "без stderr/stdout" : text;
	}

	private int resolvePercentQuality(Double quality) {
		if (quality == null) {
			return 90;
		}

		var normalized = Math.max(0.1d, Math.min(1.0d, quality));
		return Math.max(1, Math.min(100, (int) Math.round(normalized * 100)));
	}

	private String normalizedBackgroundColor(String backgroundColor) {
		return backgroundColor != null && !backgroundColor.isBlank() ? backgroundColor : "#ffffff";
	}

	private String normalizeExtension(String extension) {
		if (extension == null) {
			return "";
		}

		var normalized = extension.trim().toLowerCase();
		return switch (normalized) {
			case "jpeg" -> "jpg";
			case "tif" -> "tiff";
			default -> normalized;
		};
	}

	private String replaceExtension(String fileName, String targetExtension) {
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
		return baseName + "." + targetExtension;
	}

	private String derivedFileName(String fileName, String suffix) {
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
		return baseName + suffix;
	}

	public record ImageProcessingResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel,
		List<String> warnings
	) {
	}

	public record ImageProcessingManifest(
		UUID uploadId,
		String originalFileName,
		String family,
		String operation,
		Integer sourceWidth,
		Integer sourceHeight,
		Integer width,
		Integer height,
		boolean sourceHasTransparency,
		String resultMediaType,
		String previewMediaType,
		String outputExtension,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		Instant generatedAt,
		List<String> warnings
	) {
	}

	private record SourceRasterization(
		Path rasterPath,
		String adapterLabel,
		List<String> warnings
	) {
	}

	private record TransformedRaster(
		Path rasterPath,
		RasterInfo rasterInfo,
		List<String> warnings
	) {
	}

	private record EncodedImageResult(
		Path resultPath,
		String resultFileName,
		String resultMediaType,
		Path previewPath,
		String previewFileName,
		String previewMediaType,
		String adapterLabel,
		String outputExtension,
		List<String> warnings
	) {
	}

	private record RasterInfo(
		int width,
		int height,
		boolean hasTransparency
	) {
	}

	private record ScaledSize(
		int width,
		int height
	) {
	}

	private record BinaryCommandResult(
		byte[] stdout
	) {
	}

}
