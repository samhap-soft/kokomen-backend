package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.LLM_BUSY;

import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 요청 스레드에서 동기로 부르는 역질문 LLM 호출의 동시 실행 상한. Tomcat 스레드(30개)가 LLM 응답을 기다리며 모두 묶이지 않게 한다.
 * 허가를 {@link #ACQUIRE_TIMEOUT} 안에 받지 못하면 503으로 바로 답한다.
 */
@Slf4j
@Component
public class SystemDesignLlmBulkhead {

    public static final int MAX_CONCURRENT_CALLS = 8;
    public static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(2);

    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_CALLS);

    /**
     * 허가를 받은 경우에만 호출하고 끝나면 반납한다. 허가를 받지 못한 경우까지 반납하면 503이 날 때마다 허가가 늘어 상한이 무너진다.
     *
     * @throws ServiceUnavailableException 허가를 제때 받지 못했거나 기다리는 중에 인터럽트됐으면 503
     */
    public <T> T execute(Supplier<T> call) {
        acquire();
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(ACQUIRE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("시스템 설계 LLM 동시 실행 허가를 기다리다 인터럽트됐습니다. limit: {}", MAX_CONCURRENT_CALLS);
            throw new ServiceUnavailableException(LLM_BUSY.getMessage(), e);
        }
        if (!acquired) {
            log.warn("시스템 설계 LLM 동시 실행 상한 초과 - limit: {}, acquireTimeout: {}ms", MAX_CONCURRENT_CALLS,
                    ACQUIRE_TIMEOUT.toMillis());
            throw new ServiceUnavailableException(LLM_BUSY.getMessage());
        }
    }

    public int availablePermits() {
        return permits.availablePermits();
    }
}
