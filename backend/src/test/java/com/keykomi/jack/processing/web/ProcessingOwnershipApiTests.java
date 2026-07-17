package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.application.ProcessingStateStore;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
class ProcessingOwnershipApiTests {

	private static final Path STORAGE_ROOT = createStorageRoot();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProcessingStateStore stateStore;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", STORAGE_ROOT::toString);
		registry.add("jack.processing.fixed-session-owner", () -> "");
		registry.add("jack.processing.max-concurrent-jobs-per-session", () -> 1);
		registry.add("jack.processing.max-storage-bytes-per-session", () -> 32L);
	}

	@Test
	void quotaRejectsBeforeCreatingJobOrUploadMetadata() throws Exception {
		var ownerCookie = newSession(null);
		var ownerId = UUID.fromString(ownerCookie.getValue().substring(0, ownerCookie.getValue().indexOf('.')));
		var upload = parse(this.mockMvc.perform(
			multipart("/api/uploads")
				.file(new MockMultipartFile("file", "small.txt", "text/plain", "small".getBytes()))
				.cookie(ownerCookie)
		).andExpect(status().isCreated()).andReturn());
		var now = Instant.now();
		var active = StoredProcessingJob.queued(
			UUID.randomUUID(), UUID.fromString(upload.path("id").asText()), ProcessingJobType.DOCUMENT_PREVIEW,
			Map.of(), now, now.plusSeconds(3_600), "test-policy", UUID.randomUUID()
		);
		this.stateStore.createJob(ownerId, active);

		this.mockMvc.perform(
			post("/api/jobs").cookie(ownerCookie).contentType(APPLICATION_JSON).content("""
				{"uploadId":"%s","jobType":"UPLOAD_INTAKE_ANALYSIS"}
				""".formatted(upload.path("id").asText()))
		).andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "5"))
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));

		this.mockMvc.perform(
			multipart("/api/uploads")
				.file(new MockMultipartFile("file", "large.txt", "text/plain", "x".repeat(40).getBytes()))
				.cookie(ownerCookie)
		).andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	@Test
	void foreignSessionCannotReadCancelOrDownloadResources() throws Exception {
		var ownerCookie = newSession(null);
		var foreignCookie = newSession(new Cookie("JACK_SESSION", "invalid.signature"));
		var upload = parse(this.mockMvc.perform(
			multipart("/api/uploads")
				.file(new MockMultipartFile("file", "owner.txt", "text/plain", "owner-data".getBytes()))
				.cookie(ownerCookie)
		).andExpect(status().isCreated()).andReturn());

		this.mockMvc.perform(get("/api/uploads/{id}", upload.path("id").asText()).cookie(foreignCookie))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		var job = parse(this.mockMvc.perform(
			post("/api/jobs").cookie(ownerCookie).contentType(APPLICATION_JSON).content("""
				{"uploadId":"%s","jobType":"UPLOAD_INTAKE_ANALYSIS"}
				""".formatted(upload.path("id").asText()))
		).andExpect(status().isAccepted()).andReturn());
		var completed = awaitTerminal(job.path("id").asText(), ownerCookie);
		var artifactPath = completed.path("artifacts").get(0).path("downloadPath").asText();

		this.mockMvc.perform(get("/api/jobs/{id}", job.path("id").asText()).cookie(foreignCookie))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(delete("/api/jobs/{id}", job.path("id").asText()).cookie(foreignCookie))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(get(artifactPath).cookie(foreignCookie))
			.andExpect(status().isNotFound());

		this.mockMvc.perform(get(artifactPath).cookie(ownerCookie).header("Range", "bytes=0-31"))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		assertThat(completed.path("correlationId").asText()).isNotBlank();
	}

	@Test
	void crossOriginMutationRequiresExplicitClientIntentHeader() throws Exception {
		var file = new MockMultipartFile("file", "csrf.txt", "text/plain", "csrf".getBytes());
		this.mockMvc.perform(
			multipart("/api/uploads")
				.file(file)
				.header("Origin", "http://localhost:5173")
		).andExpect(status().isForbidden())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));

		this.mockMvc.perform(
			multipart("/api/uploads")
				.file(file)
				.header("Origin", "http://localhost:5173")
				.header("X-Jack-Request", "processing")
		).andExpect(status().isCreated());
	}

	private Cookie newSession(Cookie input) throws Exception {
		var request = get("/api/capabilities/platform");
		if (input != null) {
			request.cookie(input);
		}
		var result = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return result.getResponse().getCookie("JACK_SESSION");
	}

	private JsonNode awaitTerminal(String jobId, Cookie cookie) throws Exception {
		for (int attempt = 0; attempt < 50; attempt += 1) {
			var response = parse(this.mockMvc.perform(get("/api/jobs/{id}", jobId).cookie(cookie)).andReturn());
			if (response.path("status").asText().matches("COMPLETED|FAILED|CANCELLED")) {
				return response;
			}
			Thread.sleep(20L);
		}
		throw new AssertionError("Job did not reach terminal state");
	}

	private JsonNode parse(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static Path createStorageRoot() {
		try {
			return Files.createTempDirectory("jack-processing-owner-tests");
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
