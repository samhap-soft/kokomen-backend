package com.samhap.kokomen.global.config;

import com.samhap.kokomen.global.logging.MdcDecorator;
import java.time.Duration;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // 시스템 설계 답변 채점 전용 Bedrock 클라이언트의 최대 연결 수(SystemDesignAnswerBedrockTimeouts.MAX_CONNECTIONS)와 같다.
    public static final int BEDROCK_FLOW_CALLBACK_POOL_SIZE = 100;
    // 6번째 답변 채점의 전체 호출 상한(SystemDesignAnswerBedrockTimeouts.API_CALL_TIMEOUT, 85초)에 저장·차감·상태 기록 여유를 더했다.
    public static final Duration BEDROCK_FLOW_CALLBACK_AWAIT_TERMINATION = Duration.ofSeconds(100);

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

    /**
     * 기존 면접(V2) 답변 처리와 시스템 설계 답변 채점(L2·L3)의 워커. 배포(SIGTERM) 때 진행 중이거나 큐에 있는 작업을 끝까지 기다린다.
     * 기다림은 웹 서버의 우아한 종료 단계(spring.lifecycle.timeout-per-shutdown-phase)가 끝난 뒤 이 빈을 파괴할 때 일어나고 최대
     * {@link #BEDROCK_FLOW_CALLBACK_AWAIT_TERMINATION}이다. 채점은 늦어도 그 종료 단계 안에 맡겨지고 맡긴 때부터 전체 호출 상한(85초) +
     * 저장 안에 끝나므로, 컨테이너 종료 유예(docker stop -t, stop_grace_period)를 종료 단계 상한과 이 대기를 더한 것보다 길게 두면 진행 중인
     * 채점이 끝난다(ShutdownGracePeriodParityTest가 고정한다). 이력서 분석이 돌고 있으면 실행기 생명주기 단계와 이력서 실행기 종료가 이
     * 실행기보다 먼저 더 기다려 앱 전체 종료가 유예를 넘길 수 있지만, 그때도 채점은 끝나 있다.
     *
     * <p>빈은 등록의 역순으로 파괴되므로, 의존을 선언하지 않으면 워커가 쓰는 자원이 이 실행기가 기다리기 전에 닫힐 수 있다. 실제로 채점 전용
     * Bedrock 클라이언트가 실행기보다 먼저 닫혔다. 워커가 쓰는 Bedrock 클라이언트·Redis·JPA·DB 커넥션 풀에 의존을 선언해, 이 실행기를 먼저
     * 파괴(작업 대기)한 뒤에 그 자원들을 닫게 한다(BedrockFlowCallbackExecutorShutdownOrderTest가 고정한다).
     */
    @DependsOn({"systemDesignAnswerBedrockRuntimeClient", "bedrockRuntimeClient", "redissonClient",
            "entityManagerFactory", "dataSource"})
    @Bean("bedrockFlowCallbackExecutor")
    public ThreadPoolTaskExecutor bedrockFlowCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // TODO: Supertone 호출도 1~2초 걸리므로, 이를 논블로킹으로 전환한 뒤에 다시 크기 조정
        executor.setCorePoolSize(BEDROCK_FLOW_CALLBACK_POOL_SIZE);
        executor.setMaxPoolSize(BEDROCK_FLOW_CALLBACK_POOL_SIZE);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(BEDROCK_FLOW_CALLBACK_AWAIT_TERMINATION.toMillis());
        executor.setThreadNamePrefix("Async-Nonblock-Bedrock-");
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
