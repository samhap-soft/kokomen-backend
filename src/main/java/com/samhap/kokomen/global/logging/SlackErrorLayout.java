package com.samhap.kokomen.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SlackErrorLayout extends LayoutBase<ILoggingEvent> {

    private static final int MAX_STACK_TRACE_LENGTH = 2000;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private String environment = "UNKNOWN";

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append(":rotating_light: *[").append(environment.toUpperCase()).append("]* 서버 에러 발생\n\n");

        String requestId = event.getMDCPropertyMap().get("requestId");
        if (requestId != null && !requestId.isEmpty()) {
            sb.append(":label: *Request ID:* `").append(requestId).append("`\n");
        }

        sb.append(":clock3: *Time:* ").append(DATE_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp()))).append("\n");
        sb.append(":page_facing_up: *Logger:* `").append(getShortLoggerName(event.getLoggerName())).append("`\n\n");

        sb.append(":speech_balloon: *Message:*\n```").append(event.getFormattedMessage()).append("```\n");

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            sb.append("\n:bug: *Stack Trace:*\n```");
            String stackTrace = ThrowableProxyUtil.asString(throwableProxy);
            if (stackTrace.length() > MAX_STACK_TRACE_LENGTH) {
                stackTrace = stackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... (truncated)";
            }
            sb.append(stackTrace);
            sb.append("```");
        }

        return sb.toString();
    }

    private String getShortLoggerName(String loggerName) {
        if (loggerName == null) {
            return "";
        }
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot > 0 ? loggerName.substring(lastDot + 1) : loggerName;
    }
}
