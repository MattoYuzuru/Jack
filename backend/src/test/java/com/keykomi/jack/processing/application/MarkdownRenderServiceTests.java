package com.keykomi.jack.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.web.server.ResponseStatusException;

class MarkdownRenderServiceTests {

	private final MarkdownRenderService service = new MarkdownRenderService();

	@Test
	void coversRequiredCommonMarkBlocksAndInlineSyntax() {
		var source = """
			Setext heading
			--------------

			Paragraph with **strong**, *emphasis*, `code`, [link](https://example.com) and &amp;.

			> quote
			> > nested

			1. ordered
			   - mixed child

			---

			```java
			System.out.println("safe");
			```

			    indented code
			""";

		var contract = this.service.render(source, "commonmark-gfm");

		assertThat(contract.sanitizedHtml())
			.contains("<h2", "<strong>", "<em>", "<code>", "<blockquote>", "<ol>", "<ul>", "<hr", "<pre>")
			.contains("href=\"https://example.com\"");
		assertThat(contract.outline()).extracting("label").containsExactly("Setext heading");
	}

	@Test
	void rendersAccessibleAlignedGfmTableInsideScrollablePreview() {
		var source = """
			| Функция | Статус | Счётчик |
			| :--- | :---: | ---: |
			| GFM table | Готово | 42 |
			""";

		var contract = this.service.render(source, "commonmark-gfm");
		var fragment = Jsoup.parseBodyFragment(contract.sanitizedHtml());
		var scrollRegion = fragment.selectFirst(".markdown-table-scroll");

		assertThat(scrollRegion).isNotNull();
		assertThat(scrollRegion.attr("role")).isEqualTo("region");
		assertThat(scrollRegion.attr("tabindex")).isEqualTo("0");
		assertThat(fragment.select("thead th[scope=col]")).hasSize(3);
		assertThat(fragment.select("th.markdown-align-left")).hasSize(1);
		assertThat(fragment.select("th.markdown-align-center")).hasSize(1);
		assertThat(fragment.select("th.markdown-align-right")).hasSize(1);
		assertThat(contract.detectedFeatures()).contains("gfm-table");
		assertThat(contract.previewDocument())
			.contains("Content-Security-Policy", ".markdown-table-scroll", "overflow-x:auto")
			.doesNotContain("javascript:");
	}

	@Test
	void enablesSafeOptionalExtensionsOnlyWhenRequested() {
		var source = """
			==highlight== H~2~O x^2^

			Term
			: Definition

			Footnote[^1]

			[^1]: Note
			""";

		var plain = this.service.render(source, "commonmark-gfm");
		var extended = this.service.render(
			source,
			"commonmark-gfm",
			Set.of("highlight", "sub-sup", "definition-lists", "footnotes")
		);

		assertThat(plain.sanitizedHtml()).contains("==highlight==");
		assertThat(extended.sanitizedHtml()).contains("<mark>highlight</mark>", "<sub>2</sub>", "<sup>2</sup>", "<dl>");
		assertThat(extended.enabledExtensions()).containsExactlyInAnyOrder("highlight", "sub-sup", "definition-lists", "footnotes");
	}

	@Test
	void rejectsUnknownOptionalExtension() {
		assertThatThrownBy(() -> this.service.render("text", "commonmark-gfm", Set.of("user-javascript")))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Неизвестное Markdown extension");
	}
}
