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
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, value, ttl);
        if (result == null) {
            throw new IllegalStateException("분산 락 획득 실패. key: " + lockKey);
        }
        return result;
    }

    public void incrementKey(String key) {
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count == null) {
            throw new IllegalStateException("Redis 카운트 증가 실패. key: " + key);
        }
    }

    public void expireKey(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
