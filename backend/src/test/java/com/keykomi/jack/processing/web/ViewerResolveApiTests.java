package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
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
class ViewerResolveApiTests {

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
		    printf 'artifact for %s\n' "$(basename "$output")" > "$output"
		    ;;
		esac
		""");
	private static final Path FAKE_FFMPEG = createExecutable(BIN_ROOT.resolve("fake-ffmpeg"), """
		#!/bin/bash
		set -euo pipefail

		output="${!#}"
		if [[ "$output" == "-" ]]; then
		  printf '\\x00\\x00\\x00\\x08\\x00\\x20\\x00\\x40\\x00\\x20\\x00\\x08\\x00\\x00'
		  exit 0
		fi

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

		if [[ "$args" == *"tone.wav"* ]]; then
		  cat <<'JSON'
		{"format":{"duration":"1.0"},"streams":[{"codec_name":"pcm_s16le","sample_rate":"44100","channels":1}]}
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
		registry.add("jack.processing.max-upload-size-bytes", () -> 8_388_608L);
		registry.add("jack.processing.ffmpeg-executable", () -> FAKE_FFMPEG.toString());
		registry.add("jack.processing.ffprobe-executable", () -> FAKE_FFPROBE.toString());
		registry.add("jack.processing.image-convert-executable", () -> FAKE_CONVERT.toString());
		registry.add("jack.processing.potrace-executable", () -> FAKE_POTRACE.toString());
		registry.add("jack.processing.raw-preview-executable", () -> FAKE_RAW_PREVIEW.toString());
		registry.add("jack.processing.media-preview-timeout-seconds", () -> 5L);
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
	void imageViewerResolveBuildsUnifiedManifest() throws Exception {
		var uploadId = upload("camera.jpg", "image/jpeg", createPlainJpeg());
		var completedJob = awaitJobCompletion(createViewerResolveJob(uploadId));

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");

		var artifacts = artifactIndex(completedJob);
		assertThat(artifacts).containsKeys(
			"viewer-resolve-manifest",
			"image-preview-manifest",
			"image-preview-binary",
			"metadata-inspect-manifest"
		);

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("viewer-resolve-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("kind").asText()).isEqualTo("image");
		assertThat(manifest.path("binaryArtifact").path("mediaType").asText()).isEqualTo("image/png");
		assertThat(manifest.path("imagePayload").path("metadata").path("summary")).isNotEmpty();
		assertThat(manifest.path("imagePayload").path("warnings").isArray()).isTrue();
	}

	@Test
	void documentViewerResolveBuildsUnifiedManifestWithPdfArtifact() throws Exception {
		var uploadId = upload("viewer.pdf", "application/pdf", createPdfBytes("Viewer resolve pdf"));
		var completedJob = awaitJobCompletion(createViewerResolveJob(uploadId));
		var artifacts = artifactIndex(completedJob);

		assertThat(artifacts).containsKeys("viewer-resolve-manifest", "document-preview-manifest", "document-preview-binary");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("viewer-resolve-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("kind").asText()).isEqualTo("document");
		assertThat(manifest.path("documentPayload").path("layout").path("mode").asText()).isEqualTo("pdf");
		assertThat(manifest.path("documentPayload").path("searchableText").asText()).contains("Viewer resolve pdf");
		assertThat(manifest.path("binaryArtifact").path("mediaType").asText()).isEqualTo("application/pdf");
	}

	@Test
	void documentViewerResolveKeepsEditableDraftForMarkdown() throws Exception {
		var uploadId = upload(
			"notes.md",
			"text/markdown",
			"""
			# Team notes

			## Follow-up
			- Share recap
			""".getBytes(StandardCharsets.UTF_8)
		);
		var completedJob = awaitJobCompletion(createViewerResolveJob(uploadId));
		var artifacts = artifactIndex(completedJob);

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("viewer-resolve-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("kind").asText()).isEqualTo("document");
		assertThat(manifest.path("documentPayload").path("layout").path("mode").asText()).isEqualTo("html");
		assertThat(manifest.path("documentPayload").path("layout").path("editableDraft").path("editorFormatId").asText()).isEqualTo("markdown");
		assertThat(manifest.path("documentPayload").path("layout").path("outline").get(0).path("label").asText()).isEqualTo("Team notes");
	}

	@Test
	void videoViewerResolveBuildsUnifiedManifest() throws Exception {
		var uploadId = upload("legacy.avi", "video/x-msvideo", "video-source".getBytes(StandardCharsets.UTF_8));
		var completedJob = awaitJobCompletion(createViewerResolveJob(uploadId));
		var artifacts = artifactIndex(completedJob);

		assertThat(artifacts).containsKeys("viewer-resolve-manifest", "media-preview-manifest", "media-preview-binary");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("viewer-resolve-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("kind").asText()).isEqualTo("video");
		assertThat(manifest.path("videoPayload").path("layout").path("width").asInt()).isEqualTo(640);
		assertThat(manifest.path("videoPayload").path("layout").path("metadata").path("mimeType").asText()).isEqualTo("video/mp4");
		assertThat(manifest.path("videoPayload").path("summary").get(6).path("value").asText()).contains("VIEWER_RESOLVE");
	}

	@Test
	void audioViewerResolveBuildsUnifiedManifestWithWaveform() throws Exception {
		var uploadId = upload("tone.wav", "audio/wav", createWaveFile());
		var completedJob = awaitJobCompletion(createViewerResolveJob(uploadId));
		var artifacts = artifactIndex(completedJob);

		assertThat(artifacts).containsKeys("viewer-resolve-manifest", "media-preview-manifest", "media-preview-binary", "metadata-inspect-manifest");

		var manifest = parseJson(
			this.mockMvc.perform(get(artifacts.get("viewer-resolve-manifest").path("downloadPath").asText()))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(manifest.path("kind").asText()).isEqualTo("audio");
		assertThat(manifest.path("audioPayload").path("layout").path("metadata").path("sampleRate").asInt()).isEqualTo(44_100);
		assertThat(manifest.path("audioPayload").path("layout").path("waveform")).isNotEmpty();
		assertThat(manifest.path("binaryArtifact").path("mediaType").asText()).isEqualTo("audio/mpeg");
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

	private String createViewerResolveJob(String uploadId) throws Exception {
		var response = parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content("""
						{
						  "uploadId": "%s",
						  "jobType": "VIEWER_RESOLVE"
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

		throw new AssertionError("VIEWER_RESOLVE job did not finish within the expected polling window.");
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

	private static byte[] createPlainJpeg() throws IOException {
		var image = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
		var output = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", output);
		return output.toByteArray();
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

	private static byte[] createWaveFile() throws IOException {
		var format = new AudioFormat(44_100, 16, 1, true, false);
		var pcm = new byte[44_100 * 2];
		try (var inputStream = new AudioInputStream(new ByteArrayInputStream(pcm), format, 44_100L);
			var output = new ByteArrayOutputStream()) {
			javax.sound.sampled.AudioSystem.write(inputStream, AudioFileFormat.Type.WAVE, output);
			return output.toByteArray();
		}
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-viewer-resolve-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for viewer resolve tests.", exception);
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
