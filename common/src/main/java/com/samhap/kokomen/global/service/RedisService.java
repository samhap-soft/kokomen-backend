package com.samhap.kokomen.global.service;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.annotation.RedisExceptionWrapper;
import com.samhap.kokomen.global.exception.RedisException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

@Slf4j
@RedisExceptionWrapper
@ExecutionTimer
@RequiredArgsConstructor
@Service
public class RedisService {

    private final RedissonClient redissonClient;

    public boolean acquireLock(String lockKey, Duration ttl) {
        return setIfAbsent(lockKey, "1", ttl);
    }

    public boolean acquireLockWithValue(String lockKey, String lockValue, Duration ttl) {
        return setIfAbsent(lockKey, lockValue, ttl);
    }

    public void releaseLockSafely(String lockKey, String expectedValue) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) "
                + "else return 0 end";
        RScript rScript = redissonClient.getScript(StringCodec.INSTANCE);
        rScript.eval(
                RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER,
                Collections.singletonList(lockKey), expectedValue
        );
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        return bucket.setIfAbsent(value, ttl);
    }

    public void setValue(String key, Object value, Duration ttl) {
        RBucket<Object> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.set(value, ttl);
    }

    public Long incrementKey(String key) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        return atomicLong.incrementAndGet();
    }

    public boolean expireKey(String key, Duration ttl) {
        return redissonClient.getBucket(key).expire(ttl);
    }

    public Iterable<String> scanKeys(String pattern, int scanCount) {
        return redissonClient.getKeys().getKeys(KeysScanOptions.defaults().pattern(pattern).chunkSize(scanCount));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        RBucket<Object> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        Object value = bucket.get();
        return Optional.ofNullable(value)
                .map(type::cast);
    }

    public Map<String, Object> multiGet(List<String> keys) {
        RBuckets buckets = redissonClient.getBuckets(StringCodec.INSTANCE);
        Map<String, Object> result = buckets.get(keys.toArray(new String[0]));
        if (result == null) {
            throw new RedisException("Redis 멀티 GET 실패. keys: " + keys);
        }
        return result;
    }

    public void releaseLock(String lockKey) {
        redissonClient.getKeys().delete(lockKey);
    }
}
