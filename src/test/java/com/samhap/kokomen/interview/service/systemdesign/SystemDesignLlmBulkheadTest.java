package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemDesignLlmBulkheadTest {

    private static final String BUSY_MESSAGE = "요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요.";

    private SystemDesignLlmBulkhead bulkhead;
    private ExecutorService executor;
    private CountDownLatch releaseOccupants;
    private List<Future<?>> occupants;

    @BeforeEach
    void setUp() {
        bulkhead = new SystemDesignLlmBulkhead();
        executor = Executors.newFixedThreadPool(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS + 1);
        releaseOccupants = new CountDownLatch(1);
        occupants = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        releaseOccupants.countDown();
        for (Future<?> occupant : occupants) {
            occupant.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();
    }

    @Test
    void 허가를_받으면_결과를_돌려주고_허가를_반납한다() {
        // when
        String result = bulkhead.execute(() -> {
            assertThat(bulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS - 1);
            return "면접관 답";
        });

        // then
        assertAll(
                () -> assertThat(result).isEqualTo("면접관 답"),
                () -> assertThat(bulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS)
        );
    }

    @Test
    void 허가가_모두_쓰이면_2초_뒤_503을_던진다() throws Exception {
        // given
        occupyAllPermits();
        long startedAt = System.nanoTime();

        // when & then
        assertThatThrownBy(() -> bulkhead.execute(() -> "실행되면 안 되는 호출"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(BUSY_MESSAGE);
        Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);
        assertAll(
                () -> assertThat(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS).isEqualTo(8),
                () -> assertThat(SystemDesignLlmBulkhead.ACQUIRE_TIMEOUT).isEqualTo(Duration.ofSeconds(2)),
                () -> assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(1_900))
        );
    }

    // 실패한 호출까지 반납하면 503이 날 때마다 허가가 하나씩 늘어 상한이 무너진다.
    @Test
    void 허가를_못_받은_호출은_허가를_늘리지_않는다() throws Exception {
        // given
        occupyAllPermits();
        assertThatThrownBy(() -> bulkhead.execute(() -> "실행되면 안 되는 호출"))
                .isInstanceOf(ServiceUnavailableException.class);

        // when
        releaseOccupants.countDown();
        for (Future<?> occupant : occupants) {
            occupant.get(5, TimeUnit.SECONDS);
        }

        // then
        assertThat(bulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
    }

    @Test
    void 호출이_예외로_끝나도_허가를_반납한다() {
        // when & then
        assertThatThrownBy(() -> bulkhead.execute(() -> {
            throw new IllegalStateException("LLM 호출 실패");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LLM 호출 실패");
        assertThat(bulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
    }

    @Test
    void 대기_중_인터럽트되면_503이고_인터럽트_플래그를_복구한다() throws Exception {
        // given
        occupyAllPermits();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfterThrow = new AtomicBoolean();
        AtomicBoolean called = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                bulkhead.execute(() -> {
                    called.set(true);
                    return "실행되면 안 되는 호출";
                });
            } catch (RuntimeException e) {
                thrown.set(e);
                interruptedAfterThrow.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        await().atMost(Duration.ofSeconds(1)).until(() -> waiter.getState() == Thread.State.TIMED_WAITING);

        // when
        waiter.interrupt();
        waiter.join(Duration.ofSeconds(5).toMillis());

        // then
        assertAll(
                () -> assertThat(thrown.get()).isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage(BUSY_MESSAGE),
                () -> assertThat(interruptedAfterThrow).isTrue(),
                () -> assertThat(called).isFalse(),
                () -> assertThat(bulkhead.availablePermits()).isZero()
        );
    }

    // 다른 스레드 MAX_CONCURRENT_CALLS개가 허가를 쥔 채 releaseOccupants를 기다리게 한다.
    private void occupyAllPermits() throws InterruptedException {
        CountDownLatch acquired = new CountDownLatch(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        for (int i = 0; i < SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS; i++) {
            occupants.add(executor.submit(() -> bulkhead.execute(() -> {
                acquired.countDown();
                awaitRelease();
                return null;
            })));
        }
        assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(bulkhead.availablePermits()).isZero();
    }

    private void awaitRelease() {
        try {
            releaseOccupants.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
