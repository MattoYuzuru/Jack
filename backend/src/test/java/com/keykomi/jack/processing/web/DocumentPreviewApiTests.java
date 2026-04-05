package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
class DocumentPreviewApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 8_388_608L);
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
	void pdfPreviewFlowProducesManifestAndBinaryArtifact() throws Exception {
		var uploadId = upload("viewer.pdf", "application/pdf", createPdfBytes("Viewer PDF phase three"));
		var completedJob = awaitJobCompletion(createDocumentJob(uploadId));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		assertThat(artifacts).containsKeys("document-preview-manifest", "document-preview-binary");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("document-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("layout").path("mode").asText()).isEqualTo("pdf");
		assertThat(manifest.path("layout").path("pageCount").asInt()).isEqualTo(1);
		assertThat(manifest.path("searchableText").asText()).contains("Viewer PDF phase three");
		assertThat(artifacts.get("document-preview-binary").path("mediaType").asText()).isEqualTo("application/pdf");
	}

	@Test
	void htmlPreviewFlowSanitizesMarkupAndBuildsOutline() throws Exception {
		var uploadId = upload(
			"index.html",
			"text/html",
			"""
			<!doctype html>
			<html>
			  <body>
			    <h1>Viewer Docs</h1>
			    <p>Document intelligence is active.</p>
			    <script>alert('x')</script>
			  </body>
			</html>
			""".getBytes(StandardCharsets.UTF_8)
		);
		var completedJob = awaitJobCompletion(createDocumentJob(uploadId));
		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("document-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("layout").path("mode").asText()).isEqualTo("html");
		assertThat(manifest.path("layout").path("srcDoc").asText()).doesNotContain("<script>");
		assertThat(manifest.path("layout").path("outline").get(0).path("label").asText()).isEqualTo("Viewer Docs");
		assertThat(manifest.path("warnings")).hasSize(1);
	}

	@Test
	void docxPreviewFlowBuildsStructuredHtmlPayload() throws Exception {
		var uploadId = upload(
			"proposal.docx",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			createDocxBytes()
		);
		var completedJob = awaitJobCompletion(createDocumentJob(uploadId));
		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("document-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("layout").path("mode").asText()).isEqualTo("html");
		assertThat(manifest.path("layout").path("outline").get(0).path("label").asText()).isEqualTo("Viewer Heading");
		assertThat(manifest.path("searchableText").asText()).contains("Backend docx payload");
	}

	@Test
	void workbookPreviewFlowBuildsWorkbookLayout() throws Exception {
		var uploadId = upload(
			"sheet.xlsx",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			createWorkbookBytes()
		);
		var completedJob = awaitJobCompletion(createDocumentJob(uploadId));
		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("document-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("layout").path("mode").asText()).isEqualTo("workbook");
		assertThat(manifest.path("layout").path("sheets")).hasSize(1);
		assertThat(manifest.path("layout").path("sheets").get(0).path("table").path("columns").get(0).asText()).isEqualTo("Name");
		assertThat(manifest.path("layout").path("sheets").get(0).path("table").path("rows").get(0).get(0).asText()).isEqualTo("Jack");
	}

	@Test
	void slidesPreviewFlowBuildsSlideContract() throws Exception {
		var uploadId = upload(
			"deck.pptx",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			createSlidesBytes()
		);
		var completedJob = awaitJobCompletion(createDocumentJob(uploadId));
		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("document-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("layout").path("mode").asText()).isEqualTo("slides");
		assertThat(manifest.path("layout").path("slides")).hasSize(1);
		assertThat(manifest.path("layout").path("slides").get(0).path("bullets").get(0).asText()).isEqualTo("Backend slide bullet");
	}

	@Test
	void sqlitePreviewFlowBuildsDatabasePreview() throws Exception {
		var databasePath = TEST_ROOT.resolve("sample.sqlite");
		createSampleSqliteDatabase(databasePath);
		var uploadId = upload("sample.sqlite", "application/vnd.sqlite3", Files.readAllBytes(databasePath));
		var completedJob = awaitJobCompletion(createDocumentJob(uploadId));
		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("document-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("layout").path("mode").asText()).isEqualTo("database");
		assertThat(manifest.path("layout").path("tables")).hasSize(1);
		assertThat(manifest.path("layout").path("tables").get(0).path("name").asText()).isEqualTo("notes");
		assertThat(manifest.path("layout").path("tables").get(0).path("sample").path("rows").get(0).get(1).asText()).isEqualTo("viewer payload");
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

	private String createDocumentJob(String uploadId) throws Exception {
		var response = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "DOCUMENT_PREVIEW"
						}
						""".formatted(uploadId))
			)
				.andExpect(status().isAccepted())
				.andReturn()
		);
		return response.path("id").asText();
	}

	private JsonNode awaitJobCompletion(String jobId) throws Exception {
		for (int attempt = 0; attempt < 80; attempt += 1) {
			var response = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var status = response.path("status").asText();

			if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
				return response;
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Document processing job did not finish within the expected polling window.");
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

	private static byte[] createPdfBytes(String text) throws IOException {
		try (var document = new PDDocument();
			var outputStream = new ByteArrayOutputStream()) {
			var page = new PDPage();
			document.addPage(page);

			try (var contentStream = new PDPageContentStream(document, page)) {
				contentStream.beginText();
				contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				contentStream.newLineAtOffset(60, 720);
				contentStream.showText(text);
				contentStream.endText();
			}

			document.save(outputStream);
			return outputStream.toByteArray();
		}
	}

	private static byte[] createDocxBytes() throws IOException {
		try (var document = new XWPFDocument();
			var outputStream = new ByteArrayOutputStream()) {
			var heading = document.createParagraph();
			heading.setStyle("Heading1");
			heading.createRun().setText("Viewer Heading");

			var paragraph = document.createParagraph();
			paragraph.createRun().setText("Backend docx payload");

			document.write(outputStream);
			return outputStream.toByteArray();
		}
	}

	private static byte[] createWorkbookBytes() throws IOException {
		try (var workbook = new XSSFWorkbook();
			var outputStream = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("Overview");
			sheet.createRow(0).createCell(0).setCellValue("Name");
			sheet.getRow(0).createCell(1).setCellValue("Value");
			sheet.createRow(1).createCell(0).setCellValue("Jack");
			sheet.getRow(1).createCell(1).setCellValue("42");
			workbook.write(outputStream);
			return outputStream.toByteArray();
		}
	}

	private static byte[] createSlidesBytes() throws IOException {
		try (var slideShow = new XMLSlideShow();
			var outputStream = new ByteArrayOutputStream()) {
			var slide = slideShow.createSlide();
			XSLFTextBox textBox = slide.createTextBox();
			textBox.setText("Backend slide bullet");
			slideShow.write(outputStream);
			return outputStream.toByteArray();
		}
	}

	private static void createSampleSqliteDatabase(Path databasePath) throws Exception {
		Files.deleteIfExists(databasePath);
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath())) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT)");
				statement.execute("INSERT INTO notes(body) VALUES ('viewer payload')");
			}
		}
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-document-preview-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp test root.", exception);
		}
	}

	private static Path createDirectory(Path path) {
		try {
			return Files.createDirectories(path);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create temp directory.", exception);
		}
	}

}
