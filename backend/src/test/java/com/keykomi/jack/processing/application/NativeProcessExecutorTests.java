package com.keykomi.jack.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.ProcessingException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NativeProcessExecutorTests {

	@Test
	void enforcesTimeoutAndOutputBudget() throws Exception {
		var properties = new ProcessingProperties();
		properties.setMaxProcessOutputBytes(32);
		var executor = new NativeProcessExecutor(properties);
		var workingDirectory = Files.createTempDirectory("jack-native-test-");

		assertThatThrownBy(() -> executor.execute(
			List.of("/bin/sh", "-c", "sleep 2"),
			workingDirectory,
			Duration.ofMillis(50)
		)).isInstanceOfSatisfying(ProcessingException.class, exception ->
			assertThat(exception.code()).isEqualTo("PROCESSING_TIMEOUT"));

		assertThatThrownBy(() -> executor.execute(
			List.of("/bin/sh", "-c", "printf '%080d' 0"),
			workingDirectory,
			Duration.ofSeconds(2)
		)).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
			assertThat(exception.getStatusCode().value()).isEqualTo(413));
	}
}
