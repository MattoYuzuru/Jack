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
class ImageProcessingApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path COMMAND_LOG = TEST_ROOT.resolve("commands.log");
	private static final Path FAKE_CONVERT = createExecutable(BIN_ROOT.resolve("fake-convert"), """
		#!/bin/bash
		set -euo pipefail

		log_file=%s
		printf 'convert %%s\\n' "$*" >> "$log_file"
		output="${!#}"
		mkdir -p "$(dirname "$output")"

		case "$output" in
		  *.png)
		    base64 -d <<'EOF' > "$output"
		iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==
		EOF
		    ;;
		  *.pgm)
		    cat <<'EOF' > "$output"
		P2
		1 1
		255
		0
		EOF
		    ;;
		  *.svg)
		    cat <<'EOF' > "$output"
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" fill="#111111"/></svg>
		EOF
		    ;;
		  *)
		    printf 'artifact for %%s\\n' "$(basename "$output")" > "$output"
		    ;;
		esac
		""".formatted(COMMAND_LOG.toString()));
	private static final Path FAKE_FFMPEG = createExecutable(BIN_ROOT.resolve("fake-ffmpeg"), """
		#!/bin/bash
		set -euo pipefail

		log_file=%s
		printf 'ffmpeg %%s\\n' "$*" >> "$log_file"
		output="${!#}"
		mkdir -p "$(dirname "$output")"

		case "$output" in
		  *.png)
		    base64 -d <<'EOF' > "$output"
		iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==
		EOF
		    ;;
		  *)
		    printf 'ffmpeg artifact for %%s\\n' "$(basename "$output")" > "$output"
		    ;;
		esac
		""".formatted(COMMAND_LOG.toString()));
	private static final Path FAKE_FFPROBE = createExecutable(BIN_ROOT.resolve("fake-ffprobe"), """
		#!/bin/bash
		set -euo pipefail

		echo '{"format":{"duration":"1.0"},"streams":[{}]}'
		""");
	private static final Path FAKE_POTRACE = createExecutable(BIN_ROOT.resolve("fake-potrace"), """
		#!/bin/bash
		set -euo pipefail

		log_file=%s
		printf 'potrace %%s\\n' "$*" >> "$log_file"
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
		""".formatted(COMMAND_LOG.toString()));
	private static final Path FAKE_RAW_PREVIEW = createExecutable(BIN_ROOT.resolve("fake-dcraw-emu"), """
		#!/bin/bash
		set -euo pipefail

		log_file=%s
		printf 'dcraw %%s\\n' "$*" >> "$log_file"
		printf 'raw-preview-jpeg'
		""".formatted(COMMAND_LOG.toString()));

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
	void imagePreviewFlowProducesManifestAndBinaryArtifact() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);

		var uploadId = upload("capture.heic", "image/heic", "heic-source");
		var completedJob = awaitJobCompletion(createImageJob(uploadId, """
			{
			  "operation": "preview"
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		assertThat(artifacts).containsKeys("image-preview-manifest", "image-preview-binary");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("image-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("operation").asText()).isEqualTo("preview");
		assertThat(manifest.path("sourceAdapterLabel").asText()).isEqualTo("HEIC server rasterization");
		assertThat(manifest.path("targetAdapterLabel").asText()).isEqualTo("Viewer preview PNG");
		assertThat(manifest.path("previewMediaType").asText()).isEqualTo("image/png");
		assertThat(manifest.path("outputExtension").asText()).isEqualTo("png");

		var previewArtifact = artifacts.get("image-preview-binary");
		assertThat(previewArtifact.path("fileName").asText()).isEqualTo("capture.preview.png");
		assertThat(previewArtifact.path("mediaType").asText()).isEqualTo("image/png");
		assertThat(Files.readString(COMMAND_LOG, StandardCharsets.UTF_8)).contains("convert ");
	}

	@Test
	void rawPreviewFlowUsesRawPreviewExecutable() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);

		var uploadId = upload("session.nef", "application/octet-stream", "raw-source");
		var completedJob = awaitJobCompletion(createImageJob(uploadId, """
			{
			  "operation": "preview"
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("image-preview-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("sourceAdapterLabel").asText()).isEqualTo("LibRaw preview extraction");
		assertThat(Files.readString(COMMAND_LOG, StandardCharsets.UTF_8)).contains("dcraw ");
	}

	@Test
	void imageConvertFlowProducesPreviewSafeArtifactsForAvif() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);

		var uploadId = upload("poster.png", "image/png", "png-source");
		var completedJob = awaitJobCompletion(createImageJob(uploadId, """
			{
			  "operation": "convert",
			  "targetExtension": "avif",
			  "quality": 0.78,
			  "presetLabel": "Web Balanced"
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		assertThat(artifacts).containsKeys(
			"image-convert-manifest",
			"image-convert-binary",
			"image-convert-preview"
		);

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("image-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("operation").asText()).isEqualTo("convert");
		assertThat(manifest.path("resultMediaType").asText()).isEqualTo("image/avif");
		assertThat(manifest.path("previewMediaType").asText()).isEqualTo("image/png");
		assertThat(manifest.path("targetAdapterLabel").asText()).isEqualTo("FFmpeg AVIF encode");

		assertThat(artifacts.get("image-convert-binary").path("fileName").asText()).isEqualTo("poster.avif");
		assertThat(artifacts.get("image-convert-preview").path("fileName").asText()).isEqualTo("poster.preview.png");
		assertThat(Files.readString(COMMAND_LOG, StandardCharsets.UTF_8)).contains("ffmpeg ");
	}

	@Test
	void illustrationConvertFlowUsesPotraceForSvgTarget() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);

		var uploadId = upload("diagram.ai", "application/octet-stream", "illustration-source");
		var completedJob = awaitJobCompletion(createImageJob(uploadId, """
			{
			  "operation": "convert",
			  "targetExtension": "svg"
			}
			"""));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("image-convert-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("sourceAdapterLabel").asText()).isEqualTo("Illustration rasterization");
		assertThat(manifest.path("targetAdapterLabel").asText()).isEqualTo("Potrace SVG trace");
		assertThat(artifacts.get("image-convert-binary").path("mediaType").asText()).isEqualTo("image/svg+xml");
		assertThat(Files.readString(COMMAND_LOG, StandardCharsets.UTF_8)).contains("potrace ");
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

	private String createImageJob(String uploadId, String parametersJson) throws Exception {
		var jobResponse = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "IMAGE_CONVERT",
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

		throw new AssertionError("Image processing job did not finish within the expected polling window.");
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
			return Files.createTempDirectory("jack-image-processing-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for image processing tests.", exception);
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
