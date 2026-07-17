package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DelimitedTableRangeApiTests {

	private static final Path STORAGE_ROOT = createStorageRoot();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jack.processing.storage-root", STORAGE_ROOT::toString);
	}

	@Test
	void preservesMultilineBlankAndRaggedRowsAcrossStableCursor() throws Exception {
		var csv = """
			name,note
			alpha,"line one
			line two"

			beta,=2+2,extra
			gamma,last
			""";
		var uploadResponse = this.mockMvc.perform(
			multipart("/api/uploads").file(new MockMultipartFile("file", "data.csv", "text/csv", csv.getBytes()))
		).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		var uploadId = this.objectMapper.readTree(uploadResponse).path("id").asText();

		var firstResponse = this.mockMvc.perform(
			get("/api/uploads/{uploadId}/table-range", uploadId)
				.param("headerMode", "PRESENT")
				.param("limit", "2")
		).andExpect(status().isOk())
			.andExpect(jsonPath("$.table.rows[0][1]").value("line one\nline two"))
			.andExpect(jsonPath("$.table.rows[1][0]").value(""))
			.andExpect(jsonPath("$.table.nextCursor").isNotEmpty())
			.andExpect(jsonPath("$.warnings[0]").exists())
			.andReturn().getResponse().getContentAsString();

		var first = this.objectMapper.readTree(firstResponse);
		var cursor = first.path("table").path("nextCursor").asText();
		var revision = first.path("table").path("revision").asText();
		var second = this.mockMvc.perform(
			get("/api/uploads/{uploadId}/table-range", uploadId)
				.param("headerMode", "PRESENT")
				.param("limit", "2")
				.param("cursor", cursor)
		).andExpect(status().isOk())
			.andExpect(jsonPath("$.table.rows[0][0]").value("beta"))
			.andReturn().getResponse().getContentAsString();
		assertThat(this.objectMapper.readTree(second).path("table").path("revision").asText()).isEqualTo(revision);

		this.mockMvc.perform(
			get("/api/uploads/{uploadId}/table-range", uploadId).param("cursor", "invalid")
		).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
	}

	private static Path createStorageRoot() {
		try {
			return Files.createTempDirectory("jack-table-range-tests-");
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
