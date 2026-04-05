package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class ProcessingJobCancellationApiTests {

	private static final Path TEST_ROOT = createTestRoot();
	private static final Path STORAGE_ROOT = createDirectory(TEST_ROOT.resolve("storage"));
	private static final Path BIN_ROOT = createDirectory(TEST_ROOT.resolve("bin"));
	private static final Path COMMAND_LOG = TEST_ROOT.resolve("commands.log");
	private static final Path SLOW_CONVERT = createExecutable(BIN_ROOT.resolve("slow-convert"), """
		#!/bin/bash
		set -euo pipefail

		log_file=%s
		trap 'printf "terminated\\n" >> "$log_file"; exit 130' TERM INT
		printf 'convert %%s\\n' "$*" >> "$log_file"
		sleep 10

		output="${!#}"
		mkdir -p "$(dirname "$output")"

		case "$output" in
		  *.png)
		    base64 -d <<'EOF' > "$output"
		iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==
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

		output="${!#}"
		mkdir -p "$(dirname "$output")"
		printf 'ffmpeg artifact for %%s\\n' "$(basename "$output")" > "$output"
		""");
	private static final Path FAKE_FFPROBE = createExecutable(BIN_ROOT.resolve("fake-ffprobe"), """
		#!/bin/bash
		set -euo pipefail

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
		registry.add("jack.processing.image-convert-executable", () -> SLOW_CONVERT.toString());
		registry.add("jack.processing.potrace-executable", () -> FAKE_POTRACE.toString());
		registry.add("jack.processing.raw-preview-executable", () -> FAKE_RAW_PREVIEW.toString());
		registry.add("jack.processing.image-processing-timeout-seconds", () -> 30L);
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
	void imageConvertJobCanBeCancelled() throws Exception {
		Files.writeString(COMMAND_LOG, "", StandardCharsets.UTF_8);

		var uploadId = upload("poster.png", "image/png", "png-source");
		var jobId = createImageJob(uploadId, """
			{
			  "operation": "convert",
			  "targetExtension": "jpg"
			}
			""");

		awaitCommandStart(jobId);

		var cancelledJob = parseJson(
			this.mockMvc.perform(delete("/api/jobs/{jobId}", jobId))
				.andExpect(status().isOk())
				.andReturn()
		);
		assertThat(cancelledJob.path("status").asText()).isEqualTo("CANCELLED");

		var terminalJob = awaitTerminalJob(jobId);
		assertThat(terminalJob.path("status").asText()).isEqualTo("CANCELLED");
		assertThat(terminalJob.path("artifacts")).isEmpty();
		assertThat(terminalJob.path("message").asText()).contains("отмен");
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

	private void awaitCommandStart(String jobId) throws Exception {
		for (int attempt = 0; attempt < 60; attempt += 1) {
			var job = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var log = Files.exists(COMMAND_LOG) ? Files.readString(COMMAND_LOG, StandardCharsets.UTF_8) : "";

			if (log.contains("convert ")) {
				return;
			}

			var status = job.path("status").asText();
			if ("FAILED".equals(status) || "COMPLETED".equals(status) || "CANCELLED".equals(status)) {
				throw new AssertionError("Job завершился до того, как внешний processor успел стартовать: " + status);
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Внешний IMAGE_CONVERT processor не стартовал в ожидаемое время.");
	}

	private JsonNode awaitTerminalJob(String jobId) throws Exception {
		for (int attempt = 0; attempt < 80; attempt += 1) {
			var response = parseJson(this.mockMvc.perform(get("/api/jobs/{jobId}", jobId)).andReturn());
			var status = response.path("status").asText();

			if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
				return response;
			}

			Thread.sleep(50L);
		}

		throw new AssertionError("Processing job не завершился в ожидаемое время.");
	}

	private JsonNode parseJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static Path createTestRoot() {
		try {
			return Files.createTempDirectory("jack-processing-cancel-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp root for cancellation tests.", exception);
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
