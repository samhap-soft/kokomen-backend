package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.PROCEED_IN_PROGRESS;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.TEMPORARILY_UNAVAILABLE;

import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.exception.RedisException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
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
 * 잡을 때의 Redis 오류는 시스템 설계 API의 사용자 문구(503)로 바꾼다. 기존 면접 API가 쓰는 RedisService의 공용 문구는 그대로 둔다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SystemDesignProceedLock {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final RedisService redisService;

    /**
     * @return 이 획득을 가리키는 값. 같은 값으로만 풀 수 있다
     * @throws ConflictException           같은 회원의 다른 요청이 락을 쥐고 있으면 409
     * @throws ServiceUnavailableException Redis 오류로 락을 잡지 못했으면 사용자 문구의 503(원인은 RedisException)
     */
    public String acquire(Long memberId) {
        String lockValue = UUID.randomUUID().toString();
        if (!tryAcquire(memberId, lockValue)) {
            throw new ConflictException(PROCEED_IN_PROGRESS.getMessage());
        }
        return lockValue;
    }

    // 명령이 Redis에 닿아 키가 생긴 뒤 응답만 잃었을 수 있다(응답 시간 초과). 그대로 두면 이 회원의 요청이 TTL(5분) 동안 409를 받으므로
    // 이 값으로 잡힌 락을 풀어 둔다. 해제도 실패하면 WARN만 남고 키는 TTL이 지나 사라진다.
    private boolean tryAcquire(Long memberId, String lockValue) {
        try {
            return redisService.acquireLockWithValue(createLockKey(memberId), lockValue, LOCK_TTL);
        } catch (RedisException e) {
            release(memberId, lockValue);
            throw new ServiceUnavailableException(TEMPORARILY_UNAVAILABLE.getMessage(), e);
        }
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
