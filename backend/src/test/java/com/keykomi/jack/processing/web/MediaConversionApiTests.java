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
class MediaConversionApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path FAKE_FFMPEG = createExecutable(BIN_ROOT.resolve("fake-ffmpeg"), """
		#!/bin/bash
		set -euo pipefail

		output="${!#}"
		mkdir -p "$(dirname "$output")"
		printf '%s\n' "$*" > "$output"
		""");
	private static final Path FAKE_FFPROBE = createExecutable(BIN_ROOT.resolve("fake-ffprobe"), """
		#!/bin/bash
		set -euo pipefail

		args="$*"
		if [[ "$args" == *"clip.mkv"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"matroska","duration":"12.5","bit_rate":"8200000"},"streams":[{"codec_type":"video","codec_name":"hevc","width":3840,"height":2160,"avg_frame_rate":"60000/1001","bit_rate":"7800000"},{"codec_type":"audio","codec_name":"aac","sample_rate":"48000","channels":2,"bit_rate":"192000"}]}
		JSON
		  exit 0
		fi

		if [[ "$args" == *"clip.mp4"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"12.5","bit_rate":"2660000"},"streams":[{"codec_type":"video","codec_name":"h264","width":1280,"height":720,"avg_frame_rate":"24/1","bit_rate":"2500000"},{"codec_type":"audio","codec_name":"aac","sample_rate":"48000","channels":2,"bit_rate":"160000"}]}
		JSON
		  exit 0
		fi

		if [[ "$args" == *"song.flac"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"flac","duration":"8.0","bit_rate":"920000"},"streams":[{"codec_type":"audio","codec_name":"flac","sample_rate":"48000","channels":2,"bit_rate":"920000"}]}
		JSON
		  exit 0
		fi

		if [[ "$args" == *"song.mp3"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"mp3","duration":"8.0","bit_rate":"192000"},"streams":[{"codec_type":"audio","codec_name":"mp3","sample_rate":"44100","channels":2,"bit_rate":"192000"}]}
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
	void videoMediaConvertFlowProducesManifestAndPreviewArtifacts() throws Exception {
		var uploadId = upload("clip.mkv", "video/x-matroska", "video-source");
		var completedJob = awaitJobCompletion(createMediaConvertJob(uploadId, """
			{
			  "targetExtension": "mp4",
			  "videoCodec": "h264",
			  "maxWidth": 1280,
			  "maxHeight": 720,
			  "targetFps": 24,
			  "videoBitrateKbps": 2500,
			  "audioBitrateKbps": 160
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(3);

		var artifacts = artifactIndex(completedJob);
		assertThat(artifacts).containsKeys("media-convert-manifest", "media-convert-binary", "media-convert-preview");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("media-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("targetExtension").asText()).isEqualTo("mp4");
		assertThat(manifest.path("previewKind").asText()).isEqualTo("media");
		assertThat(manifest.path("resultMediaType").asText()).isEqualTo("video/mp4");

		var sourceFacts = factIndex(manifest.path("sourceFacts"));
		assertThat(sourceFacts.get("Контейнер")).isEqualTo("MATROSKA");
		assertThat(sourceFacts.get("Video codec")).isEqualTo("H.265 / HEVC");
		assertThat(sourceFacts.get("Resolution")).isEqualTo("3840 x 2160");
		assertThat(sourceFacts.get("FPS")).startsWith("59.94");

		var resultFacts = factIndex(manifest.path("resultFacts"));
		assertThat(resultFacts.get("Контейнер")).isEqualTo("MP4");
		assertThat(resultFacts.get("Video codec")).isEqualTo("H.264");
		assertThat(resultFacts.get("Resolution")).isEqualTo("1280 x 720");
		assertThat(resultFacts.get("FPS")).isEqualTo("24 fps");

		assertThat(manifest.path("warnings")).anyMatch(node -> node.asText().contains("Resolution изменена отдельно от контейнера"));
		assertThat(manifest.path("warnings")).anyMatch(node -> node.asText().contains("Target FPS"));
		assertThat(manifest.path("warnings")).anyMatch(node -> node.asText().contains("Target bitrate"));

		var binaryArtifact = artifacts.get("media-convert-binary");
		var binaryContent = this.mockMvc.perform(get(binaryArtifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(binaryArtifact.path("fileName").asText()).isEqualTo("clip.mp4");
		assertThat(binaryArtifact.path("mediaType").asText()).isEqualTo("video/mp4");
		assertThat(binaryContent).contains("-c:v libx264");
		assertThat(binaryContent).contains("-b:v 2500k");
		assertThat(binaryContent).contains("-b:a 160k");
		assertThat(binaryContent).contains("scale=1280:720");
		assertThat(binaryContent).contains("fps=24");

		var previewArtifact = artifacts.get("media-convert-preview");
		assertThat(previewArtifact.path("fileName").asText()).isEqualTo("clip.preview.mp4");
		assertThat(previewArtifact.path("mediaType").asText()).isEqualTo("video/mp4");
	}

	@Test
	void audioMediaConvertFlowProducesSeparatedAudioFacts() throws Exception {
		var uploadId = upload("song.flac", "audio/flac", "audio-source");
		var completedJob = awaitJobCompletion(createMediaConvertJob(uploadId, """
			{
			  "targetExtension": "mp3",
			  "audioBitrateKbps": 192
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(3);

		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("media-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("targetExtension").asText()).isEqualTo("mp3");
		assertThat(manifest.path("previewKind").asText()).isEqualTo("media");
		assertThat(manifest.path("resultMediaType").asText()).isEqualTo("audio/mpeg");

		var sourceFacts = factIndex(manifest.path("sourceFacts"));
		assertThat(sourceFacts.get("Контейнер")).isEqualTo("FLAC");
		assertThat(sourceFacts.get("Audio codec")).isEqualTo("FLAC");
		assertThat(sourceFacts.get("Sample rate")).isEqualTo("48000 Hz");

		var resultFacts = factIndex(manifest.path("resultFacts"));
		assertThat(resultFacts.get("Контейнер")).isEqualTo("MP3");
		assertThat(resultFacts.get("Audio codec")).isEqualTo("MP3");
		assertThat(resultFacts.get("Sample rate")).isEqualTo("44100 Hz");
		assertThat(resultFacts.get("Audio bitrate")).isEqualTo("192 kbps");

		assertThat(manifest.path("warnings")).anyMatch(node -> node.asText().contains("Lossy audio export"));

		var binaryArtifact = artifacts.get("media-convert-binary");
		var binaryContent = this.mockMvc.perform(get(binaryArtifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(binaryArtifact.path("fileName").asText()).isEqualTo("song.mp3");
		assertThat(binaryContent).contains("-c:a libmp3lame");
		assertThat(binaryContent).contains("-b:a 192k");
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

	private String createMediaConvertJob(String uploadId, String parametersJson) throws Exception {
		var jobResponse = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "MEDIA_CONVERT",
						  "parameters": %s
						}
						""".formatted(uploadId, parametersJson))
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

		throw new AssertionError("Media conversion job did not finish within the expected polling window.");
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

	private Map<String, String> factIndex(JsonNode facts) {
		var result = new java.util.HashMap<String, String>();
		for (JsonNode fact : facts) {
			result.put(fact.path("label").asText(), fact.path("value").asText());
		}
		return Map.copyOf(result);
	}

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-media-convert-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for media conversion tests.", exception);
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
