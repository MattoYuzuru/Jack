package com.keykomi.jack.processing.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProcessingException extends ResponseStatusException {

	private final String code;

	public ProcessingException(HttpStatus status, String code, String message) {
		super(status, message);
		this.code = code;
	}

	public ProcessingException(HttpStatus status, String code, String message, Throwable cause) {
		super(status, message, cause);
		this.code = code;
	}

	public String code() {
		return this.code;
	}
}
