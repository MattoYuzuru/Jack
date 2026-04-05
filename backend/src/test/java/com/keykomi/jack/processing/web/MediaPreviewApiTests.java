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
class MediaPreviewApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path FAKE_FFMPEG = createExecutable(BIN_ROOT.resolve("fake-ffmpeg"), """
		#!/bin/bash
		set -euo pipefail

		output="${!#}"
		mkdir -p "$(dirname "$output")"
		printf 'preview artifact for %s\n' "$(basename "$output")" > "$output"
		""");
	private static final Path FAKE_FFPROBE = createExecutable(BIN_ROOT.resolve("fake-ffprobe"), """
		#!/bin/bash
		set -euo pipefail

		args="$*"
		if [[ "$args" == *"legacy.avi"* ]]; then
		  cat <<'JSON'
		{"format":{"duration":"12.5"},"streams":[{"codec_name":"mpeg4","width":640,"height":360}]}
		JSON
		  exit 0
		fi

		if [[ "$args" == *"legacy.flac"* ]]; then
		  cat <<'JSON'
		{"format":{"duration":"8.0"},"streams":[{"codec_name":"flac","sample_rate":"48000","channels":2}]}
		JSON
		  exit 0
		fi

		echo '{"format":{"duration":"1.0"},"streams":[{}]}'
		""");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 5_242_880L);
		registry.add("jack.processing.ffmpeg-executable", () -> FAKE_FFMPEG.toString());
		registry.add("jack.processing.ffprobe-executable", () -> FAKE_FFPROBE.toString());
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
	void videoMediaPreviewFlowProducesManifestAndBinaryArtifact() throws Exception {
		var uploadId = upload("legacy.avi", "video/x-msvideo", "video-source");
		var completedJob = awaitJobCompletion(createMediaPreviewJob(uploadId));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(2);

		var artifacts = artifactIndex(completedJob);
		assertThat(artifacts).containsKeys("media-preview-manifest", "media-preview-binary");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("media-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("family").asText()).isEqualTo("media");
		assertThat(manifest.path("runtimeLabel").asText()).isEqualTo("MP4 transcode");
		assertThat(manifest.path("previewMediaType").asText()).isEqualTo("video/mp4");
		assertThat(manifest.path("probe").path("durationSeconds").asDouble()).isEqualTo(12.5d);
		assertThat(manifest.path("probe").path("codecName").asText()).isEqualTo("mpeg4");
		assertThat(manifest.path("probe").path("width").asInt()).isEqualTo(640);
		assertThat(manifest.path("probe").path("height").asInt()).isEqualTo(360);

		var binaryArtifact = artifacts.get("media-preview-binary");
		var binaryContent = this.mockMvc.perform(get(binaryArtifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(binaryArtifact.path("fileName").asText()).isEqualTo("legacy.preview.mp4");
		assertThat(binaryArtifact.path("mediaType").asText()).isEqualTo("video/mp4");
		assertThat(binaryContent).contains("legacy.preview.mp4");
	}

	@Test
	void audioMediaPreviewFlowProducesManifestAndBinaryArtifact() throws Exception {
		var uploadId = upload("legacy.flac", "audio/flac", "audio-source");
		var completedJob = awaitJobCompletion(createMediaPreviewJob(uploadId));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(2);

		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("media-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("family").asText()).isEqualTo("audio");
		assertThat(manifest.path("runtimeLabel").asText()).isEqualTo("MP3 transcode");
		assertThat(manifest.path("previewMediaType").asText()).isEqualTo("audio/mpeg");
		assertThat(manifest.path("probe").path("durationSeconds").asDouble()).isEqualTo(8.0d);
		assertThat(manifest.path("probe").path("codecName").asText()).isEqualTo("flac");
		assertThat(manifest.path("probe").path("sampleRate").asInt()).isEqualTo(48_000);
		assertThat(manifest.path("probe").path("channelCount").asInt()).isEqualTo(2);

		var binaryArtifact = artifacts.get("media-preview-binary");
		var binaryContent = this.mockMvc.perform(get(binaryArtifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(binaryArtifact.path("fileName").asText()).isEqualTo("legacy.preview.mp3");
		assertThat(binaryArtifact.path("mediaType").asText()).isEqualTo("audio/mpeg");
		assertThat(binaryContent).contains("legacy.preview.mp3");
	}

	private String upload(String fileName, String mediaType, String payload) throws Exception {
		var uploadResponse = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", fileName, mediaType, payload.getBytes(StandardCharsets.UTF_8)))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);

		return uploadResponse.path("id").asText();
	}

	private String createMediaPreviewJob(String uploadId) throws Exception {
		var jobResponse = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "MEDIA_PREVIEW"
						}
						""".formatted(uploadId))
			)
				.andExpect(status().isAccepted())
				.andReturn()
		);

		return jobResponse.path("id").asText();
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

		throw new AssertionError("Media preview job did not finish within the expected polling window.");
	}

	private Map<String, JsonNode> artifactIndex(JsonNode jobResponse) {
		var iterator = jobResponse.path("artifacts").elements();
		var artifacts = new java.util.HashMap<String, JsonNode>();
		while (iterator.hasNext()) {
			var artifact = iterator.next();
			artifacts.put(artifact.path("kind").asText(), artifact);
		}

		return Map.copyOf(artifacts);
	}

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-media-preview-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for media preview tests.", exception);
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
				throw new IllegalStateException("Failed to mark test executable as runnable: " + path);
			}
			return path;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to create test executable: " + path, exception);
		}
	}

}
