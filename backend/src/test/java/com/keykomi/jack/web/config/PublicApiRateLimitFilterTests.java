package com.keykomi.jack.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.keykomi.jack.processing.config.ProcessingProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicApiRateLimitFilterTests {

	@Test
	void rejectsBurstWithRetryAfter() throws Exception {
		var properties = new ProcessingProperties();
		properties.setPublicRequestsPerMinute(2);
		var clock = Clock.fixed(Instant.parse("2026-07-12T20:00:10Z"), ZoneOffset.UTC);
		var filter = new PublicApiRateLimitFilter(properties, clock);
		var accepted = new AtomicInteger();

		for (var index = 0; index < 3; index += 1) {
			var request = new MockHttpServletRequest("POST", "/api/jobs");
			request.setRemoteAddr("203.0.113.10");
			var response = new MockHttpServletResponse();

			filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());

			if (index == 2) {
				assertThat(response.getStatus()).isEqualTo(429);
				assertThat(response.getHeader("Retry-After")).isEqualTo("50");
				assertThat(response.getContentAsString()).contains("RATE_LIMITED");
			}
		}

		assertThat(accepted).hasValue(2);
	}
}
