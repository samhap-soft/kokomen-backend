package com.samhap.kokomen.global.service;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.annotation.RedisExceptionWrapper;
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
import org.redisson.api.options.OptionalOptions;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

@Slf4j
@RedisExceptionWrapper
@ExecutionTimer
@RequiredArgsConstructor
@Service
public class RedisService {

    // 값이 같을 때만 지운다. 다른 요청이 다시 잡은 락은 지우지 않는다.
    private static final String RELEASE_LOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) "
            + "else return 0 end";

    private final RedissonClient redissonClient;

    public boolean acquireLock(String lockKey, Duration ttl) {
        return setIfAbsent(lockKey, "1", ttl);
    }

    public boolean acquireLockWithValue(String lockKey, String lockValue, Duration ttl) {
        return setIfAbsent(lockKey, lockValue, ttl);
    }

    public void releaseLockSafely(String lockKey, String expectedValue) {
        RScript rScript = redissonClient.getScript(StringCodec.INSTANCE);
        rScript.eval(
                RScript.Mode.READ_WRITE, RELEASE_LOCK_SCRIPT, RScript.ReturnType.INTEGER,
                Collections.singletonList(lockKey), expectedValue
        );
    }

    /**
     * {@link #releaseLockSafely}와 같지만 응답을 기다리는 요청 스레드에서 쓰도록 다시 보내지 않고 timeout 안에서 한 번만 보낸다.
     * 기본 설정(시도마다 3초, 다시 보내기 4번)이면 Redis가 멈췄을 때 해제 하나가 20초 가까이 걸린다.
     */
    public void releaseLockSafelyWithin(String lockKey, String expectedValue, Duration timeout) {
        RScript rScript = redissonClient.getScript(OptionalOptions.defaults()
                .codec(StringCodec.INSTANCE)
                .timeout(timeout)
                .retryAttempts(0));
        rScript.eval(
                RScript.Mode.READ_WRITE, RELEASE_LOCK_SCRIPT, RScript.ReturnType.INTEGER,
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

    /**
     * TTL이 아직 없을 때만 만료 시각을 설정한다(Redis EXPIRE ... NX). 매 호출마다 불러도 기존 만료 시각을
     * 밀지 않으므로 고정 시간창 카운터를 만들 수 있고, INCR 직후 프로세스가 죽어 TTL 없이 남은 키도
     * 다음 호출에서 스스로 회복한다.
     */
    public boolean expireKeyIfNotSet(String key, Duration ttl) {
        return redissonClient.getBucket(key).expireIfNotSet(ttl);
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
        return buckets.get(keys.toArray(new String[0]));
    }

    public void releaseLock(String lockKey) {
        redissonClient.getKeys().delete(lockKey);
    }

    public void deleteKey(String key) {
        redissonClient.getKeys().delete(key);
    }
}
