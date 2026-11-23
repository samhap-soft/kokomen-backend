package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.PROCEED_IN_PROGRESS;

import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시스템 설계 면접의 진행 락. 기존 면접 답변과 같은 회원 단위 키(SET NX, 값은 UUID)를 써서 같은 회원의 진행 요청을 하나씩만 처리한다.
 * Redisson 락(@DistributedLock)으로 같은 이름을 만들면 자료형이 달라 충돌하므로 RedisService의 값 기반 락을 그대로 쓴다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SystemDesignProceedLock {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final RedisService redisService;

    /**
     * @return 이 획득을 가리키는 값. 같은 값으로만 풀 수 있다
     * @throws ConflictException 같은 회원의 다른 요청이 락을 쥐고 있으면 409
     */
    public String acquire(Long memberId) {
        String lockValue = UUID.randomUUID().toString();
        if (!redisService.acquireLockWithValue(createLockKey(memberId), lockValue, LOCK_TTL)) {
            throw new ConflictException(PROCEED_IN_PROGRESS.getMessage());
        }
        return lockValue;
    }

    /**
     * 잡을 때 받은 값이 남아 있을 때만 푼다. 해제 실패가 이미 만든 응답이나 원래 예외를 덮지 않도록 WARN 로그만 남긴다.
     * 남은 키는 TTL이 지나면 사라진다.
     * 인터럽트된 스레드(동시 실행 허가를 기다리다 인터럽트되어 503이 난 요청 등)에서는 Redis 동기 호출이 결과를 기다리지 않고
     * 바로 실패하므로, 해제하는 동안만 인터럽트 플래그를 내렸다가 되살린다.
     */
    public void release(Long memberId, String lockValue) {
        boolean interrupted = Thread.interrupted();
        try {
            redisService.releaseLockSafely(createLockKey(memberId), lockValue);
        } catch (RuntimeException e) {
            log.warn("시스템 설계 진행 락 해제 실패 - memberId: {}", memberId, e);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String createLockKey(Long memberId) {
        return InterviewProceedFacadeService.createInterviewProceedLockKey(memberId);
    }
}
