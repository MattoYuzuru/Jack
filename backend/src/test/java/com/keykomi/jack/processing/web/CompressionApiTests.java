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
class CompressionApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path FAKE_CONVERT = createExecutable(BIN_ROOT.resolve("fake-convert"), """
		#!/bin/bash
		set -euo pipefail

		output="${!#}"
		mkdir -p "$(dirname "$output")"

		case "$output" in
		  *.png)
		    base64 -d <<'EOF' > "$output"
		iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==
		EOF
		    ;;
		  *.webp)
		    printf 'webp-compact' > "$output"
		    ;;
		  *.jpg|*.jpeg)
		    printf 'jpg-compact-result' > "$output"
		    ;;
		  *.tiff)
		    printf 'tiff-result' > "$output"
		    ;;
		  *)
		    printf 'convert-result-%s' "$(basename "$output")" > "$output"
		    ;;
		esac
		""");
	private static final Path FAKE_FFMPEG = createExecutable(BIN_ROOT.resolve("fake-ffmpeg"), """
		#!/bin/bash
		set -euo pipefail

		output="${!#}"
		mkdir -p "$(dirname "$output")"
		args="$*"

		case "$output" in
		  *.png)
		    base64 -d <<'EOF' > "$output"
		iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==
		EOF
		    ;;
		  *.m4a)
		    if [[ "$args" == *"-b:a 64k"* ]]; then
		      printf 'm4a64' > "$output"
		    else
		      printf 'm4a96-longer' > "$output"
		    fi
		    ;;
		  *.mp3)
		    if [[ "$args" == *"-b:a 64k"* ]]; then
		      printf 'mp364-longer' > "$output"
		    else
		      printf 'mp396-preview' > "$output"
		    fi
		    ;;
		  *.avif)
		    printf 'avif-compact' > "$output"
		    ;;
		  *)
		    printf 'ffmpeg-result-%s' "$(basename "$output")" > "$output"
		    ;;
		esac
		""");
	private static final Path FAKE_FFPROBE = createExecutable(BIN_ROOT.resolve("fake-ffprobe"), """
		#!/bin/bash
		set -euo pipefail

		args="$*"
		if [[ "$args" == *"song.flac"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"flac","duration":"8.0","bit_rate":"920000"},"streams":[{"codec_type":"audio","codec_name":"flac","sample_rate":"48000","channels":2,"bit_rate":"920000"}]}
		JSON
		  exit 0
		fi

		if [[ "$args" == *".m4a"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"8.0","bit_rate":"64000"},"streams":[{"codec_type":"audio","codec_name":"aac","sample_rate":"44100","channels":2,"bit_rate":"64000"}]}
		JSON
		  exit 0
		fi

		if [[ "$args" == *".mp3"* ]]; then
		  cat <<'JSON'
		{"format":{"format_name":"mp3","duration":"8.0","bit_rate":"96000"},"streams":[{"codec_type":"audio","codec_name":"mp3","sample_rate":"44100","channels":2,"bit_rate":"96000"}]}
		JSON
		  exit 0
		fi

		echo '{"format":{"duration":"1.0"},"streams":[{}]}'
		""");
	private static final Path FAKE_POTRACE = createExecutable(BIN_ROOT.resolve("fake-potrace"), """
		#!/bin/bash
		set -euo pipefail

		output=""
		while [[ $# -gt 0 ]]; do
		  if [[ "$1" == "-o" ]]; then
		    output="$2"
		    shift 2
		    continue
		  fi
		  shift
		done

		mkdir -p "$(dirname "$output")"
		cat <<'EOF' > "$output"
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><path d="M0 0H1V1H0Z" fill="#101820"/></svg>
		EOF
		""");
	private static final Path FAKE_RAW_PREVIEW = createExecutable(BIN_ROOT.resolve("fake-dcraw-emu"), """
		#!/bin/bash
		set -euo pipefail

		printf 'raw-preview-jpeg'
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
		registry.add("jack.processing.image-convert-executable", () -> FAKE_CONVERT.toString());
		registry.add("jack.processing.potrace-executable", () -> FAKE_POTRACE.toString());
		registry.add("jack.processing.raw-preview-executable", () -> FAKE_RAW_PREVIEW.toString());
		registry.add("jack.processing.image-processing-timeout-seconds", () -> 5L);
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
	void imageTargetSizeCompressionProducesManifestAndArtifacts() throws Exception {
		var uploadId = upload("photo.png", "image/png", "png-source");
		var completedJob = awaitJobCompletion(createCompressionJob(uploadId, """
			{
			  "mode": "target-size",
			  "targetSizeBytes": 4096
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(3);

		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("compression-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("mode").asText()).isEqualTo("TARGET_SIZE");
		assertThat(manifest.path("targetMet").asBoolean()).isTrue();
		assertThat(manifest.path("targetExtension").asText()).isEqualTo("webp");
		assertThat(manifest.path("attempts")).hasSize(1);
		assertThat(artifacts.get("compression-binary").path("fileName").asText()).isEqualTo("photo.webp");
		assertThat(artifacts.get("compression-preview").path("mediaType").asText()).isEqualTo("image/webp");
	}

	@Test
	void audioMaximumCompressionSelectsSmallestCandidate() throws Exception {
		var uploadId = upload("song.flac", "audio/flac", "audio-source");
		var completedJob = awaitJobCompletion(createCompressionJob(uploadId, """
			{
			  "mode": "maximum"
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("compression-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);

		assertThat(manifest.path("mode").asText()).isEqualTo("MAX_REDUCTION");
		assertThat(manifest.path("targetExtension").asText()).isEqualTo("m4a");
		assertThat(manifest.path("attempts").size()).isGreaterThanOrEqualTo(3);
		assertThat(manifest.path("attempts").get(1).path("audioBitrateKbps").asInt()).isEqualTo(64);
		assertThat(artifacts.get("compression-binary").path("fileName").asText()).isEqualTo("song.m4a");
		assertThat(artifacts.get("compression-preview").path("fileName").asText()).isEqualTo("song.preview.mp3");

		var binaryContent = this.mockMvc.perform(get(artifacts.get("compression-binary").path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(binaryContent).isEqualTo("m4a64");
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

	private String createCompressionJob(String uploadId, String parametersJson) throws Exception {
		var jobResponse = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "FILE_COMPRESS",
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
		for (int attempt = 0; attempt < 60; attempt += 1) {
			var response = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var status = response.path("status").asText();

			if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
				return response;
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Compression job did not finish within the expected polling window.");
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
			return Files.createTempDirectory("jack-compression-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for compression tests.", exception);
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
