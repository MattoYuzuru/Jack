package com.keykomi.jack.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.ProcessingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class FileIntakeServiceTests {

	private final ProcessingProperties properties = new ProcessingProperties();
	private final ProcessingResourceBudgetService budgets = new ProcessingResourceBudgetService(this.properties);
	private final FileIntakeService intake = new FileIntakeService(this.budgets, new ActiveContentPolicyService());

	@Test
	void rejectsMagicExtensionMismatchAndUnsafeFilename() throws Exception {
		var pdf = Files.createTempFile("jack-intake-", ".bin");
		Files.writeString(pdf, "%PDF-1.7\n", StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> this.intake.inspect(pdf, "photo.jpg", "image/jpeg"))
			.isInstanceOfSatisfying(ProcessingException.class, exception ->
				assertThat(exception.code()).isEqualTo("FILE_TYPE_MISMATCH"));
		assertThatThrownBy(() -> this.intake.inspect(pdf, "../report.pdf", "application/pdf"))
			.isInstanceOfSatisfying(ProcessingException.class, exception ->
				assertThat(exception.code()).isEqualTo("UNSAFE_FILE_NAME"));
	}

	@Test
	void classifiesAllowlistedWorkbookPackage() throws Exception {
		var workbook = zip("xl/workbook.xml", "<workbook/>");

		var result = this.intake.inspect(
			workbook,
			"book.xlsx",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
		);

		assertThat(result.parserRoute()).isEqualTo("workbook");
		assertThat(result.mediaType()).contains("spreadsheetml");
	}

	@Test
	void rejectsArchiveTraversalAndExpansionBombBeforeParserRoute() throws Exception {
		var traversal = zip("../outside.txt", "payload");
		assertThatThrownBy(() -> this.intake.inspect(traversal, "unsafe.zip", "application/zip"))
			.isInstanceOfSatisfying(ProcessingException.class, exception ->
				assertThat(exception.code()).isEqualTo("CORRUPT_FILE"));

		var bomb = zip("payload.txt", "A".repeat(1_000_000));
		assertThatThrownBy(() -> this.intake.inspect(bomb, "bomb.zip", "application/zip"))
			.isInstanceOfSatisfying(ProcessingException.class, exception ->
				assertThat(exception.code()).isEqualTo("RESOURCE_LIMIT_EXCEEDED"));
	}

	@Test
	void rejectsActiveSvgAndExternalResourcesButAcceptsInertVector() throws Exception {
		var activeSvg = Files.createTempFile("jack-intake-active-", ".svg");
		Files.writeString(activeSvg, "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>");
		assertThatThrownBy(() -> this.intake.inspect(activeSvg, "active.svg", "image/svg+xml"))
			.isInstanceOfSatisfying(ProcessingException.class, exception ->
				assertThat(exception.code()).isEqualTo("UNSAFE_ACTIVE_CONTENT"));

		var externalSvg = Files.createTempFile("jack-intake-external-", ".svg");
		Files.writeString(externalSvg, "<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"https://example.test/pixel.png\"/></svg>");
		assertThatThrownBy(() -> this.intake.inspect(externalSvg, "external.svg", "image/svg+xml"))
			.isInstanceOfSatisfying(ProcessingException.class, exception ->
				assertThat(exception.code()).isEqualTo("UNSAFE_ACTIVE_CONTENT"));

		var inertSvg = Files.createTempFile("jack-intake-inert-", ".svg");
		Files.writeString(inertSvg, "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\" fill=\"#173436\"/></svg>");
		assertThat(this.intake.inspect(inertSvg, "inert.svg", "image/svg+xml").parserRoute()).isEqualTo("svg");
	}

	private Path zip(String entryName, String content) throws Exception {
		var path = Files.createTempFile("jack-intake-", ".zip");
		try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
			zip.putNextEntry(new ZipEntry(entryName));
			zip.write(content.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return path;
	}
}
