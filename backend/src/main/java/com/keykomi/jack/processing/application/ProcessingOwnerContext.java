package com.keykomi.jack.processing.application;

import com.keykomi.jack.web.config.ProcessingSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessingOwnerContext {

	private final HttpServletRequest request;

	public ProcessingOwnerContext(HttpServletRequest request) {
		this.request = request;
	}

	public UUID ownerId() {
		var owner = this.request.getAttribute(ProcessingSessionFilter.OWNER_ATTRIBUTE);
		if (owner instanceof UUID ownerId) {
			return ownerId;
		}
		throw new IllegalStateException("Processing owner отсутствует в request context.");
	}
}
