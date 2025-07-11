package com.samhap.kokomen.global.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
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

    public Long incrementKey(String key) {
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count == null) {
            throw new IllegalStateException("Redis 카운트 증가 실패. key: " + key);
        }

        return count;
    }

    public boolean expireKey(String key, Duration ttl) {
        Boolean expireSuccess = redisTemplate.expire(key, ttl);
        if (expireSuccess == null) {
            throw new IllegalStateException("Redis 키 만료 설정 실패. key: " + key);
        }

        return expireSuccess;
    }

    public Cursor<String> scanKeys(String pattern, int scanCount) {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(scanCount)
                .build();

        return redisTemplate.scan(scanOptions);
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value)
                .map(type::cast);
    }

    public Map<String, Object> multiGet(List<String> keys) {
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            throw new IllegalStateException("Redis 멀티 GET 실패. keys: " + keys);
        }

        return IntStream.range(0, keys.size())
                .boxed()
                .collect(Collectors.toMap(keys::get, values::get));
    }

    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
