package com.samhap.kokomen.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

import java.util.concurrent.ConcurrentHashMap;

public class RateLimitingTurboFilter extends TurboFilter {

    private static final int MAX_CACHE_SIZE = 1000;

    private final ConcurrentHashMap<String, Long> lastLogTimes = new ConcurrentHashMap<>();
    private long cooldownMillis = 60000;

    public void setCooldownSeconds(int seconds) {
        this.cooldownMillis = seconds * 1000L;
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        if (level != Level.ERROR) {
            return FilterReply.NEUTRAL;
        }

        String errorKey = buildErrorKey(logger, format, t);
        long now = System.currentTimeMillis();

        Long lastTime = lastLogTimes.get(errorKey);
        if (lastTime != null && (now - lastTime) < cooldownMillis) {
            return FilterReply.DENY;
        }

        lastLogTimes.put(errorKey, now);

        if (lastLogTimes.size() > MAX_CACHE_SIZE) {
            cleanupOldEntries(now);
        }

        return FilterReply.NEUTRAL;
    }

    private String buildErrorKey(Logger logger, String format, Throwable t) {
        StringBuilder key = new StringBuilder();
        key.append(logger.getName());
        if (format != null) {
            key.append(":").append(format.hashCode());
        }
        if (t != null) {
            key.append(":").append(t.getClass().getName());
        }
        return key.toString();
    }

    private void cleanupOldEntries(long now) {
        lastLogTimes.entrySet().removeIf(
                entry -> (now - entry.getValue()) > cooldownMillis * 10
        );
    }
}
