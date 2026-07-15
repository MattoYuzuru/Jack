package com.keykomi.jack.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class SecureXmlParserTests {

	@Test
	void parsesNamespaceAwareDocuments() throws Exception {
		var document = SecureXmlParser.parse("<office:document xmlns:office=\"urn:office\"><office:body/></office:document>");

		assertThat(document.getDocumentElement().getLocalName()).isEqualTo("document");
	}

	@Test
	void rejectsExternalFileEntityBeforeResolution() throws Exception {
		var secret = Files.createTempFile("jack-xxe-", ".txt");
		Files.writeString(secret, "must-not-be-read");
		var payload = """
			<!DOCTYPE document [<!ENTITY xxe SYSTEM "%s">]>
			<document>&xxe;</document>
			""".formatted(secret.toUri());

		try {
			assertThatThrownBy(() -> SecureXmlParser.parse(payload))
				.hasMessageContaining("DOCTYPE");
		}
		finally {
			Files.deleteIfExists(secret);
		}
	}

	@Test
	void rejectsExternalNetworkEntityBeforeResolution() {
		var payload = """
			<!DOCTYPE document [<!ENTITY xxe SYSTEM "http://127.0.0.1:9/xxe">]>
			<document>&xxe;</document>
			""";

		assertThatThrownBy(() -> SecureXmlParser.parse(payload))
			.hasMessageContaining("DOCTYPE");
	}
}
