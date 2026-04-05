package com.keykomi.jack.processing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProcessingConfiguration {

	@Bean(destroyMethod = "close")
	ExecutorService processingExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean
	ObjectMapper processingObjectMapper() {
		return JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.findAndAddModules()
			.build();
	}

}
