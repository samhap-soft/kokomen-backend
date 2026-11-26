package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.interview.external.SystemDesignAnswerBedrockTimeouts;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class AsyncConfigTest {

    // 배포(SIGTERM) 때 진행 중인 채점을 끝까지 기다린다. 6번째 답변 채점은 전체 호출 상한(85초)까지 걸릴 수 있고 그 뒤에 저장·차감·상태
    // 기록이 남는다. 대기는 빈을 파괴할 때(컨텍스트 종료 단계 상한과 별개로) 일어나며, 이 시간 안에 끝나지 않은 작업은 버려진다.
    @Test
    void 채점_실행기는_종료할_때_채점_호출_상한에_저장_여유를_더한_것보다_오래_기다린다() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().bedrockFlowCallbackExecutor();

        try {
            Duration awaitTermination = Duration.ofMillis(
                    (Long) ReflectionTestUtils.getField(executor, "awaitTerminationMillis"));
            assertAll(
                    () -> assertThat(ReflectionTestUtils.getField(executor, "waitForTasksToCompleteOnShutdown"))
                            .isEqualTo(true),
                    () -> assertThat(awaitTermination).isEqualTo(Duration.ofSeconds(100)),
                    () -> assertThat(awaitTermination)
                            .isGreaterThan(SystemDesignAnswerBedrockTimeouts.API_CALL_TIMEOUT.plusSeconds(10))
            );
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 채점_실행기는_종료할_때_실행_중인_작업이_끝나기를_기다린다() throws Exception {
        ThreadPoolTaskExecutor executor = new AsyncConfig().bedrockFlowCallbackExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        executor.execute(() -> {
            started.countDown();
            sleepQuietly(Duration.ofMillis(500));
            finished.set(true);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        executor.shutdown();

        // then
        assertThat(finished).isTrue();
    }

    // 채점 전용 Bedrock 클라이언트의 최대 연결 수와 같아, 모든 채점 워커가 연결을 기다리지 않고 호출한다.
    @Test
    void 채점_실행기의_코어와_최대_스레드_수는_채점_클라이언트의_최대_연결_수와_같다() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().bedrockFlowCallbackExecutor();

        try {
            assertAll(
                    () -> assertThat(executor.getCorePoolSize())
                            .isEqualTo(SystemDesignAnswerBedrockTimeouts.MAX_CONNECTIONS),
                    () -> assertThat(executor.getMaxPoolSize())
                            .isEqualTo(SystemDesignAnswerBedrockTimeouts.MAX_CONNECTIONS)
            );
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 이력서_분석_executor는_코어와_최대가_60이고_큐가_40이다() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().resumeAnalysisExecutor();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(60);
            assertThat(executor.getMaxPoolSize()).isEqualTo(60);
            assertThat(executor.getQueueCapacity()).isEqualTo(40);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 이력서_분석_executor의_스레드_이름은_전용_prefix를_쓴다() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().resumeAnalysisExecutor();

        try {
            assertThat(executor.getThreadNamePrefix()).isEqualTo("Async-Resume-Analysis-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 이력서_분석_executor는_포화시_요청_스레드에_거절을_던진다() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().resumeAnalysisExecutor();

        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 이력서_분석_executor는_코어_스레드를_미리_기동한다() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().resumeAnalysisExecutor();

        try {
            assertThat(executor.getThreadPoolExecutor().getPoolSize()).isEqualTo(60);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 이력서_분석_executor는_제출_스레드의_MDC를_워커_스레드로_넘긴다() throws Exception {
        ThreadPoolTaskExecutor executor = new AsyncConfig().resumeAnalysisExecutor();
        AtomicReference<String> workerRequestId = new AtomicReference<>();

        try {
            MDC.put("requestId", "req-1");
            executor.submit(() -> workerRequestId.set(MDC.get("requestId"))).get();
        } finally {
            MDC.remove("requestId");
            executor.shutdown();
        }

        assertThat(workerRequestId.get()).isEqualTo("req-1");
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
