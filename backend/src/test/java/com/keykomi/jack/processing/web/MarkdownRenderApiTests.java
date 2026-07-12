package com.keykomi.jack.processing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MarkdownRenderApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void rendersCommonMarkAndGfmWithVersionedContract() throws Exception {
		var markdown = """
			Title
			=====

			- [x] task
			  1. nested

			| name | value |
			| --- | ---: |
			| Jack | 1 |

			~~done~~ <https://example.com>
			""";

		this.mockMvc.perform(
			post("/api/markdown/render")
				.contentType(APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsBytes(Map.of("source", markdown, "profile", "commonmark-gfm")))
		)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileVersion").value("jack-markdown-1.0.0"))
			.andExpect(jsonPath("$.profile").value("commonmark-gfm"))
			.andExpect(jsonPath("$.sanitizedHtml").value(org.hamcrest.Matchers.containsString("<table")))
			.andExpect(jsonPath("$.sanitizedHtml").value(org.hamcrest.Matchers.containsString("<del>done</del>")))
			.andExpect(jsonPath("$.outline[0].label").value("Title"));
	}

	@Test
	void blocksScriptNavigationAndExternalImages() throws Exception {
		var markdown = """
			[bad](javascript:alert(1))
			<img src=x onerror=alert(1)>
			![tracking](https://example.com/pixel.png)
			""";

		var response = this.mockMvc.perform(
			post("/api/markdown/render")
				.contentType(APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsBytes(Map.of("source", markdown)))
		)
			.andExpect(status().isOk())
			.andReturn();
		var payload = this.objectMapper.readTree(response.getResponse().getContentAsByteArray());
		var html = payload.path("sanitizedHtml").asText();

		assertThat(html).doesNotContainIgnoringCase("javascript:");
		assertThat(Jsoup.parseBodyFragment(html).select("script,img,[onerror],[onclick]")).isEmpty();
		assertThat(html).contains("Изображение заблокировано");
	}

	@Test
	void returnsExplicitObsidianFallbacksWithoutVaultContext() throws Exception {
		this.mockMvc.perform(
			post("/api/markdown/render")
				.contentType(APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsBytes(Map.of(
					"source", "---\ntags: [jack]\n---\n> [!NOTE] Важно\n[[Page|Страница]] и ![[image.png]]",
					"profile", "obsidian-safe"
				)))
		)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.detectedFeatures").isArray())
			.andExpect(jsonPath("$.unresolvedReferences.length()").value(2))
			.andExpect(jsonPath("$.unresolvedReferences[0].kind").value("wikilink"))
			.andExpect(jsonPath("$.unresolvedReferences[1].kind").value("embed"))
			.andExpect(jsonPath("$.sanitizedHtml").value(org.hamcrest.Matchers.containsString("Ссылка не разрешена")));
	}
}
