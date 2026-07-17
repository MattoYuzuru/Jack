package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.keykomi.jack.processing.domain.ProcessingException;

@Service
public class NativeProcessExecutor {

	private final ProcessingProperties properties;

	public NativeProcessExecutor(ProcessingProperties properties) {
		this.properties = properties;
	}

	public NativeProcessResult execute(List<String> command, Path workingDirectory, Duration timeout) {
		if (command == null || command.isEmpty()) {
			throw new IllegalArgumentException("Native command не может быть пустой.");
		}

		Process process = null;
		try {
			process = new ProcessBuilder(List.copyOf(command))
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true)
				.start();
			var runningProcess = process;
			var output = new ByteArrayOutputStream();
			var outputFailure = new AtomicReference<RuntimeException>();
			var reader = Thread.ofVirtual().name("jack-native-output").start(() -> {
				try (var input = runningProcess.getInputStream()) {
					var buffer = new byte[8_192];
					long total = 0;
					int read;
					while ((read = input.read(buffer)) != -1) {
						total += read;
						if (total > this.properties.getMaxProcessOutputBytes()) {
							outputFailure.set(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Native process output превысил допустимый budget."));
							killProcessTree(runningProcess);
							return;
						}
						output.write(buffer, 0, read);
					}
				}
				catch (IOException exception) {
					outputFailure.set(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать bounded output native processor.", exception));
				}
			});

			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				killProcessTree(process);
				throw new ProcessingException(HttpStatus.REQUEST_TIMEOUT, "PROCESSING_TIMEOUT", "Native processor превысил допустимый timeout.");
			}
			reader.join(Math.min(5_000L, Math.max(1_000L, timeout.toMillis())));
			if (reader.isAlive()) {
				killProcessTree(process);
				throw new ProcessingException(HttpStatus.REQUEST_TIMEOUT, "PROCESSING_TIMEOUT", "Native processor не закрыл output stream вовремя.");
			}
			if (outputFailure.get() != null) {
				throw outputFailure.get();
			}
			if (process.exitValue() != 0) {
				throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Native processor завершился с ненулевым exit code."
				);
			}
			return new NativeProcessResult(process.exitValue(), output.toByteArray());
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native processor недоступен.", exception);
		}
		catch (InterruptedException exception) {
			if (process != null) {
				killProcessTree(process);
			}
			Thread.currentThread().interrupt();
			throw new NativeProcessCancelledException(exception);
		}
		finally {
			if (process != null && process.isAlive()) {
				killProcessTree(process);
			}
		}
	}

	private void killProcessTree(Process process) {
		// Дочерние ffmpeg/convert процессы завершаются раньше родителя, иначе они
		// могут продолжить писать во временный каталог после timeout/cancel.
		process.toHandle().descendants()
			.sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
			.forEach(handle -> {
				handle.destroy();
				if (handle.isAlive()) {
					handle.destroyForcibly();
				}
			});
		process.destroy();
		if (process.isAlive()) {
			process.destroyForcibly();
		}
	}

	public record NativeProcessResult(int exitCode, byte[] output) {
		public String utf8Output() {
			return new String(this.output, java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	public static final class NativeProcessCancelledException extends RuntimeException {
		private NativeProcessCancelledException(InterruptedException cause) {
			super("Native processor был отменён.", cause);
		}
	}
}
