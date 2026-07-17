package com.keykomi.jack.web.config;

import com.keykomi.jack.processing.config.ProcessingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProcessingSessionFilter extends OncePerRequestFilter {

	public static final String OWNER_ATTRIBUTE = ProcessingSessionFilter.class.getName() + ".owner";
	private final ProcessingProperties properties;
	private final byte[] signingKey;

	public ProcessingSessionFilter(ProcessingProperties properties) {
		this.properties = properties;
		this.signingKey = properties.getSessionSecret().getBytes(StandardCharsets.UTF_8);
		if (this.signingKey.length < 32) {
			throw new IllegalStateException("JACK_PROCESSING_SESSION_SECRET должен содержать минимум 32 байта.");
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		var ownerId = readOwner(request);
		if (ownerId == null) {
			ownerId = fixedOwner().orElseGet(UUID::randomUUID);
			response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ownerId).toString());
		}

		request.setAttribute(OWNER_ATTRIBUTE, ownerId);
		filterChain.doFilter(request, response);
	}

	private java.util.Optional<UUID> fixedOwner() {
		var configured = this.properties.getFixedSessionOwner();
		if (configured == null || configured.isBlank()) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(UUID.fromString(configured));
	}

	private UUID readOwner(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}

		for (Cookie cookie : request.getCookies()) {
			if (!this.properties.getSessionCookieName().equals(cookie.getName())) {
				continue;
			}

			var parts = cookie.getValue().split("\\.", 2);
			if (parts.length != 2) {
				return null;
			}

			try {
				var expected = sign(parts[0]);
				var actual = Base64.getUrlDecoder().decode(parts[1]);
				if (!MessageDigest.isEqual(expected, actual)) {
					return null;
				}
				return UUID.fromString(parts[0]);
			}
			catch (IllegalArgumentException exception) {
				return null;
			}
		}

		return null;
	}

	private ResponseCookie buildCookie(UUID ownerId) {
		var value = ownerId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(ownerId.toString()));
		return ResponseCookie.from(this.properties.getSessionCookieName(), value)
			.httpOnly(true)
			.secure(this.properties.isSessionCookieSecure())
			.sameSite("Strict")
			.path("/")
			.maxAge(Duration.ofHours(Math.max(1L, this.properties.getUploadRetentionHours())))
			.build();
	}

	private byte[] sign(String value) {
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(this.signingKey, "HmacSHA256"));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("HMAC-SHA256 должен быть доступен в стандартной JDK.", exception);
		}
	}
}
