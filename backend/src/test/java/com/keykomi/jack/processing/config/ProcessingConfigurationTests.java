package com.keykomi.jack.processing.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProcessingConfigurationTests {

	@Test
	void rejectsWorkWhenConcurrencyAndQueueBudgetsAreExhausted() throws Exception {
		var properties = new ProcessingProperties();
		properties.setMaxConcurrentJobs(1);
		properties.setJobQueueCapacity(1);
		var executor = new ProcessingConfiguration().processingExecutor(properties);
		var release = new CountDownLatch(1);

		try {
			executor.execute(() -> await(release));
			executor.execute(() -> await(release));

			assertThatThrownBy(() -> executor.execute(() -> { }))
				.isInstanceOf(RejectedExecutionException.class);
		}
		finally {
			release.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
