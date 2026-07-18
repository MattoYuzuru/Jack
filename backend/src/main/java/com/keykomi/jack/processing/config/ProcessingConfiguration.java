package com.keykomi.jack.processing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

@Configuration(proxyBeanMethods = false)
public class ProcessingConfiguration {

	@Bean(initMethod = "migrate")
	Flyway processingFlyway(DataSource dataSource) {
		// Boot 4 разделяет infrastructure auto-configuration; явный bean гарантирует,
		// что durable schema существует до startup reconciliation и при локальном H2.
		return Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration")
			.load();
	}

	@Bean(destroyMethod = "shutdown")
	ExecutorService processingExecutor(ProcessingProperties properties) {
		var concurrency = Math.max(1, properties.getMaxConcurrentJobs());
		var queueCapacity = Math.max(1, properties.getJobQueueCapacity());

		// Виртуальные потоки не ограничивают нативные ffmpeg/ImageMagick процессы сами
		// по себе, поэтому admission обязан иметь жёсткие concurrency и queue bounds.
		return new ThreadPoolExecutor(
			concurrency,
			concurrency,
			0L,
			TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(queueCapacity),
			Thread.ofVirtual().name("jack-processing-", 0).factory(),
			new ThreadPoolExecutor.AbortPolicy()
		);
	}

	@Bean
	ObjectMapper processingObjectMapper() {
		return JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.findAndAddModules()
			.build();
	}

}
