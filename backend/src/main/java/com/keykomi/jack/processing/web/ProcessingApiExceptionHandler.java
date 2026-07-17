package com.keykomi.jack.processing.web;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.keykomi.jack.processing.web")
public class ProcessingApiExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
		var status = HttpStatus.resolve(exception.getStatusCode().value());
		var safeStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
		var headers = new HttpHeaders();
		if (safeStatus == HttpStatus.TOO_MANY_REQUESTS) {
			headers.set(HttpHeaders.RETRY_AFTER, "5");
		}
		return new ResponseEntity<>(
			new ApiError(codeFor(safeStatus), safeMessage(safeStatus, exception.getReason()), UUID.randomUUID()),
			headers,
			safeStatus
		);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
			new ApiError(
				"INTERNAL",
				"Сервис не смог завершить запрос. Используй correlation id при обращении в поддержку.",
				UUID.randomUUID()
			)
		);
	}

	private String codeFor(HttpStatus status) {
		return switch (status.value()) {
			case 400 -> "INVALID_FILE";
			case 413 -> "FILE_TOO_LARGE";
			case 416 -> "INVALID_RANGE";
			case 429 -> "RATE_LIMITED";
			case 404 -> "RESOURCE_NOT_FOUND";
			case 503 -> "CAPABILITY_UNAVAILABLE";
			default -> "PROCESSING_FAILED";
		};
	}

	private String safeMessage(HttpStatus status, String reason) {
		if (status == HttpStatus.NOT_FOUND) {
			return "Ресурс не найден, истёк или недоступен текущей session.";
		}
		if (reason != null && !reason.isBlank() && status != HttpStatus.INTERNAL_SERVER_ERROR) {
			return reason;
		}
		return "Сервис не смог завершить запрос.";
	}

	public record ApiError(String code, String message, UUID correlationId) {
	}
}
