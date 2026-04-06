package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.DocumentPreviewPayload;
import com.keykomi.jack.processing.domain.OfficeConversionRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
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
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

@Service
public class OfficeConversionService {

	private static final float PDF_MARGIN = 42f;
	private static final float PDF_BODY_FONT_SIZE = 11f;
	private static final float PDF_HEADING_FONT_SIZE = 16f;
	private static final float PDF_SUBHEADING_FONT_SIZE = 13f;
	private static final float PDF_LINE_HEIGHT = 15f;
	private static final int CONTACT_SHEET_PADDING = 24;
	private static final int CONTACT_SHEET_GAP = 18;
	private static final int PDF_RENDER_DPI = 144;
	private static final int SLIDE_VIDEO_SECONDS = 2;
	private static final Set<String> NARRATIVE_SOURCE_EXTENSIONS = Set.of("doc", "docx", "pdf", "rtf", "odt");
	private static final Set<String> SPREADSHEET_SOURCE_EXTENSIONS = Set.of("csv", "xlsx", "ods");
	private static final Set<String> SLIDE_SOURCE_EXTENSIONS = Set.of("pptx");

	private final ProcessingProperties processingProperties;
	private final ArtifactStorageService artifactStorageService;
	private final DocumentPreviewService documentPreviewService;

	public OfficeConversionService(
		ProcessingProperties processingProperties,
		ArtifactStorageService artifactStorageService,
		DocumentPreviewService documentPreviewService
	) {
		this.processingProperties = processingProperties;
		this.artifactStorageService = artifactStorageService;
		this.documentPreviewService = documentPreviewService;
	}

	public boolean isAvailable() {
		return this.documentPreviewService.isAvailable();
	}

	public OfficeConversionResult process(UUID jobId, StoredUpload upload, OfficeConversionRequest request) {
		if (!"document".equals(ProcessingFileFamilyResolver.detectFamily(upload))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OFFICE_CONVERT job принимает только document uploads.");
		}

		var targetExtension = normalizeExtension(request.targetExtension());
		if (targetExtension.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OFFICE_CONVERT требует targetExtension.");
		}

		Path workingDirectory = null;
		try {
			workingDirectory = Files.createTempDirectory(this.processingProperties.getStorageRoot(), "office-convert-");
			var output = convert(upload, request, targetExtension, workingDirectory);
			var artifacts = storeArtifacts(jobId, upload, targetExtension, output);
			return new OfficeConversionResult(artifacts, output.runtimeLabel(), output.warnings());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить office conversion workspace.", exception);
		}
		finally {
			deleteRecursively(workingDirectory);
		}
	}

