package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

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
}
