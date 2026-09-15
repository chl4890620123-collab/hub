// bounded executor for document/STT jobs; prevents unbounded duplicate AI work from exhausting the server.
package com.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    /**
     * One analysis at a time by default. Importing a folder queues a job per file, and running several
     * against a single Gemini key exceeds its per-minute quota: the extra jobs come back 429 and the
     * documents are stored without a summary. Raise this only with quota to match.
     */
    @Value("${hub.ai-job-concurrency:1}")
    private int concurrency;

    @Bean(name = "hubTaskExecutor")
    public Executor hubTaskExecutor() {
        int threads = Math.max(1, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("hub-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
