package com.samhap.kokomen.global.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean acquireLock(String lockKey, Duration ttl) {
        return setIfAbsent(lockKey, "1", ttl);
    }

    public boolean setIfAbsent(String lockKey, String value, Duration ttl) {
        Boolean setSuccess = redisTemplate.opsForValue().setIfAbsent(lockKey, value, ttl);
        if (setSuccess == null) {
            throw new IllegalStateException("분산 락 획득 실패. key: " + lockKey);
        }
        return setSuccess;
    }

    public void incrementKey(String key) {
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count == null) {
            throw new IllegalStateException("Redis 카운트 증가 실패. key: " + key);
        }
    }

    public boolean expireKey(String key, Duration ttl) {
        Boolean expireSuccess = redisTemplate.expire(key, ttl);
        if (expireSuccess == null) {
            throw new IllegalStateException("Redis 키 만료 설정 실패. key: " + key);
        }

        return expireSuccess;
    }

    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
