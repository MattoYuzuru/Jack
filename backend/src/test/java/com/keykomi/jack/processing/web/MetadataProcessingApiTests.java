package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
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
class MetadataProcessingApiTests {

	private static final Path STORAGE_ROOT = createStorageRoot();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", () -> STORAGE_ROOT.toString());
		registry.add("jack.processing.max-upload-size-bytes", () -> 5_242_880L);
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
	void inspectImageMetadataBuildsStructuredPayload() throws Exception {
		var uploadResponse = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", createJpegWithExif()))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);

		var completedJob = awaitJobCompletion(
			createMetadataJob(uploadResponse.path("id").asText(), "inspect-image", null).path("id").asText()
		);

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(1);

		this.mockMvc.perform(get(completedJob.path("artifacts").get(0).path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.operation").value("inspect-image"))
			.andExpect(jsonPath("$.family").value("image"))
			.andExpect(jsonPath("$.imagePayload.editable.description").value("Studio frame"))
			.andExpect(jsonPath("$.imagePayload.editable.artist").value("Jack"))
			.andExpect(jsonPath("$.imagePayload.editable.capturedAt").value("2026-04-05T11:20"))
			.andExpect(jsonPath("$.imagePayload.summary[0].label").value("Тип файла"));
	}

	@Test
	void exportImageMetadataBuildsEmbeddedJpegArtifact() throws Exception {
		var uploadResponse = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", "plain.jpg", "image/jpeg", createPlainJpeg()))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);

		var metadataPayload = """
			{
			  "description": "Exported frame",
			  "artist": "Jack Backend",
			  "copyright": "Jack Studio",
			  "capturedAt": "2026-04-05T14:10"
			}
			""";
		var completedJob = awaitJobCompletion(
			createMetadataJob(uploadResponse.path("id").asText(), "export-image", metadataPayload).path("id").asText()
		);

		assertThat(completedJob.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completedJob.path("artifacts")).hasSize(2);

		var manifestArtifact = findArtifact(completedJob, "metadata-export-manifest");
		var binaryArtifact = findArtifact(completedJob, "metadata-export-binary");

		this.mockMvc.perform(get(manifestArtifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("embedded-jpeg"))
			.andExpect(jsonPath("$.fileName").value("plain-metadata.jpg"));

		var exportedBytes = this.mockMvc.perform(get(binaryArtifact.path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(exportedBytes));
		var ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
		var exifSubIfdDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

		assertThat(ifd0Directory).isNotNull();
		assertThat(ifd0Directory.getDescription(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)).isEqualTo("Exported frame");
		assertThat(ifd0Directory.getDescription(ExifIFD0Directory.TAG_ARTIST)).isEqualTo("Jack Backend");
		assertThat(ifd0Directory.getDescription(ExifIFD0Directory.TAG_COPYRIGHT)).isEqualTo("Jack Studio");
		assertThat(exifSubIfdDirectory.getDescription(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL))
			.isEqualTo("2026:04:05 14:10:00");
	}

	@Test
	void inspectAudioMetadataBuildsTechnicalPayload() throws Exception {
		var uploadResponse = parseJson(
			this.mockMvc.perform(
				multipart("/api/uploads")
					.file(new MockMultipartFile("file", "tone.wav", "audio/wav", createWaveFile()))
			)
				.andExpect(status().isCreated())
				.andReturn()
		);

		var completedJob = awaitJobCompletion(
			createMetadataJob(uploadResponse.path("id").asText(), "inspect-audio", null).path("id").asText()
		);

		this.mockMvc.perform(get(findArtifact(completedJob, "metadata-inspect-manifest").path("downloadPath").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.operation").value("inspect-audio"))
			.andExpect(jsonPath("$.family").value("audio"))
			.andExpect(jsonPath("$.audioPayload.technical.sampleRate").value(44100))
			.andExpect(jsonPath("$.audioPayload.technical.channelCount").value(1))
			.andExpect(jsonPath("$.audioPayload.summary[0].label").value("Bitrate"));
	}

	private JsonNode createMetadataJob(String uploadId, String operation, String metadataPayload) throws Exception {
		var requestBody = metadataPayload == null
			? """
				{
				  "uploadId": "%s",
				  "jobType": "METADATA_EXPORT",
				  "parameters": {
				    "operation": "%s"
				  }
				}
				""".formatted(uploadId, operation)
			: """
				{
				  "uploadId": "%s",
				  "jobType": "METADATA_EXPORT",
				  "parameters": {
				    "operation": "%s",
				    "metadata": %s
				  }
				}
				""".formatted(uploadId, operation, metadataPayload);

		return parseJson(
			this.mockMvc.perform(
				post("/api/jobs")
					.contentType(APPLICATION_JSON)
					.content(requestBody)
			)
				.andExpect(status().isAccepted())
				.andReturn()
		);
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

		throw new AssertionError("Processing job did not finish within the expected polling window.");
	}

	private JsonNode findArtifact(JsonNode job, String kind) {
		for (JsonNode artifact : job.path("artifacts")) {
			if (kind.equals(artifact.path("kind").asText())) {
				return artifact;
			}
		}
		throw new AssertionError("Artifact not found: " + kind);
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

	private static byte[] createJpegWithExif() throws Exception {
		var plainJpeg = createPlainJpeg();
		var imageMetadata = Imaging.getMetadata(plainJpeg);
		TiffOutputSet outputSet = null;

		if (imageMetadata instanceof JpegImageMetadata jpegImageMetadata && jpegImageMetadata.getExif() != null) {
			outputSet = jpegImageMetadata.getExif().getOutputSet();
		}

		if (outputSet == null) {
			outputSet = new TiffOutputSet();
		}

		var rootDirectory = outputSet.getOrCreateRootDirectory();
		var exifDirectory = outputSet.getOrCreateExifDirectory();
		rootDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
		rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, "Studio frame");
		rootDirectory.removeField(TiffTagConstants.TIFF_TAG_ARTIST);
		rootDirectory.add(TiffTagConstants.TIFF_TAG_ARTIST, "Jack");
		rootDirectory.removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT);
		rootDirectory.add(TiffTagConstants.TIFF_TAG_COPYRIGHT, "Jack Studio");
		exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
		exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, "2026:04:05 11:20:00");

		var output = new ByteArrayOutputStream();
		new ExifRewriter().updateExifMetadataLossy(plainJpeg, output, outputSet);
		return output.toByteArray();
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

	private static Path createStorageRoot() {
		try {
			return Files.createTempDirectory("jack-metadata-tests");
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to allocate temp processing storage for metadata tests.", exception);
		}
	}

}
