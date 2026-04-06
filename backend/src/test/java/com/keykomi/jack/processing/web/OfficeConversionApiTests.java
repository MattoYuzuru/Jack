package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OfficeConversionApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path COMMAND_LOG = TEST_ROOT.resolve("commands.log");
	private static final Path FAKE_FFMPEG = createExecutable(BIN_ROOT.resolve("fake-ffmpeg"), """
		#!/bin/bash
		set -euo pipefail

		log_file=%s
		printf 'ffmpeg %%s\\n' "$*" >> "$log_file"
		output="${!#}"
		mkdir -p "$(dirname "$output")"
		printf 'fake-mp4-artifact' > "$output"
		""".formatted(COMMAND_LOG.toString()));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 8_388_608L);
		registry.add("jack.processing.ffmpeg-executable", () -> FAKE_FFMPEG.toString());
		registry.add("jack.processing.media-preview-timeout-seconds", () -> 5L);
	}

	@AfterAll
	static void cleanupTestRoot() throws IOException {
		if (!Files.exists(TEST_ROOT)) {
			return;
		}

		try (var paths = Files.walk(TEST_ROOT)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
				}
			});
		}
	}

	@Test
	void pdfExportsProduceDocxPngAndPptxArtifacts() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);
		var uploadId = upload("brief.pdf", "application/pdf", createPdfBytes(List.of("Office convert page one", "Office convert page two")));

		var docxJob = awaitCompletedJob(createOfficeJob(uploadId, "docx"));
		var docxArtifacts = artifactIndex(docxJob);
		assertThat(docxArtifacts).containsKeys(
			"office-convert-manifest",
			"office-convert-binary",
			"office-convert-preview"
		);

		try (var docxInput = new ByteArrayInputStream(
			this.mockMvc.perform(get(docxArtifacts.get("office-convert-binary").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray()
		);
			var document = new XWPFDocument(docxInput)) {
			var text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", String::concat);
			assertThat(text).contains("Office convert page one");
		}

		var pngJob = awaitCompletedJob(createOfficeJob(uploadId, "png"));
		var pngArtifacts = artifactIndex(pngJob);
		var pngManifest = parseJson(
			this.mockMvc.perform(get(pngArtifacts.get("office-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(pngArtifacts.get("office-convert-binary").path("mediaType").asText()).isEqualTo("image/png");
		assertThat(pngManifest.path("warnings").toString()).contains("длинный лист предпросмотра");

		var pptxJob = awaitCompletedJob(createOfficeJob(uploadId, "pptx"));
		var pptxArtifacts = artifactIndex(pptxJob);
		try (var pptxInput = new ByteArrayInputStream(
			this.mockMvc.perform(get(pptxArtifacts.get("office-convert-binary").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray()
		);
			var slideShow = new XMLSlideShow(pptxInput)) {
			assertThat(slideShow.getSlides()).hasSize(2);
		}
	}

	@Test
	void spreadsheetExportsProduceXlsxAndOdsRoundtripArtifacts() throws Exception {
		var csvUploadId = upload("sheet.csv", "text/csv", "Name,Role\nJack,Bridge\n".getBytes(StandardCharsets.UTF_8));
		var csvToXlsxJob = awaitCompletedJob(createOfficeJob(csvUploadId, "xlsx"));
		var csvToXlsxArtifacts = artifactIndex(csvToXlsxJob);
		assertThat(csvToXlsxArtifacts.get("office-convert-preview").path("mediaType").asText()).isEqualTo("text/html");

		try (var workbookInput = new ByteArrayInputStream(
			this.mockMvc.perform(get(csvToXlsxArtifacts.get("office-convert-binary").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray()
		);
			var workbook = new XSSFWorkbook(workbookInput)) {
			assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("Jack");
			assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("Bridge");
		}

		var odsUploadId = upload(
			"ledger.ods",
			"application/vnd.oasis.opendocument.spreadsheet",
			createOdsBytes()
		);
		var odsToXlsxJob = awaitCompletedJob(createOfficeJob(odsUploadId, "xlsx"));
		var odsToXlsxArtifacts = artifactIndex(odsToXlsxJob);
		try (var workbookInput = new ByteArrayInputStream(
			this.mockMvc.perform(get(odsToXlsxArtifacts.get("office-convert-binary").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray()
		);
			var workbook = new XSSFWorkbook(workbookInput)) {
			assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Quarter");
			assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("12");
		}
	}

	@Test
	void presentationExportsProducePdfAndMp4Artifacts() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);
		var uploadId = upload(
			"deck.pptx",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			createSlidesBytes()
		);

		var pdfJob = awaitCompletedJob(createOfficeJob(uploadId, "pdf"));
		var pdfArtifacts = artifactIndex(pdfJob);
		var pdfManifest = parseJson(
			this.mockMvc.perform(get(pdfArtifacts.get("office-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(pdfArtifacts.get("office-convert-binary").path("mediaType").asText()).isEqualTo("application/pdf");
		assertThat(pdfManifest.path("warnings").toString()).contains("сохраняет внешний вид каждого слайда");

		var mp4Job = awaitCompletedJob(createOfficeJob(uploadId, "mp4"));
		var mp4Artifacts = artifactIndex(mp4Job);
		var mp4Manifest = parseJson(
			this.mockMvc.perform(get(mp4Artifacts.get("office-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(mp4Artifacts.get("office-convert-binary").path("mediaType").asText()).isEqualTo("video/mp4");
		assertThat(mp4Manifest.path("previewKind").asText()).isEqualTo("media");
		assertThat(Files.readString(COMMAND_LOG, StandardCharsets.UTF_8)).contains("ffmpeg ");
	}

	private String upload(String fileName, String mediaType, byte[] bytes) throws Exception {
		var response = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", fileName, mediaType, bytes))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);
		return response.path("id").asText();
	}

	private String createOfficeJob(String uploadId, String targetExtension) throws Exception {
		var response = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "OFFICE_CONVERT",
						  "parameters": {
						    "targetExtension": "%s"
						  }
						}
						""".formatted(uploadId, targetExtension))
			)
				.andExpect(status().isAccepted())
				.andReturn()
		);
		return response.path("id").asText();
	}

	private JsonNode awaitJobCompletion(String jobId) throws Exception {
		for (int attempt = 0; attempt < 100; attempt += 1) {
			var response = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var status = response.path("status").asText();

			if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
				return response;
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Processing job did not finish within the expected polling window.");
	}

	private JsonNode awaitCompletedJob(String jobId) throws Exception {
		var job = awaitJobCompletion(jobId);
		assertThat(job.path("status").asText())
			.withFailMessage(
				"Expected office conversion job %s to complete but got status=%s, error=%s, payload=%s",
				jobId,
				job.path("status").asText(),
				job.path("errorMessage").asText(),
				job.toPrettyString()
			)
			.isEqualTo("COMPLETED");
		return job;
	}

	private Map<String, JsonNode> artifactIndex(JsonNode job) {
		var artifacts = new LinkedHashMap<String, JsonNode>();
		for (JsonNode artifact : job.path("artifacts")) {
			artifacts.put(artifact.path("kind").asText(), artifact);
		}
		return artifacts;
	}

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static byte[] createPdfBytes(List<String> pageTexts) throws IOException {
		try (var output = new ByteArrayOutputStream(); var document = new PDDocument()) {
			for (String pageText : pageTexts) {
				var page = new PDPage();
				document.addPage(page);
				try (var content = new PDPageContentStream(document, page)) {
					content.beginText();
					content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
					content.newLineAtOffset(72, 700);
					content.showText(pageText);
					content.endText();
				}
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private static byte[] createSlidesBytes() throws IOException {
		try (var output = new ByteArrayOutputStream(); var slideShow = new XMLSlideShow()) {
			var slideOne = slideShow.createSlide();
			var boxOne = slideOne.createTextBox();
			boxOne.setText("Quarter Review");

			var slideTwo = slideShow.createSlide();
			var boxTwo = slideTwo.createTextBox();
			boxTwo.setText("Ops Follow-up");

			slideShow.write(output);
			return output.toByteArray();
		}
	}

	private static byte[] createOdsBytes() throws IOException {
		try (var output = new ByteArrayOutputStream()) {
			try (var zip = new ZipOutputStream(output)) {
				writeStoredZipEntry(zip, "mimetype", "application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.UTF_8));
				writeZipEntry(
					zip,
					"META-INF/manifest.xml",
					"""
						<?xml version="1.0" encoding="UTF-8"?>
						<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
						  <manifest:file-entry manifest:media-type="application/vnd.oasis.opendocument.spreadsheet" manifest:full-path="/"/>
						  <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="content.xml"/>
						</manifest:manifest>
						"""
				);
				writeZipEntry(
					zip,
					"content.xml",
					"""
						<?xml version="1.0" encoding="UTF-8"?>
						<office:document-content
						  xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
						  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
						  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
						  office:version="1.2">
						  <office:body>
						    <office:spreadsheet>
						      <table:table table:name="Summary">
						        <table:table-row>
						          <table:table-cell office:value-type="string"><text:p>Quarter</text:p></table:table-cell>
						          <table:table-cell office:value-type="string"><text:p>Value</text:p></table:table-cell>
						        </table:table-row>
						        <table:table-row>
						          <table:table-cell office:value-type="string"><text:p>Q1</text:p></table:table-cell>
						          <table:table-cell office:value-type="string"><text:p>12</text:p></table:table-cell>
						        </table:table-row>
						      </table:table>
						    </office:spreadsheet>
						  </office:body>
						</office:document-content>
						"""
				);
			}
			return output.toByteArray();
		}
	}

	private static void writeStoredZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
		var entry = new ZipEntry(name);
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

	private static void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-office-convert-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for office conversion tests.", exception);
		}
	}

	private static Path createDirectory(Path path) {
		try {
			return Files.createDirectories(path);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create test directory " + path, exception);
		}
	}

	private static Path createExecutable(Path path, String script) {
		try {
			Files.writeString(path, script, StandardCharsets.UTF_8);
			path.toFile().setExecutable(true);
			return path;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create test executable " + path, exception);
		}
	}
}
