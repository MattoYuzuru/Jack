package com.keykomi.jack.web.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

	@Bean
	WebMvcConfigurer apiCorsConfigurer(
		@Value("${jack.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOriginsProperty
	) {
		var allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isBlank())
			.toArray(String[]::new);

		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				// Frontend dev-server ходит в backend processing API напрямую, поэтому
				// CORS нужен уже на foundation-этапе, а не только после production gateway.
				registry.addMapping("/api/**")
					.allowedOrigins(allowedOrigins)
					.allowedMethods("GET", "POST", "OPTIONS")
					.allowedHeaders("*");
			}
		};
	}

}
