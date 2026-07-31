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

    @Bean("gptCallbackExecutor")
    public ThreadPoolTaskExecutor gptCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setThreadNamePrefix("Async-Nonblock-GPT-");
        executor.initialize();
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        return executor;
    }

    @Bean("resumeAnalysisExecutor")
    public ThreadPoolTaskExecutor resumeAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(60);
        executor.setMaxPoolSize(60);
        executor.setQueueCapacity(40);
        executor.setThreadNamePrefix("Async-Resume-Analysis-");
        // 제출 스레드에서 MDC를 캡처해 워커 스레드로 넘긴다. 이 데코레이터 없이는 이력서 분석 워커의 모든 로그가
        // requestId 없이 찍혀 202를 응답한 요청과 상관관계를 잡을 수 없다. 워커 안에서는 제출 스레드의 MDC를
        // 알 수 없으므로(decorate가 제출 시점에 실행되어야 한다) 캡처는 반드시 여기서 일어나야 한다.
        executor.setTaskDecorator(new MdcDecorator());
        // 셧다운 시 큐를 버린다. 큐에 있던 행은 sweep이 종단 처리하며,
        // 억지로 실행하면 "중간에 죽는 태스크"만 늘어난다.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(60);
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
        return (ex, method, params) -> log.error("Async error in method: {} with params: {}", method.getName(), params,
                ex);
    }
}
