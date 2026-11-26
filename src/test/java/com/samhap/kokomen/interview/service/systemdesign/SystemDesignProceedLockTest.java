package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.spy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.exception.RedisException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import com.samhap.kokomen.global.service.RedisService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.StringCodec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignProceedLockTest extends BaseTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    // 기존 면접 답변 처리와 같은 회원 단위 키라 두 흐름이 서로를 막는다.
    private static final String LOCK_KEY = "lock:interview:proceed:1";
    private static final String OTHER_LOCK_KEY = "lock:interview:proceed:2";
    private static final String PROCEED_IN_PROGRESS_MESSAGE = "이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.";
    private static final String TEMPORARILY_UNAVAILABLE_MESSAGE = "일시적인 문제로 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";

    @Autowired
    private SystemDesignProceedLock systemDesignProceedLock;
    @Autowired
    private RedisService redisService;

    @Test
    void 락을_잡으면_값을_돌려주고_다시_잡으면_409다() {
        // when
        String lockValue = systemDesignProceedLock.acquire(MEMBER_ID);

        // then
        long remainTimeToLive = redissonClient.getBucket(LOCK_KEY, StringCodec.INSTANCE).remainTimeToLive();
        assertAll(
                () -> assertThat(redisService.get(LOCK_KEY, String.class)).contains(lockValue),
                () -> assertThat(remainTimeToLive).isGreaterThan(Duration.ofMinutes(4).toMillis())
                        .isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis()),
                () -> assertThatThrownBy(() -> systemDesignProceedLock.acquire(MEMBER_ID))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(PROCEED_IN_PROGRESS_MESSAGE)
        );
    }

    @Test
    void 잡은_값으로_풀면_다시_잡을_수_있다() {
        // given
        String lockValue = systemDesignProceedLock.acquire(MEMBER_ID);

        // when
        systemDesignProceedLock.release(MEMBER_ID, lockValue);

        // then
        assertAll(
                () -> assertThat(redisService.get(LOCK_KEY, String.class)).isEmpty(),
                () -> assertThatCode(() -> systemDesignProceedLock.acquire(MEMBER_ID)).doesNotThrowAnyException()
        );
    }

    // 값이 고정이면 늦게 끝난 요청이 다음 요청의 락을 풀 수 있다.
    @Test
    void 잡을_때마다_다른_값을_받는다() {
        // when
        String firstValue = systemDesignProceedLock.acquire(MEMBER_ID);
        String secondValue = systemDesignProceedLock.acquire(OTHER_MEMBER_ID);

        // then
        assertAll(
                () -> assertThat(firstValue).isNotEqualTo(secondValue),
                () -> assertThat(redisService.get(LOCK_KEY, String.class)).contains(firstValue),
                () -> assertThat(redisService.get(OTHER_LOCK_KEY, String.class)).contains(secondValue)
        );
    }

    @Test
    void 다른_값으로는_풀리지_않는다() {
        // given
        String lockValue = systemDesignProceedLock.acquire(MEMBER_ID);

        // when
        systemDesignProceedLock.release(MEMBER_ID, "other-request");

        // then
        assertAll(
                () -> assertThat(redisService.get(LOCK_KEY, String.class)).contains(lockValue),
                () -> assertThatThrownBy(() -> systemDesignProceedLock.acquire(MEMBER_ID))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(PROCEED_IN_PROGRESS_MESSAGE)
        );
    }

    // 동시 실행 허가를 기다리다 인터럽트되어 503이 난 요청도 finally에서 락을 푼다. 인터럽트 플래그가 있으면 Redis 동기 호출이
    // 결과를 기다리지 않고 바로 실패한다(커넥션을 비동기로 받는 중이면 해제 명령이 나가지 않아 키가 TTL까지 남는다).
    // 그래서 해제하는 동안만 플래그를 내렸다가 되살린다. 키가 지워졌는지만 보면 명령이 먼저 나간 경우에 실패를 놓치므로
    // 해제가 실패로 끝나지 않았는지(WARN이 없는지)도 본다.
    @Test
    void 인터럽트된_스레드에서도_락을_풀고_인터럽트_플래그를_되살린다() {
        // given
        String lockValue = systemDesignProceedLock.acquire(MEMBER_ID);
        Logger lockLogger = (Logger) LoggerFactory.getLogger(SystemDesignProceedLock.class);
        ListAppender<ILoggingEvent> lockLogs = new ListAppender<>();
        lockLogs.start();
        lockLogger.addAppender(lockLogs);
        boolean interruptedAfterRelease;

        // when
        Thread.currentThread().interrupt();
        try {
            systemDesignProceedLock.release(MEMBER_ID, lockValue);
        } finally {
            interruptedAfterRelease = Thread.interrupted();
            lockLogger.detachAppender(lockLogs);
        }

        // then
        assertAll(
                () -> assertThat(interruptedAfterRelease).isTrue(),
                () -> assertThat(lockLogs.list).noneMatch(event -> event.getLevel() == Level.WARN),
                () -> assertThat(redisService.get(LOCK_KEY, String.class)).isEmpty()
        );
    }

    // 락을 잡지 못한 원인이 Redis 오류면 내부 문구(레디스 예외 발생)의 500 대신 사용자 문구의 503으로 알린다. 원인은 남긴다.
    @Test
    void 잡을_때_Redis가_실패하면_사용자_문구의_503이고_원인은_레디스_예외다() {
        // given
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(LOCK_KEY, StringCodec.INSTANCE);

        // when
        Throwable thrown = catchThrowable(() -> systemDesignProceedLock.acquire(MEMBER_ID));

        // then
        assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(TEMPORARILY_UNAVAILABLE_MESSAGE)
                .hasCauseInstanceOf(RedisException.class);
    }

    // 명령은 Redis에 닿아 키가 생겼는데 응답만 잃은 경우(응답 시간 초과)다. 그대로 두면 이 회원의 다음 요청이 5분 동안 409를 받는다.
    @Test
    void 잡기가_Redis_오류로_끝나도_실제로_잡힌_락은_풀어_둔다() {
        // given
        willAnswer(invocation -> {
            RBucket<Object> bucket = spy((RBucket<Object>) invocation.callRealMethod());
            willAnswer(setIfAbsent -> {
                setIfAbsent.callRealMethod();
                throw new RedisTimeoutException("응답 시간 초과");
            }).given(bucket).setIfAbsent(anyString(), any(Duration.class));
            return bucket;
        }).given(redissonClient).getBucket(LOCK_KEY, StringCodec.INSTANCE);

        // when
        Throwable thrown = catchThrowable(() -> systemDesignProceedLock.acquire(MEMBER_ID));

        // then
        assertAll(
                () -> assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage(TEMPORARILY_UNAVAILABLE_MESSAGE),
                () -> assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse()
        );
    }

    @Test
    void 해제가_실패해도_예외를_던지지_않는다() {
        // given
        String lockValue = systemDesignProceedLock.acquire(MEMBER_ID);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when
        Throwable thrown = catchThrowable(() -> systemDesignProceedLock.release(MEMBER_ID, lockValue));

        // then
        assertAll(
                () -> assertThat(thrown).isNull(),
                // 해제가 실제로 실패해 키가 남아 있다. 남은 키는 TTL이 지나면 사라진다.
                () -> assertThat(redisService.get(LOCK_KEY, String.class)).contains(lockValue)
        );
    }
}
