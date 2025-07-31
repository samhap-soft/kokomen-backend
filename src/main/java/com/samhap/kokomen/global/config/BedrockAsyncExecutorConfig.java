package com.samhap.kokomen.global.config;

import com.samhap.kokomen.global.logging.MdcDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class BedrockAsyncExecutorConfig {

    @Bean("bedrockCallbackExecutor")
    public ThreadPoolTaskExecutor bedrockCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setTaskDecorator(new MdcDecorator());
        executor.setThreadNamePrefix("Async-Nonblock-Bedrock-");
        executor.initialize();
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        return executor;
    }
}
