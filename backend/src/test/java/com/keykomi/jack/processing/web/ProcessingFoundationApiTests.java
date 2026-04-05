package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
class ProcessingFoundationApiTests {

	private static final Path STORAGE_ROOT = createStorageRoot();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 1_048_576L);
		registry.add("jack.processing.ffmpeg-executable", () -> STORAGE_ROOT.resolve("missing-ffmpeg").toString());
		registry.add("jack.processing.ffprobe-executable", () -> STORAGE_ROOT.resolve("missing-ffprobe").toString());
		registry.add("jack.processing.image-convert-executable", () -> STORAGE_ROOT.resolve("missing-convert").toString());
		registry.add("jack.processing.potrace-executable", () -> STORAGE_ROOT.resolve("missing-potrace").toString());
		registry.add("jack.processing.raw-preview-executable", () -> STORAGE_ROOT.resolve("missing-dcraw").toString());
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
	void uploadJobAndArtifactFlowWorks() throws Exception {
		var uploadResponse = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", "sample.txt", "text/plain", "hello jack".getBytes()))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);

		var uploadId = uploadResponse.path("id").asText();
		assertThat(uploadResponse.path("originalFileName").asText()).isEqualTo("sample.txt");
		assertThat(uploadResponse.path("sha256").asText()).hasSize(64);

		var jobResponse = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "UPLOAD_INTAKE_ANALYSIS"
						}
						""".formatted(uploadId))
			)
				.andExpect(status().isAccepted())
				.andReturn()
		);

		var completedJob = awaitJobCompletion(jobResponse.path("id").asText());
		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(1);

		var artifact = completedJob.path("artifacts").get(0);
		this.mockMvc.perform(get(artifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.originalFileName").value("sample.txt"))
			.andExpect(jsonPath("$.detectedFamily").value("document"));
	}

	@Test
	void capabilityEndpointsExposeFoundationState() throws Exception {
		this.mockMvc.perform(get("/api/capabilities/viewer"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scope").value("viewer"))
			.andExpect(jsonPath("$.phase").value("imaging-foundation"))
			.andExpect(jsonPath("$.jobTypes[0].implemented").value(true))
			.andExpect(jsonPath("$.jobTypes[1].implemented").value(false))
			.andExpect(jsonPath("$.jobTypes[2].implemented").value(false));

		this.mockMvc.perform(get("/api/capabilities/converter"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scope").value("converter"))
			.andExpect(jsonPath("$.jobTypes[1].implemented").value(false))
			.andExpect(jsonPath("$.jobTypes[2].implemented").value(false));
	}

	private JsonNode awaitJobCompletion(String jobId) throws Exception {
		for (int attempt = 0; attempt < 40; attempt += 1) {
			var response = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var status = response.path("status").asText();

			if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
				return response;
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Processing job did not finish within the expected polling window.");
	}

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static Path createStorageRoot() {
		try {
			return Files.createTempDirectory("jack-processing-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp processing storage for tests.", exception);
		}
	}

}
