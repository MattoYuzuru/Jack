package com.keykomi.jack.web.config;

import com.keykomi.jack.processing.config.ProcessingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

	private static final long WINDOW_SECONDS = 60L;
	private final ProcessingProperties properties;
	private final Clock clock;
	private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

	@Autowired
	public PublicApiRateLimitFilter(ProcessingProperties properties) {
		this(properties, Clock.systemUTC());
	}

	PublicApiRateLimitFilter(ProcessingProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return true;
		}

		return !("/api/uploads".equals(request.getRequestURI()) || "/api/jobs".equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		var now = Instant.now(this.clock).getEpochSecond();
		var windowStart = now - Math.floorMod(now, WINDOW_SECONDS);
		var key = request.getRemoteAddr();
		var limit = Math.max(1, this.properties.getPublicRequestsPerMinute());
		var window = this.windows.compute(key, (ignored, current) -> {
			if (current == null || current.startedAtEpochSecond() != windowStart) {
				return new RequestWindow(windowStart, 1);
			}
			return new RequestWindow(windowStart, current.requestCount() + 1);
		});

		if (window.requestCount() <= limit) {
			filterChain.doFilter(request, response);
			return;
		}

		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(Math.max(1L, windowStart + WINDOW_SECONDS - now)));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Слишком много запросов. Повтори позже.\"}");
	}

	private record RequestWindow(long startedAtEpochSecond, int requestCount) {
	}
}