	private OfficeConversionOutput convert(
		StoredUpload upload,
		OfficeConversionRequest request,
		String targetExtension,
		Path workingDirectory
	) {
		var sourceExtension = normalizeExtension(upload.extension());
		var scenarioKey = sourceExtension + "->" + targetExtension;

		return switch (scenarioKey) {
			case "doc->docx", "pdf->docx", "rtf->docx", "odt->docx" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"Structured DOCX export"
			);
			case "docx->txt" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"Plain-text extraction export"
			);
			case "docx->html" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"Structured HTML export"
			);
			case "docx->rtf" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"RTF compatibility export"
			);
			case "docx->odt" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"ODT compatibility export"
			);
			case "docx->pdf", "xlsx->pdf" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"Structured PDF export"
			);
			case "pdf->txt" -> exportStructuredDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"PDF text extraction export"
			);
			case "csv->xlsx", "pdf->xlsx", "ods->xlsx" -> exportSpreadsheetDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"Workbook export"
			);
			case "xlsx->csv", "pdf->csv" -> exportSpreadsheetDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"Delimited table export"
			);
			case "xlsx->ods" -> exportSpreadsheetDocument(
				upload,
				request,
				targetExtension,
				workingDirectory,
				"ODS spreadsheet export"
			);
			case "pdf->pptx" -> exportPdfToPresentation(upload, request, workingDirectory);
			case "pdf->jpg", "pdf->png" -> exportPdfToImage(upload, request, targetExtension, workingDirectory);
			case "pptx->pdf" -> exportPresentationToPdf(upload, request, workingDirectory);
			case "pptx->jpg", "pptx->png" -> exportPresentationToImage(upload, request, targetExtension, workingDirectory);
			case "pptx->mp4" -> exportPresentationToVideo(upload, request, workingDirectory);
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"OFFICE_CONVERT пока не поддерживает сценарий %s.".formatted(scenarioKey)
			);
		};
	}

	private OfficeConversionOutput exportStructuredDocument(
		StoredUpload upload,
		OfficeConversionRequest request,
		String targetExtension,
		Path workingDirectory,
		String targetAdapterLabel
	) {
		var payload = this.documentPreviewService.analyze(upload);
		var structuredDocument = buildStructuredDocument(payload);
		var warnings = new ArrayList<>(structuredDocument.warnings());
		var sourceExtension = normalizeExtension(upload.extension());
		if ("pdf".equals(sourceExtension) && "docx".equals(targetExtension)) {
			warnings.add("PDF -> DOCX переносит текстовый поток и базовую структуру, но сложная вёрстка, колонки, positioned blocks и page-perfect layout могут измениться.");
		}
		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var previewFileName = derivedPreviewFileName(upload.originalFileName(), "html");
		var resultPath = workingDirectory.resolve(resultFileName);
		var previewPath = workingDirectory.resolve(previewFileName);

		writeHtml(previewPath, structuredDocument.htmlDocument());

		List<DocumentPreviewPayload.DocumentFact> resultFacts;
		String resultMediaType;
		String previewKind = "document";

		switch (targetExtension) {
			case "docx" -> {
				writeDocxDocument(resultPath, structuredDocument.blocks());
				resultMediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
				resultFacts = buildStructuredResultFacts("DOCX", structuredDocument.blocks());
			}
			case "txt" -> {
				var plainText = renderStructuredPlainText(structuredDocument.blocks());
				writeText(resultPath, plainText);
				resultMediaType = "text/plain";
				resultFacts = List.of(
					new DocumentPreviewPayload.DocumentFact("Тип результата", "TXT"),
					new DocumentPreviewPayload.DocumentFact("Символов", String.valueOf(plainText.length())),
					new DocumentPreviewPayload.DocumentFact("Блоки", String.valueOf(structuredDocument.blocks().size()))
				);
			}
			case "html" -> {
				writeHtml(resultPath, structuredDocument.htmlDocument());
				resultMediaType = "text/html";
				resultFacts = buildStructuredResultFacts("HTML", structuredDocument.blocks());
			}
			case "rtf" -> {
				writeText(resultPath, renderStructuredRtf(structuredDocument.blocks()));
				resultMediaType = "application/rtf";
				resultFacts = buildStructuredResultFacts("RTF", structuredDocument.blocks());
			}
			case "odt" -> {
				writeOdtDocument(resultPath, structuredDocument.blocks());
				resultMediaType = "application/vnd.oasis.opendocument.text";
				resultFacts = buildStructuredResultFacts("ODT", structuredDocument.blocks());
			}
			case "pdf" -> {
				writeStructuredPdf(resultPath, structuredDocument.blocks());
				resultMediaType = "application/pdf";
				resultFacts = buildStructuredResultFacts("PDF", structuredDocument.blocks());
				writeFile(previewPath, resultPath);
				previewFileName = derivedPreviewFileName(upload.originalFileName(), "pdf");
				previewPath = workingDirectory.resolve(previewFileName);
				writeFile(previewPath, resultPath);
			}
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Structured export не поддерживает target %s.".formatted(targetExtension)
			);
		}

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			resultMediaType,
			previewPath,
			previewFileName,
			targetExtension.equals("pdf") ? "application/pdf" : "text/html",
			previewKind,
			structuredDocument.sourceFacts(),
			resultFacts,
			structuredDocument.sourceAdapterLabel(),
			targetAdapterLabel,
			structuredDocument.sourceAdapterLabel() + " -> " + targetAdapterLabel,
			warnings
		);
	}

	private OfficeConversionOutput exportSpreadsheetDocument(
		StoredUpload upload,
		OfficeConversionRequest request,
		String targetExtension,
		Path workingDirectory,
		String targetAdapterLabel
	) {
		var spreadsheetBundle = buildSpreadsheetBundle(upload);
		var warnings = new ArrayList<>(spreadsheetBundle.warnings());
		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var previewFileName = derivedPreviewFileName(upload.originalFileName(), "html");
		var resultPath = workingDirectory.resolve(resultFileName);
		var previewPath = workingDirectory.resolve(previewFileName);
		writeHtml(previewPath, renderWorkbookHtml(spreadsheetBundle.sheets()));

		List<DocumentPreviewPayload.DocumentFact> resultFacts;
		String resultMediaType;

		switch (targetExtension) {
			case "xlsx" -> {
				writeXlsxWorkbook(resultPath, spreadsheetBundle.sheets());
				resultMediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
				resultFacts = buildWorkbookResultFacts("XLSX", spreadsheetBundle.sheets());
			}
			case "csv" -> {
				warnings.add("CSV target остаётся flattened table export: formulas, styling, comments и rich workbook structure не переносятся.");
				if (spreadsheetBundle.sheets().size() > 1) {
					warnings.add("CSV target может унести только один лист, поэтому экспортирует первый sheet из workbook source.");
				}
				writeCsvDocument(resultPath, spreadsheetBundle.sheets().getFirst().rows());
				resultMediaType = "text/csv";
				resultFacts = List.of(
					new DocumentPreviewPayload.DocumentFact("Тип результата", "CSV"),
					new DocumentPreviewPayload.DocumentFact("Строки", String.valueOf(spreadsheetBundle.sheets().getFirst().rows().size())),
					new DocumentPreviewPayload.DocumentFact("Лист-источник", spreadsheetBundle.sheets().getFirst().name())
				);
			}
			case "ods" -> {
				writeOdsWorkbook(resultPath, spreadsheetBundle.sheets());
				resultMediaType = "application/vnd.oasis.opendocument.spreadsheet";
				resultFacts = buildWorkbookResultFacts("ODS", spreadsheetBundle.sheets());
			}
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Spreadsheet export не поддерживает target %s.".formatted(targetExtension)
			);
		}

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			resultMediaType,
			previewPath,
			previewFileName,
			"text/html",
			"document",
			spreadsheetBundle.sourceFacts(),
			resultFacts,
			spreadsheetBundle.sourceAdapterLabel(),
			targetAdapterLabel,
			spreadsheetBundle.sourceAdapterLabel() + " -> " + targetAdapterLabel,
			warnings
		);
	}

	private OfficeConversionOutput exportPdfToPresentation(
		StoredUpload upload,
		OfficeConversionRequest request,
		Path workingDirectory
	) {
		var payload = this.documentPreviewService.analyze(upload);
		var pageImages = renderPdfPages(upload, request);
		var warnings = new ArrayList<>(payload.warnings());
		warnings.add("PDF -> PPTX складывает каждую страницу в отдельный slide как полноразмерное изображение: текст внутри deck остаётся не редактируемым.");

		var resultFileName = replaceExtension(upload.originalFileName(), "pptx");
		var resultPath = workingDirectory.resolve(resultFileName);
		writePptxSlideDeck(resultPath, pageImages);

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			upload.storagePath(),
			derivedPreviewFileName(upload.originalFileName(), "pdf"),
			"application/pdf",
			"document",
			payload.summary(),
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип результата", "PPTX"),
				new DocumentPreviewPayload.DocumentFact("Slides", String.valueOf(pageImages.size())),
				new DocumentPreviewPayload.DocumentFact("Режим экспорта", "PDF page images")
			),
			payload.previewLabel(),
			"PPTX image-slide export",
			payload.previewLabel() + " -> PPTX image-slide export",
			warnings
		);
	}

	private OfficeConversionOutput exportPdfToImage(
		StoredUpload upload,
		OfficeConversionRequest request,
		String targetExtension,
		Path workingDirectory
	) {
		var payload = this.documentPreviewService.analyze(upload);
		var pageImages = renderPdfPages(upload, request);
		var warnings = new ArrayList<>(payload.warnings());
		if (pageImages.size() > 1) {
			warnings.add("Несколько страниц PDF собраны в один вертикальный contact sheet, потому что image target здесь одностраничный.");
		}

		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var resultPath = workingDirectory.resolve(resultFileName);
		var contactSheet = buildContactSheet(pageImages, request, targetExtension);
		writeRasterImage(resultPath, contactSheet, targetExtension, request.quality());

		var resultFacts = List.of(
			new DocumentPreviewPayload.DocumentFact("Тип результата", targetExtension.toUpperCase(Locale.ROOT)),
			new DocumentPreviewPayload.DocumentFact("Страницы", String.valueOf(pageImages.size())),
			new DocumentPreviewPayload.DocumentFact(
				"Canvas",
				contactSheet.getWidth() + " x " + contactSheet.getHeight()
			)
		);

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			resolveRasterMediaType(targetExtension),
			resultPath,
			derivedPreviewFileName(upload.originalFileName(), targetExtension),
			resolveRasterMediaType(targetExtension),
			"image",
			payload.summary(),
			resultFacts,
			payload.previewLabel(),
			"PDF contact-sheet render",
			payload.previewLabel() + " -> PDF contact-sheet render",
			warnings
		);
	}

	private OfficeConversionOutput exportPresentationToPdf(
		StoredUpload upload,
		OfficeConversionRequest request,
		Path workingDirectory
	) {
		var payload = this.documentPreviewService.analyze(upload);
		var slideImages = renderPresentationSlides(upload, request);
		var warnings = new ArrayList<>(payload.warnings());
		warnings.add("PPTX -> PDF сохраняет visual layer каждого slide через backend rasterization: animations, speaker notes и embedded media в PDF не переносятся.");

		var resultFileName = replaceExtension(upload.originalFileName(), "pdf");
		var resultPath = workingDirectory.resolve(resultFileName);
		writeImagePdf(resultPath, slideImages);

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			"application/pdf",
			resultPath,
			derivedPreviewFileName(upload.originalFileName(), "pdf"),
			"application/pdf",
			"document",
			payload.summary(),
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип результата", "PDF"),
				new DocumentPreviewPayload.DocumentFact("Slides", String.valueOf(slideImages.size())),
				new DocumentPreviewPayload.DocumentFact("Режим экспорта", "Raster slide pages")
			),
			payload.previewLabel(),
			"Raster PDF slide export",
			payload.previewLabel() + " -> Raster PDF slide export",
			warnings
		);
	}

	private OfficeConversionOutput exportPresentationToImage(
		StoredUpload upload,
		OfficeConversionRequest request,
		String targetExtension,
		Path workingDirectory
	) {
		var payload = this.documentPreviewService.analyze(upload);
		var slideImages = renderPresentationSlides(upload, request);
		var warnings = new ArrayList<>(payload.warnings());
		if (slideImages.size() > 1) {
			warnings.add("Несколько slide preview объединены в единый vertical contact sheet, чтобы уложиться в один image artifact.");
		}

		var resultFileName = replaceExtension(upload.originalFileName(), targetExtension);
		var resultPath = workingDirectory.resolve(resultFileName);
		var contactSheet = buildContactSheet(slideImages, request, targetExtension);
		writeRasterImage(resultPath, contactSheet, targetExtension, request.quality());

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			resolveRasterMediaType(targetExtension),
			resultPath,
			derivedPreviewFileName(upload.originalFileName(), targetExtension),
			resolveRasterMediaType(targetExtension),
			"image",
			payload.summary(),
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип результата", targetExtension.toUpperCase(Locale.ROOT)),
				new DocumentPreviewPayload.DocumentFact("Slides", String.valueOf(slideImages.size())),
				new DocumentPreviewPayload.DocumentFact("Canvas", contactSheet.getWidth() + " x " + contactSheet.getHeight())
			),
			payload.previewLabel(),
			"Slide contact-sheet render",
			payload.previewLabel() + " -> Slide contact-sheet render",
			warnings
		);
	}

	private OfficeConversionOutput exportPresentationToVideo(
		StoredUpload upload,
		OfficeConversionRequest request,
		Path workingDirectory
	) {
		var payload = this.documentPreviewService.analyze(upload);
		var slideImages = renderPresentationSlides(upload, request);
		if (slideImages.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PPTX не содержит ни одного слайда для video export.");
		}

		final Path framesDirectory;
		try {
			framesDirectory = Files.createDirectories(workingDirectory.resolve("video-frames"));
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось подготовить временный каталог для video frames.", exception);
		}
		for (int index = 0; index < slideImages.size(); index += 1) {
			var framePath = framesDirectory.resolve("slide-%03d.png".formatted(index + 1));
			writeRasterImage(framePath, slideImages.get(index), "png", null);
		}

		var resultFileName = replaceExtension(upload.originalFileName(), "mp4");
		var resultPath = workingDirectory.resolve(resultFileName);
		execute(
			List.of(
				ffmpegExecutable(),
				"-v",
				"error",
				"-y",
				"-framerate",
				String.valueOf(1.0 / SLIDE_VIDEO_SECONDS),
				"-i",
				framesDirectory.resolve("slide-%03d.png").toString(),
				"-c:v",
				"libx264",
				"-pix_fmt",
				"yuv420p",
				resultPath.toString()
			),
			workingDirectory,
			videoTimeout()
		);
		ensureArtifact(resultPath, "pptx video export");

		var warnings = new ArrayList<>(payload.warnings());
		warnings.add("PPTX -> video собирает фиксированный MP4 slideshow с равной длительностью по %s секунды на slide: animations, transitions и embedded audio не воспроизводятся.".formatted(SLIDE_VIDEO_SECONDS));

		return new OfficeConversionOutput(
			resultPath,
			resultFileName,
			"video/mp4",
			resultPath,
			derivedPreviewFileName(upload.originalFileName(), "mp4"),
			"video/mp4",
			"media",
			payload.summary(),
			List.of(
				new DocumentPreviewPayload.DocumentFact("Тип результата", "MP4"),
				new DocumentPreviewPayload.DocumentFact("Slides", String.valueOf(slideImages.size())),
				new DocumentPreviewPayload.DocumentFact("Длительность", (slideImages.size() * SLIDE_VIDEO_SECONDS) + " sec")
			),
			payload.previewLabel(),
			"MP4 slideshow export",
			payload.previewLabel() + " -> MP4 slideshow export",
			warnings
		);
	}

	private StructuredDocument buildStructuredDocument(DocumentPreviewPayload payload) {
		var blocks = new ArrayList<StructuredBlock>();

		switch (payload.layout().mode()) {
			case "html" -> {
				if (payload.layout().srcDoc() != null && !payload.layout().srcDoc().isBlank()) {
					collectHtmlBlocks(Jsoup.parse(payload.layout().srcDoc()).body(), blocks);
				}
			}
			case "text", "pdf" -> {
				for (String paragraph : splitTextParagraphs(payload.searchableText())) {
					blocks.add(new TextBlock(paragraph, 0));
				}
			}
			case "table" -> blocks.add(new TableBlock(toSheetRows("table", payload.layout().table()).rows()));
			case "workbook" -> {
				for (SheetData sheet : toWorkbookSheets(payload.layout())) {
					blocks.add(new TextBlock(sheet.name(), 2));
					blocks.add(new TableBlock(sheet.rows()));
				}
			}
			case "slides" -> {
				if (payload.layout().slides() != null) {
					for (DocumentPreviewPayload.DocumentSlidePreview slide : payload.layout().slides()) {
						blocks.add(new TextBlock(slide.title(), 2));
						if (!slide.bullets().isEmpty()) {
							blocks.add(new BulletBlock(slide.bullets()));
						}
					}
				}
			}
			default -> {
			}
		}

		var warnings = new ArrayList<>(payload.warnings());
		if (blocks.isEmpty()) {
			// Здесь явно фиксируем ограничение scanned/poorly-structured документов:
			// конвертация не должна падать без результата, но и притворяться полной нельзя.
			blocks.add(new TextBlock("Searchable content is not available in this source without OCR.", 0));
			warnings.add("Источник не дал читаемый structured/text layer. Экспорт построен из placeholder-блока; для scanned PDF и image-based документов дальше нужен OCR.");
		}

		return new StructuredDocument(
			blocks,
			renderStructuredHtmlDocument(blocks),
			payload.summary(),
			warnings,
			payload.previewLabel()
		);
	}

	private SpreadsheetBundle buildSpreadsheetBundle(StoredUpload upload) {
		var extension = normalizeExtension(upload.extension());

		return switch (extension) {
			case "csv" -> {
				var payload = this.documentPreviewService.analyze(upload);
				yield new SpreadsheetBundle(
					List.of(toSheetRows(upload.originalFileName(), payload.layout().table())),
					payload.summary(),
					new ArrayList<>(payload.warnings()),
					payload.previewLabel()
				);
			}
			case "xlsx" -> {
				var payload = this.documentPreviewService.analyze(upload);
				yield new SpreadsheetBundle(
					toWorkbookSheets(payload.layout()),
					payload.summary(),
					new ArrayList<>(payload.warnings()),
					payload.previewLabel()
				);
			}
			case "ods" -> readOdsWorkbook(upload);
			case "pdf" -> buildPdfSpreadsheetBundle(upload);
			default -> throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Spreadsheet export пока не поддерживает source %s.".formatted(extension)
			);
		};
	}

	private SpreadsheetBundle buildPdfSpreadsheetBundle(StoredUpload upload) {
		var payload = this.documentPreviewService.analyze(upload);
		try (var document = Loader.loadPDF(upload.storagePath().toFile())) {
			var sheets = new ArrayList<SheetData>();
			for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex += 1) {
				var stripper = new org.apache.pdfbox.text.PDFTextStripper();
				stripper.setStartPage(pageIndex + 1);
				stripper.setEndPage(pageIndex + 1);
				var pageText = normalizeWhitespace(stripper.getText(document));
				var rows = new ArrayList<List<String>>();
				rows.add(List.of("Line", "Text"));

				var lines = splitPdfLines(pageText);
				for (int lineIndex = 0; lineIndex < lines.size(); lineIndex += 1) {
					rows.add(List.of(String.valueOf(lineIndex + 1), lines.get(lineIndex)));
				}

				if (rows.size() == 1) {
					rows.add(List.of("1", "Searchable text layer is not available without OCR."));
				}

				sheets.add(new SheetData("Page " + (pageIndex + 1), rows));
			}

			var warnings = new ArrayList<>(payload.warnings());
			warnings.add("PDF -> table export использует line-based extraction: строки и page order сохраняются, но сложная табличная вёрстка и merged cells не восстанавливаются точно.");

			return new SpreadsheetBundle(sheets, payload.summary(), warnings, payload.previewLabel());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось извлечь PDF text layer для spreadsheet export.", exception);
		}
	}

	private SpreadsheetBundle readOdsWorkbook(StoredUpload upload) {
		try (var zipFile = new ZipFile(upload.storagePath().toFile())) {
			var content = readZipEntryAsText(zipFile, "content.xml")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODS adapter не нашёл content.xml внутри spreadsheet archive."));
			var document = parseXml(content);
			var spreadsheet = findFirstElementByLocalName(document.getDocumentElement(), "spreadsheet")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODS adapter не нашёл office:spreadsheet внутри content.xml."));
			var sheets = new ArrayList<SheetData>();

			forEachChildElement(spreadsheet, tableElement -> {
				if (!"table".equals(localName(tableElement))) {
					return;
				}

				var rows = new ArrayList<List<String>>();
				forEachChildElement(tableElement, rowElement -> {
					if (!"table-row".equals(localName(rowElement))) {
						return;
					}

					var rowValues = new ArrayList<String>();
					int repeatedRows = readPositiveInt(rowElement.getAttribute("table:number-rows-repeated")).orElse(1);

					forEachChildElement(rowElement, cellElement -> {
						if (!"table-cell".equals(localName(cellElement))) {
							return;
						}

						int repeatedCells = readPositiveInt(cellElement.getAttribute("table:number-columns-repeated")).orElse(1);
						var cellText = readOdsCellText(cellElement);
						for (int repeatIndex = 0; repeatIndex < repeatedCells; repeatIndex += 1) {
							rowValues.add(cellText);
						}
					});

					for (int repeatIndex = 0; repeatIndex < repeatedRows; repeatIndex += 1) {
						rows.add(new ArrayList<>(rowValues));
					}
				});

				if (!rows.isEmpty()) {
					sheets.add(new SheetData(
						Optional.ofNullable(tableElement.getAttribute("table:name"))
							.map(String::trim)
							.filter(value -> !value.isBlank())
							.orElse("Sheet " + (sheets.size() + 1)),
						trimTrailingEmptyRows(rows)
					));
				}
			});

			if (sheets.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODS spreadsheet не содержит читаемых sheets.");
			}

			return new SpreadsheetBundle(
				sheets,
				List.of(
					new DocumentPreviewPayload.DocumentFact("Тип документа", "ODS"),
					new DocumentPreviewPayload.DocumentFact("Sheets", String.valueOf(sheets.size())),
					new DocumentPreviewPayload.DocumentFact("Rows", String.valueOf(Math.max(sheets.getFirst().rows().size() - 1, 0)))
				),
				new ArrayList<>(List.of("ODS source читается через backend archive/xml adapter: cell values и sheet order сохраняются, но формулы, styles и charts при roundtrip сводятся к plain workbook data.")),
				"ODS spreadsheet adapter"
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать ODS workbook.", exception);
		}
	}

	private List<BufferedImage> renderPdfPages(StoredUpload upload, OfficeConversionRequest request) {
		try (var document = Loader.loadPDF(upload.storagePath().toFile())) {
			var renderer = new PDFRenderer(document);
			var pages = new ArrayList<BufferedImage>();
			for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex += 1) {
				pages.add(scaleIfNeeded(renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI), request.maxWidth(), request.maxHeight()));
			}
			return pages;
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось отрендерить PDF page images.", exception);
		}
	}

	private List<BufferedImage> renderPresentationSlides(StoredUpload upload, OfficeConversionRequest request) {
		try (var inputStream = Files.newInputStream(upload.storagePath());
			var slideShow = new XMLSlideShow(inputStream)) {
			var pageSize = slideShow.getPageSize();
			var slides = new ArrayList<BufferedImage>();

			for (XSLFSlide slide : slideShow.getSlides()) {
				var slideImage = new BufferedImage(pageSize.width, pageSize.height, BufferedImage.TYPE_INT_ARGB);
				var graphics = slideImage.createGraphics();
				try {
					graphics.setComposite(AlphaComposite.SrcOver);
					graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					graphics.setColor(Color.WHITE);
					graphics.fillRect(0, 0, pageSize.width, pageSize.height);
					slide.draw(graphics);
				}
				finally {
					graphics.dispose();
				}

				slides.add(scaleIfNeeded(slideImage, request.maxWidth(), request.maxHeight()));
			}

			return slides;
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось отрендерить PPTX slide images.", exception);
		}
	}

	private BufferedImage buildContactSheet(
		List<BufferedImage> images,
		OfficeConversionRequest request,
		String targetExtension
	) {
		if (images.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact sheet нельзя собрать без исходных страниц или слайдов.");
		}

		int width = 0;
		int height = CONTACT_SHEET_PADDING * 2 + CONTACT_SHEET_GAP * Math.max(images.size() - 1, 0);
		for (BufferedImage image : images) {
			width = Math.max(width, image.getWidth());
			height += image.getHeight();
		}

		var background = resolveColor(request.backgroundColor(), "jpg".equals(targetExtension) ? "#ffffff" : "#f6f0e6");
		var sheet = new BufferedImage(
			width + CONTACT_SHEET_PADDING * 2,
			height,
			"png".equals(targetExtension) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
		);
		var graphics = sheet.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setColor(background);
			graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

			int currentTop = CONTACT_SHEET_PADDING;
			for (BufferedImage image : images) {
				int left = CONTACT_SHEET_PADDING + (width - image.getWidth()) / 2;
				graphics.drawImage(image, left, currentTop, null);
				currentTop += image.getHeight() + CONTACT_SHEET_GAP;
			}
		}
		finally {
			graphics.dispose();
		}

		return sheet;
	}

	private List<StoredArtifact> storeArtifacts(
		UUID jobId,
		StoredUpload upload,
		String targetExtension,
		OfficeConversionOutput output
	) {
		var manifest = new OfficeConversionManifest(
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
			Instant.now(),
			output.warnings()
		);

		return List.of(
			this.artifactStorageService.storeJsonArtifact(jobId, "office-convert-manifest", "office-convert-manifest.json", manifest),
			this.artifactStorageService.storeFileArtifact(jobId, "office-convert-binary", output.resultFileName(), output.resultMediaType(), output.resultPath()),
			this.artifactStorageService.storeFileArtifact(jobId, "office-convert-preview", output.previewFileName(), output.previewMediaType(), output.previewPath())
		);
	}

	private List<DocumentPreviewPayload.DocumentFact> buildStructuredResultFacts(
		String label,
		List<StructuredBlock> blocks
	) {
		int paragraphCount = 0;
		int tableCount = 0;
		int listCount = 0;
		for (StructuredBlock block : blocks) {
			if (block instanceof TextBlock) {
				paragraphCount += 1;
			} else if (block instanceof TableBlock) {
				tableCount += 1;
			} else if (block instanceof BulletBlock) {
				listCount += 1;
			}
		}

		return List.of(
			new DocumentPreviewPayload.DocumentFact("Тип результата", label),
			new DocumentPreviewPayload.DocumentFact("Блоки", String.valueOf(blocks.size())),
			new DocumentPreviewPayload.DocumentFact("Текстовые блоки", String.valueOf(paragraphCount)),
			new DocumentPreviewPayload.DocumentFact("Таблицы / списки", String.valueOf(tableCount + listCount))
		);
	}

	private List<DocumentPreviewPayload.DocumentFact> buildWorkbookResultFacts(
		String label,
		List<SheetData> sheets
	) {
		var firstSheet = sheets.getFirst();
		return List.of(
			new DocumentPreviewPayload.DocumentFact("Тип результата", label),
			new DocumentPreviewPayload.DocumentFact("Sheets", String.valueOf(sheets.size())),
			new DocumentPreviewPayload.DocumentFact("Rows", String.valueOf(Math.max(firstSheet.rows().size() - 1, 0))),
			new DocumentPreviewPayload.DocumentFact("Preview sheet", firstSheet.name())
		);
	}

	private List<SheetData> toWorkbookSheets(DocumentPreviewPayload.DocumentLayoutPayload layout) {
		if (layout.sheets() == null || layout.sheets().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workbook payload не содержит sheets для spreadsheet export.");
		}

		return layout.sheets().stream()
			.map(sheet -> toSheetRows(sheet.name(), sheet.table()))
			.toList();
	}

	private SheetData toSheetRows(String name, DocumentPreviewPayload.DocumentTablePreview table) {
		if (table == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Табличный payload не содержит table data.");
		}

		var rows = new ArrayList<List<String>>();
		if (table.columns() != null && !table.columns().isEmpty()) {
			rows.add(new ArrayList<>(table.columns()));
		}
		if (table.rows() != null) {
			for (List<String> row : table.rows()) {
				rows.add(new ArrayList<>(row));
			}
		}
		if (rows.isEmpty()) {
			rows.add(List.of("Value"));
		}
		return new SheetData(name, rows);
	}

	private void collectHtmlBlocks(Element root, List<StructuredBlock> blocks) {
		for (Element child : root.children()) {
			var tagName = child.tagName().toLowerCase(Locale.ROOT);
			switch (tagName) {
				case "h1", "h2", "h3", "h4", "h5", "h6" -> {
					var text = normalizeWhitespace(child.text());
					if (!text.isBlank()) {
						blocks.add(new TextBlock(text, Integer.parseInt(tagName.substring(1))));
					}
				}
				case "p" -> {
					var text = normalizeWhitespace(child.text());
					if (!text.isBlank()) {
						blocks.add(new TextBlock(text, 0));
					}
				}
				case "ul", "ol" -> {
					var bullets = child.select("> li").stream()
						.map(Element::text)
						.map(this::normalizeWhitespace)
						.filter(value -> !value.isBlank())
						.toList();
					if (!bullets.isEmpty()) {
						blocks.add(new BulletBlock(bullets));
					}
				}
				case "table" -> {
					var rows = new ArrayList<List<String>>();
					for (Element rowElement : child.select("tr")) {
						var row = rowElement.select("th,td").stream()
							.map(Element::text)
							.map(this::normalizeWhitespace)
							.toList();
						if (!row.isEmpty()) {
							rows.add(row);
						}
					}
					if (!rows.isEmpty()) {
						blocks.add(new TableBlock(rows));
					}
				}
				default -> collectHtmlBlocks(child, blocks);
			}
		}
	}

	private String renderStructuredHtmlDocument(List<StructuredBlock> blocks) {
		var body = new StringBuilder();
		body.append("<section class=\"export-document\">");
		for (StructuredBlock block : blocks) {
			if (block instanceof TextBlock textBlock) {
				if (textBlock.level() > 0) {
					var safeLevel = Math.min(Math.max(textBlock.level(), 1), 6);
					body.append("<h").append(safeLevel).append(">")
						.append(escapeHtml(textBlock.text()))
						.append("</h").append(safeLevel).append(">");
				} else {
					body.append("<p>").append(escapeHtml(textBlock.text())).append("</p>");
				}
			} else if (block instanceof BulletBlock bulletBlock) {
				body.append("<ul>");
				for (String bullet : bulletBlock.items()) {
					body.append("<li>").append(escapeHtml(bullet)).append("</li>");
				}
				body.append("</ul>");
			} else if (block instanceof TableBlock tableBlock) {
				body.append("<table><tbody>");
				for (List<String> row : tableBlock.rows()) {
					body.append("<tr>");
					for (String cell : row) {
						body.append("<td>").append(escapeHtml(cell)).append("</td>");
					}
					body.append("</tr>");
				}
				body.append("</tbody></table>");
			}
		}
		body.append("</section>");

		return """
			<!doctype html>
			<html lang="ru">
			  <head>
			    <meta charset="utf-8" />
			    <style>
			      :root { color-scheme: light; }
			      body {
			        margin: 0;
			        padding: 24px;
			        font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif;
			        background: #f6f0e6;
			        color: #16312d;
			      }
			      .export-document {
			        display: grid;
			        gap: 14px;
			        max-width: 960px;
			        margin: 0 auto;
			      }
			      h1, h2, h3, h4, h5, h6 {
			        margin: 0;
			        line-height: 1.1;
			      }
			      p, li, td {
			        line-height: 1.5;
			        font-size: 15px;
			      }
			      table {
			        width: 100%;
			        border-collapse: collapse;
			        background: rgba(255,255,255,0.78);
			      }
			      td {
			        border: 1px solid rgba(22, 49, 45, 0.16);
			        padding: 8px 10px;
			      }
			    </style>
			  </head>
			  <body>{{body}}</body>
			</html>
			""".replace("{{body}}", body.toString());
	}

	private String renderWorkbookHtml(List<SheetData> sheets) {
		var blocks = new ArrayList<StructuredBlock>();
		for (SheetData sheet : sheets) {
			blocks.add(new TextBlock(sheet.name(), 2));
			blocks.add(new TableBlock(sheet.rows()));
		}
		return renderStructuredHtmlDocument(blocks);
	}

	private String renderStructuredPlainText(List<StructuredBlock> blocks) {
		var sections = new ArrayList<String>();
		for (StructuredBlock block : blocks) {
			if (block instanceof TextBlock textBlock) {
				sections.add(textBlock.text());
			} else if (block instanceof BulletBlock bulletBlock) {
				sections.add(
					bulletBlock.items().stream()
						.map(item -> "- " + item)
						.collect(java.util.stream.Collectors.joining("\n"))
				);
			} else if (block instanceof TableBlock tableBlock) {
				var rows = tableBlock.rows().stream()
					.map(row -> String.join("\t", row))
					.collect(java.util.stream.Collectors.joining("\n"));
				sections.add(rows);
			}
		}
		return String.join("\n\n", sections);
	}

	private String renderStructuredRtf(List<StructuredBlock> blocks) {
		var builder = new StringBuilder();
		builder.append("{\\rtf1\\ansi\\deff0");
		for (StructuredBlock block : blocks) {
			if (block instanceof TextBlock textBlock) {
				builder.append("\\par ").append(escapeRtf(textBlock.text())).append("\\par ");
			} else if (block instanceof BulletBlock bulletBlock) {
				for (String bullet : bulletBlock.items()) {
					builder.append("\\par - ").append(escapeRtf(bullet)).append("\\par ");
				}
			} else if (block instanceof TableBlock tableBlock) {
				for (List<String> row : tableBlock.rows()) {
					builder.append("\\par ").append(escapeRtf(String.join(" | ", row))).append("\\par ");
				}
			}
		}
		builder.append("}");
		return builder.toString();
	}

	private void writeDocxDocument(Path outputPath, List<StructuredBlock> blocks) {
		try (var document = new XWPFDocument()) {
			for (StructuredBlock block : blocks) {
				if (block instanceof TextBlock textBlock) {
					var paragraph = document.createParagraph();
					if (textBlock.level() > 0) {
						paragraph.setStyle("Heading" + Math.min(textBlock.level(), 6));
						paragraph.setAlignment(ParagraphAlignment.LEFT);
					}
					paragraph.createRun().setText(textBlock.text());
				} else if (block instanceof BulletBlock bulletBlock) {
					for (String item : bulletBlock.items()) {
						var paragraph = document.createParagraph();
						paragraph.createRun().setText("• " + item);
					}
				} else if (block instanceof TableBlock tableBlock) {
					var rows = tableBlock.rows();
					if (rows.isEmpty()) {
						continue;
					}
					var table = document.createTable(Math.max(rows.size(), 1), Math.max(rows.getFirst().size(), 1));
					for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
						var row = rows.get(rowIndex);
						var tableRow = table.getRow(rowIndex);
						for (int cellIndex = 0; cellIndex < row.size(); cellIndex += 1) {
							tableRow.getCell(cellIndex).setText(row.get(cellIndex));
						}
					}
				}
			}

			try (var outputStream = Files.newOutputStream(outputPath)) {
				document.write(outputStream);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать DOCX export artifact.", exception);
		}
	}

	private void writeStructuredPdf(Path outputPath, List<StructuredBlock> blocks) {
		try (var document = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			PDPageContentStream content = new PDPageContentStream(document, page);
			float y = page.getMediaBox().getUpperRightY() - PDF_MARGIN;

			for (StructuredBlock block : blocks) {
				var lines = linesForPdf(block);
				var font = fontForBlock(block);
				var fontSize = fontSizeForBlock(block);
				var wrappedLines = new ArrayList<String>();
				var maxWidth = page.getMediaBox().getWidth() - PDF_MARGIN * 2;
				for (String line : lines) {
					wrappedLines.addAll(wrapPdfText(line, font, fontSize, maxWidth));
				}

				for (String line : wrappedLines) {
					if (y < PDF_MARGIN + PDF_LINE_HEIGHT) {
						content.close();
						page = new PDPage(PDRectangle.A4);
						document.addPage(page);
						content = new PDPageContentStream(document, page);
						y = page.getMediaBox().getUpperRightY() - PDF_MARGIN;
					}

					content.beginText();
					content.setFont(font, fontSize);
					content.newLineAtOffset(PDF_MARGIN, y);
					content.showText(line);
					content.endText();
					y -= PDF_LINE_HEIGHT + (fontSize >= PDF_SUBHEADING_FONT_SIZE ? 2f : 0f);
				}

				y -= 6f;
			}

			content.close();
			document.save(outputPath.toFile());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать structured PDF export artifact.", exception);
		}
	}

	private void writeImagePdf(Path outputPath, List<BufferedImage> pages) {
		try (var document = new PDDocument()) {
			for (BufferedImage image : pages) {
				var page = new PDPage(PDRectangle.A4);
				document.addPage(page);
				var imageObject = LosslessFactory.createFromImage(document, image);
				try (var content = new PDPageContentStream(document, page)) {
					var bounds = fitInto(page.getMediaBox(), image.getWidth(), image.getHeight());
					content.drawImage(imageObject, bounds.x(), bounds.y(), bounds.width(), bounds.height());
				}
			}
			document.save(outputPath.toFile());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать raster PDF artifact.", exception);
		}
	}

	private void writePptxSlideDeck(Path outputPath, List<BufferedImage> pages) {
		try (var slideShow = new XMLSlideShow()) {
			if (pages.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PPTX deck нельзя собрать без slide/page images.");
			}

			slideShow.setPageSize(new Dimension(pages.getFirst().getWidth(), pages.getFirst().getHeight()));
			for (BufferedImage pageImage : pages) {
				var pictureData = slideShow.addPicture(toPngBytes(pageImage), XSLFPictureData.PictureType.PNG);
				var slide = slideShow.createSlide();
				var picture = slide.createPicture(pictureData);
				picture.setAnchor(new java.awt.Rectangle(0, 0, pageImage.getWidth(), pageImage.getHeight()));
			}

			try (var outputStream = Files.newOutputStream(outputPath)) {
				slideShow.write(outputStream);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать PPTX export artifact.", exception);
		}
	}

	private void writeXlsxWorkbook(Path outputPath, List<SheetData> sheets) {
		try (Workbook workbook = new XSSFWorkbook()) {
			for (SheetData sheet : sheets) {
				var workbookSheet = workbook.createSheet(sheet.name());
				for (int rowIndex = 0; rowIndex < sheet.rows().size(); rowIndex += 1) {
					var workbookRow = workbookSheet.createRow(rowIndex);
					var row = sheet.rows().get(rowIndex);
					for (int cellIndex = 0; cellIndex < row.size(); cellIndex += 1) {
						workbookRow.createCell(cellIndex).setCellValue(row.get(cellIndex));
					}
				}
			}

			try (var outputStream = Files.newOutputStream(outputPath)) {
				workbook.write(outputStream);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать XLSX workbook artifact.", exception);
		}
	}

	private void writeOdtDocument(Path outputPath, List<StructuredBlock> blocks) {
		try (var outputStream = Files.newOutputStream(outputPath);
			var zip = new ZipOutputStream(outputStream)) {
			writeOpenDocumentCommonEntries(zip, "application/vnd.oasis.opendocument.text");
			writeZipEntry(zip, "content.xml", buildOdtContent(blocks));
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать ODT artifact.", exception);
		}
	}

	private void writeOdsWorkbook(Path outputPath, List<SheetData> sheets) {
		try (var outputStream = Files.newOutputStream(outputPath);
			var zip = new ZipOutputStream(outputStream)) {
			writeOpenDocumentCommonEntries(zip, "application/vnd.oasis.opendocument.spreadsheet");
			writeZipEntry(zip, "content.xml", buildOdsContent(sheets));
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать ODS artifact.", exception);
		}
	}

	private void writeOpenDocumentCommonEntries(ZipOutputStream zip, String mimeType) throws IOException {
		writeStoredZipEntry(zip, "mimetype", mimeType.getBytes(StandardCharsets.UTF_8));
		zip.setLevel(Deflater.DEFAULT_COMPRESSION);
		writeZipEntry(
			zip,
			"META-INF/manifest.xml",
			"""
				<?xml version="1.0" encoding="UTF-8"?>
				<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
				  <manifest:file-entry manifest:media-type="%s" manifest:full-path="/"/>
				  <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="content.xml"/>
				</manifest:manifest>
				""".formatted(escapeXml(mimeType))
		);
	}

	private String buildOdtContent(List<StructuredBlock> blocks) {
		var body = new StringBuilder();
		for (StructuredBlock block : blocks) {
			if (block instanceof TextBlock textBlock) {
				if (textBlock.level() > 0) {
					body.append("<text:h text:outline-level=\"")
						.append(Math.min(textBlock.level(), 6))
						.append("\">")
						.append(escapeXml(textBlock.text()))
						.append("</text:h>");
				} else {
					body.append("<text:p>").append(escapeXml(textBlock.text())).append("</text:p>");
				}
			} else if (block instanceof BulletBlock bulletBlock) {
				body.append("<text:list>");
				for (String item : bulletBlock.items()) {
					body.append("<text:list-item><text:p>")
						.append(escapeXml(item))
						.append("</text:p></text:list-item>");
				}
				body.append("</text:list>");
			} else if (block instanceof TableBlock tableBlock) {
				body.append("<table:table table:name=\"Table\">");
				for (List<String> row : tableBlock.rows()) {
					body.append("<table:table-row>");
					for (String cell : row) {
						body.append("<table:table-cell office:value-type=\"string\"><text:p>")
							.append(escapeXml(cell))
							.append("</text:p></table:table-cell>");
					}
					body.append("</table:table-row>");
				}
				body.append("</table:table>");
			}
		}

		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<office:document-content
			  xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
			  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
			  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
			  office:version="1.2">
			  <office:body>
			    <office:text>%s</office:text>
			  </office:body>
			</office:document-content>
			""".formatted(body);
	}

	private String buildOdsContent(List<SheetData> sheets) {
		var tables = new StringBuilder();
		for (SheetData sheet : sheets) {
			tables.append("<table:table table:name=\"").append(escapeXml(sheet.name())).append("\">");
			for (List<String> row : sheet.rows()) {
				tables.append("<table:table-row>");
				for (String cell : row) {
					tables.append("<table:table-cell office:value-type=\"string\"><text:p>")
						.append(escapeXml(cell))
						.append("</text:p></table:table-cell>");
				}
				tables.append("</table:table-row>");
			}
			tables.append("</table:table>");
		}

		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<office:document-content
			  xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
			  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
			  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
			  office:version="1.2">
			  <office:body>
			    <office:spreadsheet>%s</office:spreadsheet>
			  </office:body>
			</office:document-content>
			""".formatted(tables);
	}

	private void writeCsvDocument(Path outputPath, List<List<String>> rows) {
		try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
			var printer = CSVFormat.DEFAULT.print(writer)) {
			for (List<String> row : rows) {
				printer.printRecord(row);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось собрать CSV artifact.", exception);
		}
	}

	private void writeText(Path outputPath, String content) {
		try {
			Files.writeString(outputPath, content, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить текстовый artifact.", exception);
		}
	}

	private void writeHtml(Path outputPath, String html) {
		writeText(outputPath, html);
	}

	private void writeFile(Path outputPath, Path sourcePath) {
		try {
			Files.copy(sourcePath, outputPath);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить preview artifact.", exception);
		}
	}

	private void writeRasterImage(Path outputPath, BufferedImage image, String extension, Double quality) {
		try {
			if ("jpg".equals(extension) || "jpeg".equals(extension)) {
				writeJpeg(outputPath, image, quality);
				return;
			}

			ImageIO.write(image, extension, outputPath.toFile());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить raster artifact.", exception);
		}
	}

	private void writeJpeg(Path outputPath, BufferedImage image, Double quality) throws IOException {
		var rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		var graphics = rgbImage.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
			graphics.drawImage(image, 0, 0, null);
		}
		finally {
			graphics.dispose();
		}

		var writers = ImageIO.getImageWritersByFormatName("jpg");
		if (!writers.hasNext()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JPG writer недоступен в текущем backend окружении.");
		}

		ImageWriter writer = writers.next();
		try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(outputPath.toFile())) {
			writer.setOutput(outputStream);
			var parameters = writer.getDefaultWriteParam();
			if (parameters.canWriteCompressed()) {
				parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				parameters.setCompressionQuality((float) (quality == null ? 0.9 : Math.max(0.55, Math.min(1.0, quality))));
			}
			writer.write(null, new IIOImage(rgbImage, null, null), parameters);
		}
		finally {
			writer.dispose();
		}
	}

	private RectangleFit fitInto(PDRectangle pageRectangle, int contentWidth, int contentHeight) {
		var maxWidth = pageRectangle.getWidth() - PDF_MARGIN * 2;
		var maxHeight = pageRectangle.getHeight() - PDF_MARGIN * 2;
		var widthRatio = maxWidth / contentWidth;
		var heightRatio = maxHeight / contentHeight;
		var scale = Math.min(widthRatio, heightRatio);
		var width = contentWidth * scale;
		var height = contentHeight * scale;
		var x = (pageRectangle.getWidth() - width) / 2f;
		var y = (pageRectangle.getHeight() - height) / 2f;
		return new RectangleFit(x, y, width, height);
	}

	private List<String> linesForPdf(StructuredBlock block) {
		if (block instanceof TextBlock textBlock) {
			return List.of(textBlock.text());
		}
		if (block instanceof BulletBlock bulletBlock) {
			return bulletBlock.items().stream().map(item -> "- " + item).toList();
		}
		if (block instanceof TableBlock tableBlock) {
			return tableBlock.rows().stream()
				.map(row -> String.join(" | ", row))
				.toList();
		}
		return List.of();
	}

	private PDType1Font fontForBlock(StructuredBlock block) {
		if (block instanceof TextBlock textBlock && textBlock.level() > 0) {
			return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		}
		return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
	}

	private float fontSizeForBlock(StructuredBlock block) {
		if (block instanceof TextBlock textBlock && textBlock.level() == 1) {
			return PDF_HEADING_FONT_SIZE;
		}
		if (block instanceof TextBlock textBlock && textBlock.level() > 1) {
			return PDF_SUBHEADING_FONT_SIZE;
		}
		return PDF_BODY_FONT_SIZE;
	}

	private List<String> wrapPdfText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
		var words = text.split("\\s+");
		if (words.length == 0) {
			return List.of("");
		}

		var lines = new ArrayList<String>();
		var currentLine = new StringBuilder();
		for (String word : words) {
			var candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
			var candidateWidth = font.getStringWidth(candidate) / 1000f * fontSize;
			if (candidateWidth <= maxWidth || currentLine.isEmpty()) {
				currentLine.setLength(0);
				currentLine.append(candidate);
			} else {
				lines.add(currentLine.toString());
				currentLine.setLength(0);
				currentLine.append(word);
			}
		}
		if (!currentLine.isEmpty()) {
			lines.add(currentLine.toString());
		}
		return lines;
	}

	private BufferedImage scaleIfNeeded(BufferedImage image, Integer maxWidth, Integer maxHeight) {
		if (maxWidth == null && maxHeight == null) {
			return image;
		}

		var scaled = scaleToFit(image.getWidth(), image.getHeight(), maxWidth, maxHeight);
		if (scaled.width() == image.getWidth() && scaled.height() == image.getHeight()) {
			return image;
		}

		var output = new BufferedImage(scaled.width(), scaled.height(), image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType());
		var graphics = output.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(image, 0, 0, scaled.width(), scaled.height(), null);
		}
		finally {
			graphics.dispose();
		}
		return output;
	}

	private Size scaleToFit(int sourceWidth, int sourceHeight, Integer maxWidth, Integer maxHeight) {
		double widthRatio = maxWidth == null ? Double.POSITIVE_INFINITY : (double) maxWidth / sourceWidth;
		double heightRatio = maxHeight == null ? Double.POSITIVE_INFINITY : (double) maxHeight / sourceHeight;
		double scale = Math.min(Math.min(widthRatio, heightRatio), 1d);
		if (Double.isInfinite(scale) || scale <= 0d) {
			scale = 1d;
		}
		return new Size(Math.max(1, (int) Math.round(sourceWidth * scale)), Math.max(1, (int) Math.round(sourceHeight * scale)));
	}

	private String resolveRasterMediaType(String extension) {
		return switch (extension) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			default -> "application/octet-stream";
		};
	}

	private String replaceExtension(String fileName, String targetExtension) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex == -1) {
			return fileName + "." + targetExtension;
		}
		return fileName.substring(0, dotIndex + 1) + targetExtension;
	}

	private String derivedPreviewFileName(String fileName, String previewExtension) {
		int dotIndex = fileName.lastIndexOf('.');
		var base = dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
		return base + ".preview." + previewExtension;
	}

	private String normalizeExtension(String extension) {
		return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
	}

	private String normalizeWhitespace(String value) {
		return value == null ? "" : value.replace("\r", "\n").replaceAll("[\\t\\x0B\\f ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
	}

	private List<String> splitTextParagraphs(String text) {
		var normalized = normalizeWhitespace(text);
		if (normalized.isBlank()) {
			return List.of();
		}
		return List.of(normalized.split("\\n\\n+"));
	}

	private List<String> splitPdfLines(String text) {
		var normalized = normalizeWhitespace(text);
		if (normalized.isBlank()) {
			return List.of();
		}
		return normalized.lines()
			.map(this::normalizeWhitespace)
			.filter(line -> !line.isBlank())
			.toList();
	}

	private String escapeHtml(String value) {
		return new TextNode(value).outerHtml();
	}

	private String escapeXml(String value) {
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}

	private String escapeRtf(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("{", "\\{")
			.replace("}", "\\}")
			.replace("\n", "\\line ");
	}

	private byte[] toPngBytes(BufferedImage image) {
		try (var output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось закодировать временный PNG frame.", exception);
		}
	}

	private Optional<String> readZipEntryAsText(ZipFile zipFile, String entryName) throws IOException {
		var entry = zipFile.getEntry(entryName);
		if (entry == null) {
			return Optional.empty();
		}

		try (var inputStream = zipFile.getInputStream(entry)) {
			return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private org.w3c.dom.Document parseXml(String content) {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
		}
		catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось разобрать XML внутри office container.", exception);
		}
	}

	private Optional<org.w3c.dom.Element> findFirstElementByLocalName(org.w3c.dom.Element root, String targetLocalName) {
		if (targetLocalName.equals(localName(root))) {
			return Optional.of(root);
		}

		var childNodes = root.getChildNodes();
		for (int index = 0; index < childNodes.getLength(); index += 1) {
			var child = childNodes.item(index);
			if (child instanceof org.w3c.dom.Element element) {
				var nested = findFirstElementByLocalName(element, targetLocalName);
				if (nested.isPresent()) {
					return nested;
				}
			}
		}
		return Optional.empty();
	}

	private void forEachChildElement(org.w3c.dom.Element root, java.util.function.Consumer<org.w3c.dom.Element> consumer) {
		var childNodes = root.getChildNodes();
		for (int index = 0; index < childNodes.getLength(); index += 1) {
			var child = childNodes.item(index);
			if (child instanceof org.w3c.dom.Element element) {
				consumer.accept(element);
			}
		}
	}

	private String localName(org.w3c.dom.Element element) {
		var value = element.getLocalName();
		if (value != null && !value.isBlank()) {
			return value;
		}
		var nodeName = element.getNodeName();
		int separatorIndex = nodeName.indexOf(':');
		return separatorIndex == -1 ? nodeName : nodeName.substring(separatorIndex + 1);
	}

	private String readOdsCellText(org.w3c.dom.Element cellElement) {
		var directText = new StringBuilder();
		forEachChildElement(cellElement, child -> {
			if ("p".equals(localName(child))) {
				if (!directText.isEmpty()) {
					directText.append('\n');
				}
				directText.append(normalizeWhitespace(child.getTextContent()));
			}
		});
		return normalizeWhitespace(directText.toString());
	}

	private Optional<Integer> readPositiveInt(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return Optional.empty();
		}
		try {
			var parsed = Integer.parseInt(rawValue.trim());
			return parsed > 0 ? Optional.of(parsed) : Optional.empty();
		}
		catch (NumberFormatException ignored) {
			return Optional.empty();
		}
	}

	private List<List<String>> trimTrailingEmptyRows(List<List<String>> rows) {
		int lastIndex = rows.size() - 1;
		while (lastIndex >= 0 && rows.get(lastIndex).stream().allMatch(value -> value == null || value.isBlank())) {
			lastIndex -= 1;
		}
		if (lastIndex < 0) {
			return List.of(List.of("Value"));
		}
		return new ArrayList<>(rows.subList(0, lastIndex + 1));
	}

	private Color resolveColor(String rawValue, String fallbackHex) {
		var candidate = rawValue == null || rawValue.isBlank() ? fallbackHex : rawValue.trim();
		try {
			return Color.decode(candidate.startsWith("#") ? candidate : "#" + candidate);
		}
		catch (NumberFormatException exception) {
			return Color.decode(fallbackHex);
		}
	}

	private void writeStoredZipEntry(ZipOutputStream zip, String entryName, byte[] bytes) throws IOException {
		var entry = new ZipEntry(entryName);
		entry.setMethod(ZipEntry.STORED);
		entry.setSize(bytes.length);
		entry.setCompressedSize(bytes.length);
		var crc32 = new CRC32();
		crc32.update(bytes);
		entry.setCrc(crc32.getValue());
		zip.putNextEntry(entry);
		zip.write(bytes);
		zip.closeEntry();
	}

	private void writeZipEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
		var entry = new ZipEntry(entryName);
		zip.putNextEntry(entry);
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private String ffmpegExecutable() {
		return resolveExecutable(this.processingProperties.getFfmpegExecutable())
			.map(Path::toString)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ffmpeg executable не найден в текущем backend окружении."));
	}

	private Duration videoTimeout() {
		return Duration.ofSeconds(this.processingProperties.getMediaPreviewTimeoutSeconds());
	}

	private void ensureArtifact(Path outputPath, String label) {
		try {
			if (!Files.exists(outputPath) || Files.size(outputPath) == 0L) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Команда завершилась без итогового office artifact: %s".formatted(label));
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать итоговый office artifact.", exception);
		}
	}

	private void execute(List<String> command, Path workingDirectory, Duration timeout) {
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
				.name("jack-office-conversion-output")
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
				throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Office conversion превысил допустимый timeout.");
			}

			outputReader.join(1_000L);
			if (outputFailure.get() != null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать вывод внешнего office processor.", outputFailure.get());
			}

			if (process.exitValue() != 0) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Команда завершилась с кодом %s: %s".formatted(process.exitValue(), normalizeCommandOutput(output.toByteArray()))
				);
			}
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось запустить внешний office processor.", exception);
		}
		catch (InterruptedException exception) {
			if (process != null) {
				process.destroy();
			}
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Office conversion был прерван.", exception);
		}
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

	private String normalizeCommandOutput(byte[] bytes) {
		var output = new String(bytes, StandardCharsets.UTF_8).trim();
		return output.isBlank() ? "no output" : output.replaceAll("\\s+", " ");
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

	public record OfficeConversionResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel,
		List<String> warnings
	) {
	}

	private record OfficeConversionOutput(
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

	private record OfficeConversionManifest(
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
		Instant generatedAt,
		List<String> warnings
	) {
	}

	private record StructuredDocument(
		List<StructuredBlock> blocks,
		String htmlDocument,
		List<DocumentPreviewPayload.DocumentFact> sourceFacts,
		List<String> warnings,
		String sourceAdapterLabel
	) {
	}

	private record SpreadsheetBundle(
		List<SheetData> sheets,
		List<DocumentPreviewPayload.DocumentFact> sourceFacts,
		List<String> warnings,
		String sourceAdapterLabel
	) {
	}

	private record SheetData(
		String name,
		List<List<String>> rows
	) {
	}

	private interface StructuredBlock {
	}

	private record TextBlock(
		String text,
		int level
	) implements StructuredBlock {
	}

	private record BulletBlock(
		List<String> items
	) implements StructuredBlock {
	}

	private record TableBlock(
		List<List<String>> rows
	) implements StructuredBlock {
	}

	private record Size(
		int width,
		int height
	) {
	}

	private record RectangleFit(
		float x,
		float y,
		float width,
		float height
	) {
	}
}
