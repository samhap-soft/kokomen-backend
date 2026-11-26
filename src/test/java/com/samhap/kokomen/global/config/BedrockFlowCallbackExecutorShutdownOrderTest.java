package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 컨텍스트를 닫을 때(배포의 SIGTERM) 채점 실행기가 진행 중인 채점을 기다리는 동안, 채점 워커가 쓰는 자원(채점·공용 Bedrock 클라이언트,
 * Redis, JPA, DB 커넥션 풀)이 먼저 닫히지 않는지 본다. 빈 파괴 순서는 등록 순서의 역순이라 실행기보다 늦게 등록된 자원은 실행기가
 * 기다리기 전에 닫힌다. 실제 앱에서도 채점 전용 Bedrock 클라이언트가 실행기보다 먼저 닫혔다. 여기서는 자원을 일부러 실행기보다 늦게
 * 등록해 순서가 등록 순서에 기대지 않는지 확인한다. 이름은 실제 앱의 빈 이름과 같아야 실행기의 의존 선언이 이 대역을 가리킨다.
 */
class BedrockFlowCallbackExecutorShutdownOrderTest {

    private static final List<String> GRADING_RESOURCES = List.of("systemDesignAnswerBedrockRuntimeClient",
            "bedrockRuntimeClient", "redissonClient", "entityManagerFactory", "dataSource");
    private static final String TASK_FINISHED = "채점 작업 끝";

    @Test
    void 컨텍스트를_닫으면_진행_중인_채점이_끝난_뒤에_채점이_쓰는_자원을_닫는다() {
        // given
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch started = new CountDownLatch(1);
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(AsyncConfig.class, GradingResources.class)
                .withBean("shutdownEvents", ShutdownEvents.class, () -> new ShutdownEvents(events));

        // when: 채점 작업을 맡긴 뒤 run이 끝나면 컨텍스트가 닫힌다.
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("bedrockFlowCallbackExecutor",
                    ThreadPoolTaskExecutor.class);
            executor.execute(() -> {
                started.countDown();
                sleepQuietly(Duration.ofMillis(500));
                events.add(TASK_FINISHED);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        });

        // then
        assertAll(GRADING_RESOURCES.stream().map(name -> (Executable) () -> assertThat(events)
                .contains(TASK_FINISHED, closed(name))
                .containsSubsequence(TASK_FINISHED, closed(name))));
    }

    private static String closed(String beanName) {
        return beanName + " 닫힘";
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 채점 워커가 쓰는 자원의 대역. 닫힐 때 이름을 남긴다. AsyncConfig 뒤에 등록되어 실행기보다 늦게 만들어진다.
     */
    @Configuration
    static class GradingResources {

        @Bean
        RecordingResource systemDesignAnswerBedrockRuntimeClient(ShutdownEvents shutdownEvents) {
            return new RecordingResource("systemDesignAnswerBedrockRuntimeClient", shutdownEvents);
        }

        @Bean
        RecordingResource bedrockRuntimeClient(ShutdownEvents shutdownEvents) {
            return new RecordingResource("bedrockRuntimeClient", shutdownEvents);
        }

        @Bean
        RecordingResource redissonClient(ShutdownEvents shutdownEvents) {
            return new RecordingResource("redissonClient", shutdownEvents);
        }

        @Bean
        RecordingResource entityManagerFactory(ShutdownEvents shutdownEvents) {
            return new RecordingResource("entityManagerFactory", shutdownEvents);
        }

        @Bean
        RecordingResource dataSource(ShutdownEvents shutdownEvents) {
            return new RecordingResource("dataSource", shutdownEvents);
        }
    }

    record ShutdownEvents(List<String> events) {
    }

    record RecordingResource(String name, ShutdownEvents shutdownEvents) implements DisposableBean {

        @Override
        public void destroy() {
            shutdownEvents.events().add(closed(name));
        }
    }
}
