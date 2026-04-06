package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.PdfToolkitPayloads;
import com.keykomi.jack.processing.domain.PdfToolkitRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PdfToolkitService {

	private static final float STAMP_MARGIN = 28f;
	private static final float STAMP_TEXT_SIZE = 18f;
	private static final float STAMP_META_SIZE = 9f;
	private static final int OCR_RENDER_DPI = 220;
	private static final int REDACTION_RENDER_DPI = 160;

	private final ProcessingProperties processingProperties;
	private final ArtifactStorageService artifactStorageService;
	private final UploadStorageService uploadStorageService;

	public PdfToolkitService(
		ProcessingProperties processingProperties,
		ArtifactStorageService artifactStorageService,
		UploadStorageService uploadStorageService
	) {
		this.processingProperties = processingProperties;
		this.artifactStorageService = artifactStorageService;
		this.uploadStorageService = uploadStorageService;
	}

	public boolean isAvailable() {
		return true;
	}

	public boolean isAvailableFor(StoredUpload upload) {
		return isPdfUpload(upload);
	}

	public boolean isOcrAvailable() {
		return resolveExecutable(this.processingProperties.getTesseractExecutable()).isPresent();
	}

	public String defaultOcrLanguage() {
		var configuredLanguage = this.processingProperties.getPdfToolkitDefaultOcrLanguage();
		return configuredLanguage == null || configuredLanguage.isBlank() ? "eng" : configuredLanguage.trim();
	}

	public PdfToolkitResult process(
		UUID jobId,
		StoredUpload upload,
		PdfToolkitRequest request,
		ProgressCallback progressCallback
	) {
		if (!isPdfUpload(upload)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF_TOOLKIT job принимает только PDF uploads.");
		}
		if (request.operation() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF_TOOLKIT требует operation.");
		}

		Path workingDirectory = null;
		try {
			workingDirectory = Files.createTempDirectory(this.processingProperties.getStorageRoot(), "pdf-toolkit-");
			var output = switch (request.operation()) {
				case MERGE -> merge(upload, request, workingDirectory, progressCallback);
				case SPLIT -> split(upload, request, workingDirectory, progressCallback);
				case ROTATE -> rotate(upload, request, workingDirectory, progressCallback);
				case REORDER -> reorder(upload, request, workingDirectory, progressCallback);
				case OCR -> ocr(upload, request, workingDirectory, progressCallback);
				case SIGN -> sign(upload, request, workingDirectory, progressCallback);
				case REDACT -> redact(upload, request, workingDirectory, progressCallback);
				case PROTECT -> protect(upload, request, workingDirectory, progressCallback);
				case UNLOCK -> unlock(upload, request, workingDirectory, progressCallback);
			};
			var artifacts = storeArtifacts(jobId, upload, request.operation(), output);
			return new PdfToolkitResult(artifacts, output.runtimeLabel());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить PDF toolkit workspace.", exception);
		}
		finally {
			deleteRecursively(workingDirectory);
		}
	}

	private PdfToolkitOutput merge(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		var additionalUploads = resolveAdditionalPdfUploads(request.additionalUploadIds());
		if (additionalUploads.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merge требует хотя бы один дополнительный PDF upload.");
		}

		progressCallback.report(34, "Собираю merge order и открываю PDF inputs.");
		var sourceFacts = new ArrayList<PdfToolkitPayloads.PdfFact>();
		var warnings = new ArrayList<String>();
		var outputPath = workingDirectory.resolve(replaceExtension(upload.originalFileName(), "merged.pdf"));
		var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));

		try (
			var merged = new PDDocument();
			var primary = loadPdf(upload, request.currentPassword(), "merge source")
		) {
			var merger = new PDFMergerUtility();
			merger.appendDocument(merged, primary);

			int totalPages = primary.getNumberOfPages();
			sourceFacts.add(new PdfToolkitPayloads.PdfFact("Primary file", upload.originalFileName()));
			sourceFacts.add(new PdfToolkitPayloads.PdfFact("Primary pages", String.valueOf(primary.getNumberOfPages())));

			for (int index = 0; index < additionalUploads.size(); index += 1) {
				var additionalUpload = additionalUploads.get(index);
				progressCallback.report(
					40 + Math.min(28, index * 12),
					"Добавляю PDF #%s в merge stack.".formatted(index + 2)
				);
				try (var additionalDocument = loadPdf(additionalUpload, null, "merge source")) {
					totalPages += additionalDocument.getNumberOfPages();
					merger.appendDocument(merged, additionalDocument);
				}
			}

			merged.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			return new PdfToolkitOutput(
				outputPath,
				replaceExtension(upload.originalFileName(), "merged.pdf"),
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				totalPages,
				totalPages,
				sourceFacts,
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Merged PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(totalPages))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Inputs", String.valueOf(additionalUploads.size() + 1)),
					new PdfToolkitPayloads.PdfFact("Merge order", "Primary -> appended uploads")
				),
				"PDF direct intake",
				"PDF merge orchestrator",
				"PDF merge orchestrator",
				warnings,
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось объединить PDF документы.", exception);
		}
	}

	private PdfToolkitOutput split(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		progressCallback.report(34, "Разбираю ranges и подготавливаю split bundle.");
		try (var document = loadPdf(upload, request.currentPassword(), "split source")) {
			var sourcePageCount = document.getNumberOfPages();
			var ranges = resolveSplitRanges(request.splitRanges(), sourcePageCount);
			var partsDirectory = Files.createDirectories(workingDirectory.resolve("split-parts"));
			var partPaths = new ArrayList<Path>();

			for (int index = 0; index < ranges.size(); index += 1) {
				var range = ranges.get(index);
				progressCallback.report(
					42 + Math.min(28, index * 10),
					"Собираю split part %s из %s.".formatted(index + 1, ranges.size())
				);
				var partPath = partsDirectory.resolve(
					"%s.part-%02d.%s.pdf".formatted(baseFileName(upload.originalFileName()), index + 1, range.label())
				);
				try (var partDocument = new PDDocument()) {
					copyPages(document, partDocument, range.pages());
					partDocument.save(partPath.toFile());
				}
				partPaths.add(partPath);
			}

			var bundlePath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".split.zip");
			zipFiles(partPaths, bundlePath);
			var previewPath = partPaths.get(0);

			return new PdfToolkitOutput(
				bundlePath,
				baseFileName(upload.originalFileName()) + ".split.zip",
				"application/zip",
				previewPath,
				previewPath.getFileName().toString(),
				"application/pdf",
				sourcePageCount,
				null,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "ZIP bundle"),
					new PdfToolkitPayloads.PdfFact("Outputs", String.valueOf(partPaths.size()))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Split mode", request.splitRanges() == null || request.splitRanges().isEmpty() ? "Every page" : "Custom ranges"),
					new PdfToolkitPayloads.PdfFact("Preview part", previewPath.getFileName().toString())
				),
				"PDF direct intake",
				"PDF split orchestrator",
				"PDF split orchestrator",
				List.of(),
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось собрать split bundle для PDF.", exception);
		}
	}

	private PdfToolkitOutput rotate(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		var rotationDegrees = normalizeRotationRequest(request.rotationDegrees());
		progressCallback.report(34, "Подготавливаю rotate operation для выбранных страниц.");
		try (var document = loadPdf(upload, request.currentPassword(), "rotate source")) {
			var sourcePageCount = document.getNumberOfPages();
			var pages = resolvePageSelection(request.pageSelection(), sourcePageCount, true);
			for (int pageIndex : pages) {
				var page = document.getPage(pageIndex);
				page.setRotation(normalizeRotation(page.getRotation() + rotationDegrees));
			}

			var outputPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".rotated.pdf");
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			document.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			return new PdfToolkitOutput(
				outputPath,
				baseFileName(upload.originalFileName()) + ".rotated.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				sourcePageCount,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Rotated PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(sourcePageCount))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Rotation", rotationDegrees + "°"),
					new PdfToolkitPayloads.PdfFact("Touched pages", String.valueOf(pages.size()))
				),
				"PDF direct intake",
				"PDF page rotation",
				"PDF page rotation",
				List.of(),
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось повернуть страницы PDF.", exception);
		}
	}

	private PdfToolkitOutput reorder(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		progressCallback.report(34, "Проверяю новый page order и готовлю extraction contract.");
		try (
			var sourceDocument = loadPdf(upload, request.currentPassword(), "reorder source");
			var resultDocument = new PDDocument()
		) {
			var sourcePageCount = sourceDocument.getNumberOfPages();
			var pageOrder = resolvePageOrder(request.pageOrder(), sourcePageCount);
			copyPages(sourceDocument, resultDocument, pageOrder);

			var outputPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".reordered.pdf");
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			resultDocument.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			return new PdfToolkitOutput(
				outputPath,
				baseFileName(upload.originalFileName()) + ".reordered.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				pageOrder.size(),
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result pages", String.valueOf(pageOrder.size())),
					new PdfToolkitPayloads.PdfFact("Dropped pages", String.valueOf(Math.max(0, sourcePageCount - pageOrder.size())))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Operation", pageOrder.size() == sourcePageCount ? "Reorder" : "Extract subset"),
					new PdfToolkitPayloads.PdfFact("Page order", describePageOrder(pageOrder))
				),
				"PDF direct intake",
				"PDF page reorder/extract",
				"PDF page reorder/extract",
				List.of(),
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось собрать reordered PDF.", exception);
		}
	}

	private PdfToolkitOutput ocr(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		var tesseractExecutable = resolveExecutable(this.processingProperties.getTesseractExecutable())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR route требует доступного tesseract executable."));
		var language = request.ocrLanguage() == null || request.ocrLanguage().isBlank()
			? defaultOcrLanguage()
			: request.ocrLanguage().trim();

		try (var document = loadPdf(upload, request.currentPassword(), "ocr source")) {
			var renderer = new PDFRenderer(document);
			var sourcePageCount = document.getNumberOfPages();
			var pagePdfPaths = new ArrayList<Path>();
			var searchableText = new ArrayList<String>();

			for (int pageIndex = 0; pageIndex < sourcePageCount; pageIndex += 1) {
				progressCallback.report(
					32 + Math.min(40, (pageIndex * 40) / Math.max(1, sourcePageCount)),
					"Выполняю OCR для страницы %s из %s.".formatted(pageIndex + 1, sourcePageCount)
				);
				var renderedPage = renderer.renderImageWithDPI(pageIndex, OCR_RENDER_DPI);
				var imagePath = workingDirectory.resolve("ocr-page-%03d.png".formatted(pageIndex + 1));
				ImageIO.write(renderedPage, "PNG", imagePath.toFile());

				var outputBase = workingDirectory.resolve("ocr-page-%03d".formatted(pageIndex + 1));
				execute(
					List.of(
						tesseractExecutable.toString(),
						imagePath.toString(),
						outputBase.toString(),
						"-l",
						language,
						"pdf",
						"txt"
					),
					workingDirectory,
					timeout(),
					"Не удалось запустить OCR processor."
				);

				pagePdfPaths.add(outputBase.resolveSibling(outputBase.getFileName() + ".pdf"));
				var textPath = outputBase.resolveSibling(outputBase.getFileName() + ".txt");
				searchableText.add(Files.exists(textPath) ? Files.readString(textPath, StandardCharsets.UTF_8).trim() : "");
			}

			progressCallback.report(76, "Собираю searchable PDF и text export после OCR.");
			var resultPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".ocr.pdf");
			mergePdfFiles(pagePdfPaths, resultPath);
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			Files.copy(resultPath, previewPath);

			var textPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".ocr.txt");
			Files.writeString(textPath, String.join(System.lineSeparator() + System.lineSeparator(), searchableText), StandardCharsets.UTF_8);

			var blankPages = searchableText.stream().filter(String::isBlank).count();
			var warnings = new ArrayList<String>();
			if (blankPages > 0) {
				warnings.add("Часть страниц OCR не дала читаемый text layer. Проверь scan quality или язык OCR.");
			}

			return new PdfToolkitOutput(
				resultPath,
				baseFileName(upload.originalFileName()) + ".ocr.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				sourcePageCount,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Searchable PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(sourcePageCount))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("OCR language", language),
					new PdfToolkitPayloads.PdfFact("TXT export", textPath.getFileName().toString())
				),
				"PDF direct intake",
				"OCR searchable PDF",
				"OCR searchable PDF",
				warnings,
				List.of(new SupplementaryArtifact("pdf-toolkit-text", textPath, textPath.getFileName().toString(), "text/plain"))
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось выполнить OCR для PDF.", exception);
		}
	}

	private PdfToolkitOutput sign(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		progressCallback.report(34, "Подготавливаю visible signature/stamp placement.");
		var signatureImage = resolveSignatureImage(request.signatureImageUploadId());
		var signatureText = request.signatureText() == null ? "" : request.signatureText().trim();
		if ((signatureImage == null) && signatureText.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-sign требует signatureText или signatureImageUploadId.");
		}

		try (var document = loadPdf(upload, request.currentPassword(), "sign source")) {
			var sourcePageCount = document.getNumberOfPages();
			var pages = resolvePageSelection(request.pageSelection(), sourcePageCount, false);
			for (int pageIndex : pages) {
				var page = document.getPage(pageIndex);
				var mediaBox = page.getCropBox();
				var box = resolveStampBox(mediaBox, request.signaturePlacement(), signatureImage != null);
				try (var content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
					drawStampBackground(content, box);
					if (signatureImage != null) {
						var pdImage = LosslessFactory.createFromImage(document, signatureImage);
						content.drawImage(pdImage, box.x() + 8f, box.y() + 16f, box.width() - 16f, box.height() - 28f);
					}
					if (!signatureText.isBlank()) {
						writeStampText(content, signatureText, box, request.includeSignatureDate());
					}
				}
			}

			var outputPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".signed.pdf");
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			document.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			return new PdfToolkitOutput(
				outputPath,
				baseFileName(upload.originalFileName()) + ".signed.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				sourcePageCount,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Stamped PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(sourcePageCount))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Placement", normalizePlacement(request.signaturePlacement())),
					new PdfToolkitPayloads.PdfFact("Touched pages", String.valueOf(pages.size()))
				),
				"PDF direct intake",
				"Visible PDF signature/stamp",
				"Visible PDF signature/stamp",
				List.of("Этот e-sign flow добавляет видимый stamp-mark и не является certificate-based digital signature."),
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось добавить visible signature/stamp в PDF.", exception);
		}
	}

	private PdfToolkitOutput redact(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		var terms = normalizeTerms(request.redactTerms());
		if (terms.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Redaction требует непустой список term-ов.");
		}

		progressCallback.report(32, "Ищу term matches в PDF text layer перед redaction.");
		try (
			var sourceDocument = loadPdf(upload, request.currentPassword(), "redaction source");
			var resultDocument = new PDDocument()
		) {
			var sourcePageCount = sourceDocument.getNumberOfPages();
			var targetPages = new LinkedHashSet<>(resolvePageSelection(request.pageSelection(), sourcePageCount, true));
			var locator = new RedactionLocator(terms);
			var matchesByPage = locator.locate(sourceDocument);
			var totalMatches = 0;

			progressCallback.report(54, "Растризую страницы и применяю необратимую redaction mask.");
			// Здесь сознательно пересобираем PDF из page images, чтобы скрытый текст и старые content stream
			// не остались в финальном artifact под чёрной плашкой.
			var renderer = new PDFRenderer(sourceDocument);
			for (int pageIndex = 0; pageIndex < sourcePageCount; pageIndex += 1) {
				var sourcePage = sourceDocument.getPage(pageIndex);
				var pageImage = renderer.renderImageWithDPI(pageIndex, REDACTION_RENDER_DPI);
				if (targetPages.contains(pageIndex)) {
					var pageMatches = matchesByPage.getOrDefault(pageIndex, List.of());
					totalMatches += pageMatches.size();
					applyRedactionMasks(pageImage, sourcePage.getCropBox(), pageMatches);
				}
				appendRasterPage(resultDocument, sourcePage.getCropBox(), pageImage);
			}

			if (totalMatches == 0) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ни один redact term не был найден в доступном PDF text layer.");
			}

			var outputPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".redacted.pdf");
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			resultDocument.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			return new PdfToolkitOutput(
				outputPath,
				baseFileName(upload.originalFileName()) + ".redacted.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				sourcePageCount,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Redacted PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(sourcePageCount))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Terms", String.valueOf(terms.size())),
					new PdfToolkitPayloads.PdfFact("Matches", String.valueOf(totalMatches))
				),
				"PDF direct intake",
				"Rasterized PDF redaction",
				"Rasterized PDF redaction",
				List.of(
					"Redaction пересобирает страницы как raster PDF, поэтому исходный selectable text, vector layer и embedded annotations больше не сохраняются.",
					"Term-based redaction зависит от доступного text layer: для image-only scans сначала нужен OCR."
				),
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось применить redaction к PDF.", exception);
		}
	}

	private PdfToolkitOutput protect(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		if (request.ownerPassword() == null || request.ownerPassword().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Protect operation требует ownerPassword.");
		}

		progressCallback.report(34, "Применяю password protection и permission policy.");
		try (var document = loadPdf(upload, request.currentPassword(), "protect source")) {
			var accessPermission = new AccessPermission();
			accessPermission.setCanPrint(resolvePermissionValue(request.allowPrinting(), true));
			accessPermission.setCanExtractContent(resolvePermissionValue(request.allowCopying(), true));
			accessPermission.setCanModify(resolvePermissionValue(request.allowModifying(), false));

			var protectionPolicy = new StandardProtectionPolicy(
				request.ownerPassword(),
				request.userPassword() == null ? "" : request.userPassword(),
				accessPermission
			);
			protectionPolicy.setEncryptionKeyLength(256);
			document.protect(protectionPolicy);

			var sourcePageCount = document.getNumberOfPages();
			var outputPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".protected.pdf");
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			document.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			var warnings = new ArrayList<String>();
			if (request.userPassword() == null || request.userPassword().isBlank()) {
				warnings.add("User password не задан: документ будет открываться без пароля, но с owner-controlled permission policy.");
			}

			return new PdfToolkitOutput(
				outputPath,
				baseFileName(upload.originalFileName()) + ".protected.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				sourcePageCount,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Protected PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(sourcePageCount))
				),
				List.of(
					new PdfToolkitPayloads.PdfFact("Can print", yesNo(accessPermission.canPrint())),
					new PdfToolkitPayloads.PdfFact("Can copy", yesNo(accessPermission.canExtractContent())),
					new PdfToolkitPayloads.PdfFact("Can modify", yesNo(accessPermission.canModify()))
				),
				"PDF direct intake",
				"PDF password protection",
				"PDF password protection",
				warnings,
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось защитить PDF паролем.", exception);
		}
	}

	private PdfToolkitOutput unlock(
		StoredUpload upload,
		PdfToolkitRequest request,
		Path workingDirectory,
		ProgressCallback progressCallback
	) {
		if (request.currentPassword() == null || request.currentPassword().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unlock operation требует currentPassword.");
		}

		progressCallback.report(34, "Снимаю protection policy и пересохраняю PDF.");
		try (var document = loadPdf(upload, request.currentPassword(), "unlock source")) {
			var sourcePageCount = document.getNumberOfPages();
			document.setAllSecurityToBeRemoved(true);

			var outputPath = workingDirectory.resolve(baseFileName(upload.originalFileName()) + ".unlocked.pdf");
			var previewPath = workingDirectory.resolve(derivedPreviewFileName(upload.originalFileName(), "pdf"));
			document.save(outputPath.toFile());
			Files.copy(outputPath, previewPath);

			return new PdfToolkitOutput(
				outputPath,
				baseFileName(upload.originalFileName()) + ".unlocked.pdf",
				"application/pdf",
				previewPath,
				derivedPreviewFileName(upload.originalFileName(), "pdf"),
				"application/pdf",
				sourcePageCount,
				sourcePageCount,
				List.of(new PdfToolkitPayloads.PdfFact("Source pages", String.valueOf(sourcePageCount))),
				List.of(
					new PdfToolkitPayloads.PdfFact("Result type", "Unlocked PDF"),
					new PdfToolkitPayloads.PdfFact("Pages", String.valueOf(sourcePageCount))
				),
				List.of(new PdfToolkitPayloads.PdfFact("Protection removed", "Yes")),
				"PDF direct intake",
				"PDF unlock flow",
				"PDF unlock flow",
				List.of(),
				List.of()
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось снять password protection с PDF.", exception);
		}
	}

	private List<StoredArtifact> storeArtifacts(
		UUID jobId,
		StoredUpload upload,
		PdfToolkitRequest.Operation operation,
		PdfToolkitOutput output
	) {
		var manifest = new PdfToolkitPayloads.PdfToolkitManifest(
			upload.id(),
			upload.originalFileName(),
			operation.name(),
			output.resultFileName(),
			output.resultMediaType(),
			output.previewFileName(),
			output.previewMediaType(),
			"document",
			output.sourcePageCount(),
			output.resultPageCount(),
			output.sourceAdapterLabel(),
			output.targetAdapterLabel(),
			output.runtimeLabel(),
			output.sourceFacts(),
			output.resultFacts(),
			output.operationFacts(),
			output.warnings(),
			Instant.now()
		);

		var artifacts = new ArrayList<StoredArtifact>();
		artifacts.add(this.artifactStorageService.storeJsonArtifact(jobId, "pdf-toolkit-manifest", "pdf-toolkit-manifest.json", manifest));
		artifacts.add(this.artifactStorageService.storeFileArtifact(jobId, "pdf-toolkit-binary", output.resultFileName(), output.resultMediaType(), output.resultPath()));
		artifacts.add(this.artifactStorageService.storeFileArtifact(jobId, "pdf-toolkit-preview", output.previewFileName(), output.previewMediaType(), output.previewPath()));
		for (var supplementaryArtifact : output.supplementaryArtifacts()) {
			artifacts.add(
				this.artifactStorageService.storeFileArtifact(
					jobId,
					supplementaryArtifact.kind(),
					supplementaryArtifact.fileName(),
					supplementaryArtifact.mediaType(),
					supplementaryArtifact.path()
				)
			);
		}
		return List.copyOf(artifacts);
	}

	private List<StoredUpload> resolveAdditionalPdfUploads(List<UUID> uploadIds) {
		if (uploadIds == null || uploadIds.isEmpty()) {
			return List.of();
		}

		var uploads = new ArrayList<StoredUpload>();
		for (UUID uploadId : uploadIds) {
			var upload = this.uploadStorageService.getRequiredUpload(uploadId);
			if (!isPdfUpload(upload)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merge принимает только дополнительные PDF uploads.");
			}
			uploads.add(upload);
		}
		return List.copyOf(uploads);
	}

	private BufferedImage resolveSignatureImage(UUID signatureImageUploadId) {
		if (signatureImageUploadId == null) {
			return null;
		}

		var upload = this.uploadStorageService.getRequiredUpload(signatureImageUploadId);
		if (!"image".equals(ProcessingFileFamilyResolver.detectFamily(upload))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signatureImageUploadId должен указывать на image upload.");
		}

		try {
			return ImageIO.read(upload.storagePath().toFile());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать signature image upload.", exception);
		}
	}

	private PDDocument loadPdf(StoredUpload upload, String password, String operationLabel) {
		try {
			if (password != null && !password.isBlank()) {
				return Loader.loadPDF(upload.storagePath().toFile(), password);
			}
			return Loader.loadPDF(upload.storagePath().toFile());
		}
		catch (InvalidPasswordException exception) {
			throw new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"PDF для %s требует корректный password.".formatted(operationLabel),
				exception
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось открыть PDF для %s.".formatted(operationLabel), exception);
		}
	}

	private List<PageRange> resolveSplitRanges(List<String> splitRanges, int pageCount) {
		if (splitRanges == null || splitRanges.isEmpty()) {
			var ranges = new ArrayList<PageRange>();
			for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
				ranges.add(new PageRange(List.of(pageIndex), "page-%s".formatted(pageIndex + 1)));
			}
			return List.copyOf(ranges);
		}

		var ranges = new ArrayList<PageRange>();
		for (String rawRange : splitRanges) {
			if (rawRange == null || rawRange.isBlank()) {
				continue;
			}
			var pages = parsePageExpression(rawRange, pageCount, false);
			ranges.add(new PageRange(pages, rawRange.trim().replaceAll("\\s+", "")));
		}
		if (ranges.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Split ranges не должны быть пустыми.");
		}
		return List.copyOf(ranges);
	}

	private List<Integer> resolvePageSelection(String pageSelection, int pageCount, boolean defaultAll) {
		if (pageSelection == null || pageSelection.isBlank()) {
			if (!defaultAll) {
				return List.of(Math.max(0, pageCount - 1));
			}
			var pages = new ArrayList<Integer>(pageCount);
			for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
				pages.add(pageIndex);
			}
			return List.copyOf(pages);
		}
		return parsePageExpression(pageSelection, pageCount, false);
	}

	private List<Integer> resolvePageOrder(List<Integer> pageOrder, int pageCount) {
		if (pageOrder == null || pageOrder.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page extract/reorder требует непустой pageOrder.");
		}

		var seenPages = new LinkedHashSet<Integer>();
		for (Integer rawPage : pageOrder) {
			if (rawPage == null || rawPage < 1 || rawPage > pageCount) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page order содержит страницу вне диапазона документа.");
			}
			if (!seenPages.add(rawPage - 1)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page order не должен содержать дубли страниц.");
			}
		}
		return List.copyOf(seenPages);
	}

	private List<Integer> parsePageExpression(String expression, int pageCount, boolean allowDuplicates) {
		var pages = new ArrayList<Integer>();
		var seenPages = new LinkedHashSet<Integer>();
		for (String token : expression.split(",")) {
			var normalizedToken = token.trim();
			if (normalizedToken.isBlank()) {
				continue;
			}
			if (normalizedToken.contains("-")) {
				var bounds = normalizedToken.split("-", 2);
				var start = parsePageNumber(bounds[0], pageCount);
				var end = parsePageNumber(bounds[1], pageCount);
				if (end < start) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page range должен идти по возрастанию.");
				}
				for (int page = start; page <= end; page += 1) {
					appendPage(pages, seenPages, page, allowDuplicates);
				}
				continue;
			}
			appendPage(pages, seenPages, parsePageNumber(normalizedToken, pageCount), allowDuplicates);
		}
		if (pages.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page selection не должна быть пустой.");
		}
		return List.copyOf(pages);
	}

	private void appendPage(List<Integer> pages, Set<Integer> seenPages, int zeroBasedPage, boolean allowDuplicates) {
		if (allowDuplicates || seenPages.add(zeroBasedPage)) {
			pages.add(zeroBasedPage);
		}
	}

	private int parsePageNumber(String value, int pageCount) {
		try {
			var pageNumber = Integer.parseInt(value.trim());
			if (pageNumber < 1 || pageNumber > pageCount) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page number должен попадать в диапазон PDF.");
			}
			return pageNumber - 1;
		}
		catch (NumberFormatException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page selection содержит невалидный номер страницы.", exception);
		}
	}

	private int normalizeRotationRequest(Integer rotationDegrees) {
		if (rotationDegrees == null || (rotationDegrees != 90 && rotationDegrees != 180 && rotationDegrees != 270)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rotate operation принимает только 90, 180 или 270 градусов.");
		}
		return rotationDegrees;
	}

	private int normalizeRotation(int value) {
		var normalized = value % 360;
		return normalized < 0 ? normalized + 360 : normalized;
	}

	private List<String> normalizeTerms(List<String> rawTerms) {
		if (rawTerms == null) {
			return List.of();
		}
		var terms = new LinkedHashSet<String>();
		for (String rawTerm : rawTerms) {
			if (rawTerm == null) {
				continue;
			}
			var normalized = rawTerm.trim();
			if (!normalized.isBlank()) {
				terms.add(normalized);
			}
		}
		return List.copyOf(terms);
	}

	private boolean resolvePermissionValue(Boolean requestedValue, boolean defaultValue) {
		return requestedValue == null ? defaultValue : requestedValue;
	}

	private String yesNo(boolean value) {
		return value ? "Yes" : "No";
	}

	private void copyPages(PDDocument source, PDDocument target, List<Integer> pageIndexes) throws IOException {
		for (int pageIndex : pageIndexes) {
			target.importPage(source.getPage(pageIndex));
		}
	}

	private void mergePdfFiles(List<Path> sourceFiles, Path outputPath) throws IOException {
		try (var merged = new PDDocument()) {
			var merger = new PDFMergerUtility();
			for (Path sourceFile : sourceFiles) {
				try (var document = Loader.loadPDF(sourceFile.toFile())) {
					merger.appendDocument(merged, document);
				}
			}
			merged.save(outputPath.toFile());
		}
	}

	private void zipFiles(List<Path> sourceFiles, Path outputPath) throws IOException {
		try (var outputStream = Files.newOutputStream(outputPath); var zipOutputStream = new ZipOutputStream(outputStream)) {
			for (Path sourceFile : sourceFiles) {
				zipOutputStream.putNextEntry(new ZipEntry(sourceFile.getFileName().toString()));
				Files.copy(sourceFile, zipOutputStream);
				zipOutputStream.closeEntry();
			}
		}
	}

	private void appendRasterPage(PDDocument document, PDRectangle sourceBox, BufferedImage pageImage) throws IOException {
		var page = new PDPage(sourceBox);
		document.addPage(page);
		var image = LosslessFactory.createFromImage(document, pageImage);
		try (var contentStream = new PDPageContentStream(document, page)) {
			contentStream.drawImage(image, 0, 0, sourceBox.getWidth(), sourceBox.getHeight());
		}
	}

	private void applyRedactionMasks(
		BufferedImage image,
		PDRectangle pageBox,
		List<RedactionRect> matches
	) {
		if (matches.isEmpty()) {
			return;
		}

		var graphics = image.createGraphics();
		try {
			graphics.setColor(Color.BLACK);
			graphics.setStroke(new BasicStroke(1f));
			for (var match : matches) {
				var x = Math.round(match.x() * image.getWidth() / pageBox.getWidth());
				var y = Math.round(match.y() * image.getHeight() / pageBox.getHeight());
				var width = Math.max(2, Math.round(match.width() * image.getWidth() / pageBox.getWidth()));
				var height = Math.max(2, Math.round(match.height() * image.getHeight() / pageBox.getHeight()));
				graphics.fillRect(x, y, width, height);
			}
		}
		finally {
			graphics.dispose();
		}
	}

	private StampBox resolveStampBox(PDRectangle pageBox, String placement, boolean imageSignature) {
		var normalizedPlacement = normalizePlacement(placement);
		var boxWidth = imageSignature ? Math.min(190f, pageBox.getWidth() * 0.34f) : Math.min(220f, pageBox.getWidth() * 0.42f);
		var boxHeight = imageSignature ? 94f : 78f;
		return switch (normalizedPlacement) {
			case "top-left" -> new StampBox(STAMP_MARGIN, pageBox.getHeight() - STAMP_MARGIN - boxHeight, boxWidth, boxHeight);
			case "top-right" -> new StampBox(pageBox.getWidth() - STAMP_MARGIN - boxWidth, pageBox.getHeight() - STAMP_MARGIN - boxHeight, boxWidth, boxHeight);
			case "bottom-left" -> new StampBox(STAMP_MARGIN, STAMP_MARGIN, boxWidth, boxHeight);
			case "center" -> new StampBox((pageBox.getWidth() - boxWidth) / 2f, (pageBox.getHeight() - boxHeight) / 2f, boxWidth, boxHeight);
			default -> new StampBox(pageBox.getWidth() - STAMP_MARGIN - boxWidth, STAMP_MARGIN, boxWidth, boxHeight);
		};
	}

	private String normalizePlacement(String placement) {
		if (placement == null || placement.isBlank()) {
			return "bottom-right";
		}
		return switch (placement.trim().toLowerCase(Locale.ROOT)) {
			case "top-left", "top-right", "bottom-left", "bottom-right", "center" -> placement.trim().toLowerCase(Locale.ROOT);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signaturePlacement должен быть top-left, top-right, bottom-left, bottom-right или center.");
		};
	}

	private void drawStampBackground(PDPageContentStream contentStream, StampBox box) throws IOException {
		contentStream.setNonStrokingColor(new Color(255, 249, 239));
		contentStream.addRect(box.x(), box.y(), box.width(), box.height());
		contentStream.fill();
		contentStream.setStrokingColor(new Color(29, 92, 85));
		contentStream.setLineWidth(1.2f);
		contentStream.addRect(box.x(), box.y(), box.width(), box.height());
		contentStream.stroke();
	}

	private void writeStampText(
		PDPageContentStream contentStream,
		String signatureText,
		StampBox box,
		Boolean includeSignatureDate
	) throws IOException {
		contentStream.beginText();
		contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), STAMP_TEXT_SIZE);
		contentStream.setNonStrokingColor(new Color(16, 36, 38));
		contentStream.newLineAtOffset(box.x() + 12f, box.y() + box.height() - 26f);
		contentStream.showText(trimForPdf(signatureText, 24));
		contentStream.endText();

		contentStream.beginText();
		contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), STAMP_META_SIZE);
		contentStream.setNonStrokingColor(new Color(85, 105, 103));
		contentStream.newLineAtOffset(box.x() + 12f, box.y() + 12f);
		contentStream.showText("Visible PDF signature stamp");
		contentStream.endText();

		if (Boolean.TRUE.equals(includeSignatureDate)) {
			contentStream.beginText();
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), STAMP_META_SIZE);
			contentStream.setNonStrokingColor(new Color(85, 105, 103));
			contentStream.newLineAtOffset(box.x() + 12f, box.y() + 25f);
			contentStream.showText("Signed at " + Instant.now().toString());
			contentStream.endText();
		}
	}

	private String trimForPdf(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
	}

	private Duration timeout() {
		return Duration.ofSeconds(Math.max(30L, this.processingProperties.getPdfToolkitTimeoutSeconds()));
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

	private void execute(List<String> command, Path workingDirectory, Duration timeout, String startupErrorMessage) {
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
			outputReader = Thread.ofVirtual()
				.name("jack-pdf-toolkit-output")
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
				throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "PDF toolkit operation превысила допустимый timeout.");
			}

			outputReader.join(1_000L);
			if (outputFailure.get() != null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать вывод внешнего PDF processor.", outputFailure.get());
			}
			if (process.exitValue() != 0) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Команда завершилась с кодом %s: %s".formatted(process.exitValue(), normalizeCommandOutput(output.toByteArray()))
				);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, startupErrorMessage, exception);
		}
		catch (InterruptedException exception) {
			if (process != null) {
				process.destroy();
			}
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF toolkit operation была прервана.", exception);
		}
	}

	private String normalizeCommandOutput(byte[] bytes) {
		var output = new String(bytes, StandardCharsets.UTF_8).trim();
		return output.isBlank() ? "no output" : output.replaceAll("\\s+", " ");
	}

	private boolean isPdfUpload(StoredUpload upload) {
		return "pdf".equals(normalizeExtension(upload.extension()));
	}

	private String normalizeExtension(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
	}

	private String baseFileName(String fileName) {
		var lastDotIndex = fileName.lastIndexOf('.');
		return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
	}

	private String replaceExtension(String fileName, String newExtension) {
		return baseFileName(fileName) + "." + newExtension;
	}

	private String derivedPreviewFileName(String fileName, String extension) {
		return baseFileName(fileName) + ".preview." + extension;
	}

	private String describePageOrder(List<Integer> pageOrder) {
		var labels = new ArrayList<String>(pageOrder.size());
		for (int pageIndex : pageOrder) {
			labels.add(String.valueOf(pageIndex + 1));
			if (labels.size() >= 8 && pageOrder.size() > 8) {
				labels.add("…");
				break;
			}
		}
		return String.join(", ", labels);
	}

	private void deleteRecursively(Path path) {
		if (path == null || !Files.exists(path)) {
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

	public interface ProgressCallback {

		void report(int progressPercent, String message);

	}

	public record PdfToolkitResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel
	) {
	}

	private record PdfToolkitOutput(
		Path resultPath,
		String resultFileName,
		String resultMediaType,
		Path previewPath,
		String previewFileName,
		String previewMediaType,
		Integer sourcePageCount,
		Integer resultPageCount,
		List<PdfToolkitPayloads.PdfFact> sourceFacts,
		List<PdfToolkitPayloads.PdfFact> resultFacts,
		List<PdfToolkitPayloads.PdfFact> operationFacts,
		String sourceAdapterLabel,
		String targetAdapterLabel,
		String runtimeLabel,
		List<String> warnings,
		List<SupplementaryArtifact> supplementaryArtifacts
	) {
	}

	private record SupplementaryArtifact(
		String kind,
		Path path,
		String fileName,
		String mediaType
	) {
	}

	private record PageRange(
		List<Integer> pages,
		String label
	) {
	}

	private record StampBox(
		float x,
		float y,
		float width,
		float height
	) {
	}

	private record RedactionRect(
		float x,
		float y,
		float width,
		float height
	) {
	}

	private static final class RedactionLocator extends PDFTextStripper {

		private final List<String> terms;
		private final Map<Integer, List<RedactionRect>> matchesByPage = new LinkedHashMap<>();

		private RedactionLocator(List<String> terms) throws IOException {
			this.terms = terms.stream().map(term -> term.toLowerCase(Locale.ROOT)).toList();
			setSortByPosition(true);
		}

		private Map<Integer, List<RedactionRect>> locate(PDDocument document) throws IOException {
			this.matchesByPage.clear();
			try (var sink = new OutputStreamWriter(OutputStream.nullOutputStream(), StandardCharsets.UTF_8)) {
				writeText(document, sink);
			}
			return Map.copyOf(this.matchesByPage);
		}

		@Override
		protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
			if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
				return;
			}

			var lowered = text.toLowerCase(Locale.ROOT);
			for (String term : this.terms) {
				int start = 0;
				while (start < lowered.length()) {
					var matchIndex = lowered.indexOf(term, start);
					if (matchIndex < 0) {
						break;
					}

					var matchEnd = Math.min(matchIndex + term.length(), textPositions.size());
					if (matchIndex < textPositions.size() && matchEnd > matchIndex) {
						resolveRect(textPositions.subList(matchIndex, matchEnd)).ifPresent(rect ->
							this.matchesByPage.computeIfAbsent(getCurrentPageNo() - 1, ignored -> new ArrayList<>()).add(rect)
						);
					}
					start = matchIndex + term.length();
				}
			}
		}

		private Optional<RedactionRect> resolveRect(List<TextPosition> textPositions) {
			float minX = Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxX = 0f;
			float maxY = 0f;

			for (TextPosition textPosition : textPositions) {
				minX = Math.min(minX, textPosition.getXDirAdj());
				minY = Math.min(minY, textPosition.getYDirAdj() - textPosition.getHeightDir());
				maxX = Math.max(maxX, textPosition.getXDirAdj() + textPosition.getWidthDirAdj());
				maxY = Math.max(maxY, textPosition.getYDirAdj());
			}

			if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) {
				return Optional.empty();
			}

			var padding = 2.4f;
			return Optional.of(
				new RedactionRect(
					Math.max(0f, minX - padding),
					Math.max(0f, minY - padding),
					(maxX - minX) + padding * 2f,
					(maxY - minY) + padding * 2f
				)
			);
		}

	}

}
