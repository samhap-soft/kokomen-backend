package com.samhap.kokomen.interview.external;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * 시스템 설계 면접 LLM 호출의 사용량·실패 로그. 호출 수에 따로 제한을 두지 않는 대신 면접 단위로 토큰 사용량을 남겨 비용을 추적한다.
 * {@code @ExecutionTimer}는 예외가 나면 시간을 남기지 않아 실패 로그에 걸린 시간을 함께 적는다.
 */
@Slf4j
public final class SystemDesignBedrockCallLogger {

    private SystemDesignBedrockCallLogger() {
    }

    /**
     * 응답에 usage가 없어도 실패하지 않고 빈 값(null)으로 적는다.
     */
    public static void logUsage(SystemDesignCallType callType, Long interviewId, ConverseResponse response) {
        TokenUsage usage = response.usage();
        log.info("시스템 설계 LLM 사용량 - interviewId: {}, callType: {}, inputTokens: {}, outputTokens: {}, "
                        + "cacheReadInputTokens: {}, cacheWriteInputTokens: {}, stopReason: {}",
                interviewId, callType,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.cacheReadInputTokens(),
                usage == null ? null : usage.cacheWriteInputTokens(),
                response.stopReasonAsString());
    }

    public static void logFailure(SystemDesignCallType callType, Long interviewId, long elapsedMillis,
                                  Exception exception) {
        log.warn("시스템 설계 LLM 호출 실패 - interviewId: {}, callType: {}, elapsedMillis: {}", interviewId, callType,
                elapsedMillis, exception);
    }
}
