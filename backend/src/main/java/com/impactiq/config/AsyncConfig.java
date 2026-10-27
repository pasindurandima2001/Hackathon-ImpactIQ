package com.impactiq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executors for background work. Full queues reject new work (AbortPolicy) so callers can
 * report a clear "busy" state instead of piling up model calls. Executors drain on shutdown (SIGTERM).
 */
@Configuration
@EnableScheduling
public class AsyncConfig {

    private static final int SHUTDOWN_WAIT_SECONDS = 30;
    private static final int COMPONENT_DISCOVERY_QUEUE = 20;

    @Bean(name = "investigationExecutor")
    public ThreadPoolTaskExecutor investigationExecutor(InvestigationProperties properties) {
        return executor("investigation-", properties.getExecutorThreads(), properties.getExecutorQueueCapacity());
    }

    @Bean(name = "documentExecutor")
    public ThreadPoolTaskExecutor documentExecutor(KnowledgeProperties properties) {
        return executor("document-", properties.getProcessingThreads(), properties.getProcessingQueueCapacity());
    }

    /** One model call at a time; discovery is best-effort and never delays document indexing. */
    @Bean(name = "componentDiscoveryExecutor")
    public ThreadPoolTaskExecutor componentDiscoveryExecutor() {
        return executor("component-discovery-", 1, COMPONENT_DISCOVERY_QUEUE);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int threads, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(Math.max(1, threads));
        executor.setMaxPoolSize(Math.max(1, threads));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        executor.initialize();
        return executor;
    }
}
