package com.samhap.kokomen.global.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.willThrow;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.RedisException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class RedisServiceTest extends BaseTest {

    @Autowired
    private RedisService redisService;

    @Test
    void 같은_키값에_대해_분산락은_하나만_획득할_수_있다() {
        // given
        String lockKey = "test-lock-key";
        Duration ttl = Duration.ofSeconds(10);

        // when
        boolean firstTrial = redisService.acquireLock(lockKey, ttl);
        boolean secondTrial = redisService.acquireLock(lockKey, ttl);

        // then
        assertAll(
                () -> assertThat(firstTrial).isTrue(),
                () -> assertThat(secondTrial).isFalse()
        );
    }

    @Test
    void 분산락을_release하면_다시_획득할_수_있다() {
        // given
        String lockKey = "test-lock-key";
        Duration ttl = Duration.ofSeconds(10);

        // when
        boolean firstTrial = redisService.acquireLock(lockKey, ttl);
        boolean secondTrial = redisService.acquireLock(lockKey, ttl);
        redisService.releaseLock(lockKey);
        boolean retrial = redisService.acquireLock(lockKey, ttl);

        // then
        assertAll(
                () -> assertThat(firstTrial).isTrue(),
                () -> assertThat(secondTrial).isFalse(),
                () -> assertThat(retrial).isTrue()
        );
    }

    // 시스템 설계 API는 자기 경계(진행 락, 채점 상태 조회)에서만 사용자 문구로 바꾼다. 다른 API가 기대는 공용 감싸기는 그대로다.
    @Test
    void Redis_오류는_공용_레디스_예외_문구의_500으로_감싼다() {
        // given
        String lockKey = "test-lock-key";
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(lockKey, StringCodec.INSTANCE);

        // when & then
        assertThatThrownBy(() -> redisService.acquireLockWithValue(lockKey, "value", Duration.ofSeconds(10)))
                .isInstanceOf(RedisException.class)
                .hasMessage("레디스 예외 발생")
                .satisfies(exception -> assertThat(((RedisException) exception).getHttpStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
