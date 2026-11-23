package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.willThrow;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.service.RedisService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignProceedLockTest extends BaseTest {

    private static final Long MEMBER_ID = 1L;
    // 기존 면접 답변 처리와 같은 회원 단위 키라 두 흐름이 서로를 막는다.
    private static final String LOCK_KEY = "lock:interview:proceed:1";
    private static final String PROCEED_IN_PROGRESS_MESSAGE = "이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.";

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
                () -> assertThat(remainTimeToLive).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis()),
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

    @Test
    void 해제가_실패해도_예외를_던지지_않는다() {
        // given
        String lockValue = systemDesignProceedLock.acquire(MEMBER_ID);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when & then
        assertThatCode(() -> systemDesignProceedLock.release(MEMBER_ID, lockValue)).doesNotThrowAnyException();
    }
}
