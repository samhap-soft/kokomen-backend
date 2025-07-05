package com.samhap.kokomen.global.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisDistributedLockService {

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean acquireLock(String lockKey, Duration ttl) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl);
        return Boolean.TRUE.equals(result);
    }

    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
