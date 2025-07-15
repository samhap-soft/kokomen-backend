package com.samhap.kokomen.global.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
}
