package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
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
class EditorApiTests {

	private static final Path STORAGE_ROOT = createStorageRoot();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 1_048_576L);
	}

	@AfterAll
	static void cleanupStorageRoot() throws IOException {
		if (!Files.exists(STORAGE_ROOT)) {
			return;
		}

		try (var paths = Files.walk(STORAGE_ROOT)) {
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
	void editorProcessBuildsDiagnosticsAndPlainTextExportForHtml() throws Exception {
		var uploadId = upload(
			"landing.html",
			"text/html",
			"""
			<!doctype html>
			<html>
			  <body>
			    <a href="javascript:alert('x')" target="_blank">Unsafe link</a>
			    <button onclick="run()">Click</button>
			    <script>console.log('x')</script>
			    <h1>Landing</h1>
			    <p>Hello Jack</p>
			  </body>
			</html>
			"""
		);

		var completedJob = awaitJobCompletion(createEditorJob(uploadId, "html"));
		var artifacts = artifactIndex(completedJob);
		var manifest = downloadJsonArtifact(artifacts.get("editor-manifest"));

		assertThat(manifest.path("formatId").asText()).isEqualTo("html");
		assertThat(manifest.path("issues").toString()).contains("HTML_SCRIPT_TAG");
		assertThat(manifest.path("issues").toString()).contains("HTML_INLINE_HANDLER");
		assertThat(manifest.path("issues").toString()).contains("HTML_JAVASCRIPT_URL");
		assertThat(manifest.path("outline").size()).isEqualTo(1);
		assertThat(artifacts.get("editor-export-ready").path("mediaType").asText()).isEqualTo("text/html");
		assertThat(artifacts.get("editor-export-plain-text").path("mediaType").asText()).isEqualTo("text/plain");

		var plainText = new String(
			downloadArtifactBytes(artifacts.get("editor-export-plain-text")),
			StandardCharsets.UTF_8
		);
		assertThat(plainText).contains("Unsafe link", "Landing", "Hello Jack");
		assertThat(plainText).doesNotContain("<script>");
	}

	@Test
	void editorProcessReportsStrictJsonErrorsButStillProducesArtifacts() throws Exception {
		var uploadId = upload(
			"payload.json",
			"application/json",
			"""
			{
			  "alpha": [1, 2,
			}
			"""
		);

		var completedJob = awaitJobCompletion(createEditorJob(uploadId, "json"));
		var artifacts = artifactIndex(completedJob);
		var manifest = downloadJsonArtifact(artifacts.get("editor-manifest"));

		assertThat(manifest.path("formatId").asText()).isEqualTo("json");
		assertThat(manifest.path("issues").toString()).contains("JSON_PARSE_ERROR");
		assertThat(artifacts).containsKeys("editor-export-ready", "editor-export-plain-text");
		assertThat(artifacts.get("editor-export-ready").path("fileName").asText()).isEqualTo("payload.json");
	}

	private String upload(String fileName, String mediaType, String content) throws Exception {
		var response = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", fileName, mediaType, content.getBytes(StandardCharsets.UTF_8)))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);

		return response.path("id").asText();
	}

	private JsonNode createEditorJob(String uploadId, String formatId) throws Exception {
		return parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "EDITOR_PROCESS",
						  "parameters": {
						    "formatId": "%s"
						  }
						}
						""".formatted(uploadId, formatId))
			)
				.andExpect(status().isAccepted())
				.andReturn()
		);
	}

	private JsonNode awaitJobCompletion(JsonNode createdJob) throws Exception {
		var jobId = createdJob.path("id").asText();

		for (int attempt = 0; attempt < 40; attempt += 1) {
			var response = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var status = response.path("status").asText();

			if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
				return response;
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Editor processing job did not finish within the expected polling window.");
	}

	private Map<String, JsonNode> artifactIndex(JsonNode completedJob) {
		var index = new LinkedHashMap<String, JsonNode>();
		for (JsonNode artifact : completedJob.path("artifacts")) {
			index.put(artifact.path("kind").asText(), artifact);
		}
		return index;
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

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static Path createStorageRoot() {
		try {
			return Files.createTempDirectory("jack-editor-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp storage for editor tests.", exception);
		}
	}

}
