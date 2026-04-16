package com.samhap.kokomen.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slack 알림 전용 Rate Limiting Filter.
 * 동일한 에러 메시지에 대해 일정 시간(cooldown) 동안 중복 알림을 방지합니다.
 * Appender-level filter로 동작하여 FILE 로그에는 영향을 주지 않습니다.
 */
public class RateLimitingFilter extends Filter<ILoggingEvent> {

    private static final int MAX_CACHE_SIZE = 1000;

    private final ConcurrentHashMap<String, Long> lastLogTimes = new ConcurrentHashMap<>();
    private long cooldownMillis = 60000;

    public void setCooldownSeconds(int seconds) {
        this.cooldownMillis = seconds * 1000L;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getLevel() != Level.ERROR) {
            return FilterReply.NEUTRAL;
        }

        String errorKey = buildErrorKey(event);
        long now = System.currentTimeMillis();

        AtomicBoolean allowed = new AtomicBoolean(false);
        lastLogTimes.compute(errorKey, (key, lastTime) -> {
            if (lastTime == null || (now - lastTime) >= cooldownMillis) {
                allowed.set(true);
                return now;
            }
            return lastTime;
        });

        if (lastLogTimes.size() > MAX_CACHE_SIZE) {
            cleanupOldEntries(now);
        }

        return allowed.get() ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    private String buildErrorKey(ILoggingEvent event) {
        StringBuilder key = new StringBuilder();
        key.append(event.getLoggerName());

        String message = event.getMessage();
        if (message != null) {
            key.append(":").append(message.hashCode());
        }

        if (event.getThrowableProxy() != null) {
            key.append(":").append(event.getThrowableProxy().getClassName());
        }

        return key.toString();
    }

    private void cleanupOldEntries(long now) {
        lastLogTimes.entrySet().removeIf(
                entry -> (now - entry.getValue()) > cooldownMillis * 10
        );
    }
}
