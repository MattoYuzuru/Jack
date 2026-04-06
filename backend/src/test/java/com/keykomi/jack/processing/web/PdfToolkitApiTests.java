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
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
class PdfToolkitApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path OCR_TEMPLATE_PDF = createTemplatePdf(TEST_ROOT.resolve("ocr-template.pdf"), "OCR template page");
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path FAKE_TESSERACT = createExecutable(BIN_ROOT.resolve("fake-tesseract"), """
		#!/bin/bash
		set -euo pipefail

		input="$1"
		output="$2"
		printf 'OCR text for %%s\\n' "$(basename "$input")" > "${output}.txt"
		cp "%s" "${output}.pdf"
		""".formatted(OCR_TEMPLATE_PDF));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 8_388_608L);
		registry.add("jack.processing.tesseract-executable", () -> FAKE_TESSERACT.toString());
		registry.add("jack.processing.pdf-toolkit-timeout-seconds", () -> 5L);
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
	void mergeOperationProducesCombinedPdfArtifact() throws Exception {
		var primaryUploadId = upload("primary.pdf", "application/pdf", createPdfBytes("Primary page"));
		var secondaryUploadId = upload("secondary.pdf", "application/pdf", createPdfBytes("Secondary page A", "Secondary page B"));

		var completedJob = awaitJobCompletion(createPdfToolkitJob(primaryUploadId, """
			{
			  "operation": "merge",
			  "additionalUploadIds": ["%s"]
			}
			""".formatted(secondaryUploadId)));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		var manifest = downloadJsonArtifact(artifacts.get("pdf-toolkit-manifest"));
		assertThat(manifest.path("operation").asText()).isEqualTo("MERGE");
		assertThat(manifest.path("resultMediaType").asText()).isEqualTo("application/pdf");
		assertThat(manifest.path("resultPageCount").asInt()).isEqualTo(3);

		try (var resultDocument = Loader.loadPDF(downloadArtifactBytes(artifacts.get("pdf-toolkit-binary")))) {
			assertThat(resultDocument.getNumberOfPages()).isEqualTo(3);
		}
	}

	@Test
	void splitOperationProducesZipBundleAndPreviewPdf() throws Exception {
		var uploadId = upload("split.pdf", "application/pdf", createPdfBytes("Split page 1", "Split page 2", "Split page 3"));
		var completedJob = awaitJobCompletion(createPdfToolkitJob(uploadId, """
			{
			  "operation": "split",
			  "splitRanges": ["1-2", "3"]
			}
			"""));

		var artifacts = artifactIndex(completedJob);
		var manifest = downloadJsonArtifact(artifacts.get("pdf-toolkit-manifest"));
		assertThat(manifest.path("operation").asText()).isEqualTo("SPLIT");
		assertThat(manifest.path("resultMediaType").asText()).isEqualTo("application/zip");
		assertThat(artifacts.get("pdf-toolkit-preview").path("mediaType").asText()).isEqualTo("application/pdf");

		var entries = new java.util.ArrayList<String>();
		try (
			var inputStream = new ZipInputStream(new ByteArrayInputStream(downloadArtifactBytes(artifacts.get("pdf-toolkit-binary"))))
		) {
			java.util.zip.ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				entries.add(entry.getName());
			}
		}
		assertThat(entries).hasSize(2);
	}

	@Test
	void rotateAndReorderOperationsProduceEditedPdf() throws Exception {
		var uploadId = upload("arrange.pdf", "application/pdf", createPdfBytes("Arrange page 1", "Arrange page 2"));

		var rotatedJob = awaitJobCompletion(createPdfToolkitJob(uploadId, """
			{
			  "operation": "rotate",
			  "rotationDegrees": 90,
			  "pageSelection": "2"
			}
			"""));
		var rotatedArtifacts = artifactIndex(rotatedJob);
		try (var rotatedDocument = Loader.loadPDF(downloadArtifactBytes(rotatedArtifacts.get("pdf-toolkit-binary")))) {
			assertThat(rotatedDocument.getPage(1).getRotation()).isEqualTo(90);
		}

		var reorderedJob = awaitJobCompletion(createPdfToolkitJob(uploadId, """
			{
			  "operation": "reorder",
			  "pageOrder": [2, 1]
			}
			"""));
		var reorderManifest = downloadJsonArtifact(artifactIndex(reorderedJob).get("pdf-toolkit-manifest"));
		assertThat(reorderManifest.path("operation").asText()).isEqualTo("REORDER");
		assertThat(reorderManifest.path("resultPageCount").asInt()).isEqualTo(2);
	}

	@Test
	void ocrOperationProducesSearchablePdfAndTextArtifact() throws Exception {
		var uploadId = upload("scan.pdf", "application/pdf", createPdfBytes("Scanned visual page", "Second scanned page"));
		var completedJob = awaitJobCompletion(createPdfToolkitJob(uploadId, """
			{
			  "operation": "ocr",
			  "ocrLanguage": "eng"
			}
			"""));

		var artifacts = artifactIndex(completedJob);
		var manifest = downloadJsonArtifact(artifacts.get("pdf-toolkit-manifest"));
		assertThat(manifest.path("operation").asText()).isEqualTo("OCR");
		assertThat(artifacts).containsKey("pdf-toolkit-text");

		var textArtifact = new String(downloadArtifactBytes(artifacts.get("pdf-toolkit-text")), StandardCharsets.UTF_8);
		assertThat(textArtifact).contains("OCR text for");
		try (var ocrDocument = Loader.loadPDF(downloadArtifactBytes(artifacts.get("pdf-toolkit-binary")))) {
			assertThat(ocrDocument.getNumberOfPages()).isEqualTo(2);
		}
	}

	@Test
	void protectAndUnlockOperationsRoundTrip() throws Exception {
		var uploadId = upload("secure.pdf", "application/pdf", createPdfBytes("Protected page"));
		var protectedJob = awaitJobCompletion(createPdfToolkitJob(uploadId, """
			{
			  "operation": "protect",
			  "ownerPassword": "owner-secret",
			  "userPassword": "user-secret",
			  "allowPrinting": false,
			  "allowCopying": false,
			  "allowModifying": false
			}
			"""));

		var protectedArtifacts = artifactIndex(protectedJob);
		var protectedBytes = downloadArtifactBytes(protectedArtifacts.get("pdf-toolkit-binary"));
		assertThat(isEncrypted(protectedBytes)).isTrue();

		var protectedUploadId = upload("secure.protected.pdf", "application/pdf", protectedBytes);
		var unlockedJob = awaitJobCompletion(createPdfToolkitJob(protectedUploadId, """
			{
			  "operation": "unlock",
			  "currentPassword": "user-secret"
			}
			"""));
		var unlockedBytes = downloadArtifactBytes(artifactIndex(unlockedJob).get("pdf-toolkit-binary"));
		assertThat(isEncrypted(unlockedBytes)).isFalse();
	}

	@Test
	void signAndRedactOperationsProduceWarningsAndPdfArtifacts() throws Exception {
		var uploadId = upload("review.pdf", "application/pdf", createPdfBytes("Secret token 123"));
		var signedJob = awaitJobCompletion(createPdfToolkitJob(uploadId, """
			{
			  "operation": "sign",
			  "signatureText": "Jack QA",
			  "signaturePlacement": "top-left",
			  "pageSelection": "1",
			  "includeSignatureDate": true
			}
			"""));

		var signedManifest = downloadJsonArtifact(artifactIndex(signedJob).get("pdf-toolkit-manifest"));
		assertThat(signedManifest.path("warnings").toString()).contains("stamp-mark");

		var signedUploadId = upload("review.signed.pdf", "application/pdf", downloadArtifactBytes(artifactIndex(signedJob).get("pdf-toolkit-binary")));
		var redactedJob = awaitJobCompletion(createPdfToolkitJob(signedUploadId, """
			{
			  "operation": "redact",
			  "redactTerms": ["Secret token 123"],
			  "pageSelection": "1"
			}
			"""));

		var redactedManifest = downloadJsonArtifact(artifactIndex(redactedJob).get("pdf-toolkit-manifest"));
		assertThat(redactedManifest.path("warnings").toString()).contains("raster PDF");
		try (var redactedDocument = Loader.loadPDF(downloadArtifactBytes(artifactIndex(redactedJob).get("pdf-toolkit-binary")))) {
			assertThat(redactedDocument.getNumberOfPages()).isEqualTo(1);
		}
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

	private String createPdfToolkitJob(String uploadId, String parametersJson) throws Exception {
		var response = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "PDF_TOOLKIT",
						  "parameters": %s
						}
						""".formatted(uploadId, parametersJson))
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

		throw new AssertionError("PDF toolkit job did not finish within the expected polling window.");
	}

	private Map<String, JsonNode> artifactIndex(JsonNode jobResponse) {
		var artifacts = new LinkedHashMap<String, JsonNode>();
		for (JsonNode artifact : jobResponse.path("artifacts")) {
			artifacts.put(artifact.path("kind").asText(), artifact);
		}
		return Map.copyOf(artifacts);
	}

	private JsonNode downloadJsonArtifact(JsonNode artifact) throws Exception {
		return parseJson(
			this.mockMvc.perform(get(artifact.path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
	}

	private byte[] downloadArtifactBytes(JsonNode artifact) throws Exception {
		return this.mockMvc.perform(get(artifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
	}

	private boolean isEncrypted(byte[] pdfBytes) throws IOException {
		try (var document = Loader.loadPDF(pdfBytes, "user-secret")) {
			return document.isEncrypted();
		}
		catch (IOException exception) {
			return true;
		}
	}

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static byte[] createPdfBytes(String... pageTexts) {
		try (var document = new PDDocument(); var outputStream = new ByteArrayOutputStream()) {
			var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (String pageText : pageTexts) {
				var page = new PDPage();
				document.addPage(page);
				try (var contentStream = new PDPageContentStream(document, page)) {
					contentStream.beginText();
					contentStream.setFont(font, 18f);
					contentStream.newLineAtOffset(72f, 720f);
					contentStream.showText(pageText);
					contentStream.endText();
				}
			}
			document.save(outputStream);
			return outputStream.toByteArray();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create PDF fixture.", exception);
		}
	}

	private static Path createTemplatePdf(Path path, String pageText) {
		try {
			Files.write(path, createPdfBytes(pageText));
			return path;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to write OCR template PDF.", exception);
		}
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-pdf-toolkit-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for PDF toolkit tests.", exception);
		}
	}

	private static Path createDirectory(Path path) {
		try {
			return Files.createDirectories(path);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create test directory: " + path, exception);
		}
	}

	private static Path createExecutable(Path path, String content) {
		try {
			Files.writeString(path, content, StandardCharsets.UTF_8);
			if (!path.toFile().setExecutable(true)) {
				throw new IllegalStateException("Failed to mark executable as runnable: " + path);
			}
			return path;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create fake executable: " + path, exception);
		}
	}

}
