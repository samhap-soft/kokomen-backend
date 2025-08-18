package com.samhap.kokomen.global.config;

import com.samhap.kokomen.global.logging.MdcDecorator;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setTaskDecorator(new MdcDecorator());
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        return executor;
    }

    @Bean("bedrockFlowCallbackExecutor")
    public ThreadPoolTaskExecutor bedrockFlowCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100); // TODO: Supertone 호출도 1~2초 걸리므로, 이를 논블로킹으로 전환한 뒤에 다시 크기 조정
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setThreadNamePrefix("Async-Nonblock-Bedrock-");
        executor.initialize();
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async error in method: {} with params: {}", method.getName(), params, ex);
        };
    }
}
