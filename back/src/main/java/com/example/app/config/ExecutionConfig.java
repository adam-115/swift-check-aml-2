package com.example.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool des exécutions de sources lancées depuis l'interface : deux à la fois, les autres attendent.
 */
@Configuration
public class ExecutionConfig {

    @Bean(name = "sourceExecutor")
    public TaskExecutor sourceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("source-exec-");
        executor.initialize();
        return executor;
    }
}
