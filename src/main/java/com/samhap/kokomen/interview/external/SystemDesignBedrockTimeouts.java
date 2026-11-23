package com.samhap.kokomen.interview.external;

import java.time.Duration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/**
 * 시스템 설계 역질문 답 전용 Bedrock 클라이언트의 타임아웃·재시도 사양.
 *
 * <p>역질문 답은 HTTP 요청 스레드에서 동기로 부른다. 최악 출력 1024 토큰을 약 62 tok/s로 만들면 약 17초이고 입력 처리 시간이 더 들어
 * 소켓 타임아웃을 20초로 둔다. API_CALL_TIMEOUT 25초는 재시도까지 포함한 한 호출의 상한이라, 요청 하나의 최악 응답 시간은
 * 동시 실행 허가 대기 2초 + 25초 + 저장 정도다. 공용 클라이언트(소켓 60초, apiCall 타임아웃 없음, SDK 기본 재시도)를 쓰면 장애 때
 * 요청 하나가 분 단위로 Tomcat 스레드를 붙잡는다.
 *
 * <p>재시도는 빈 전략에서 시작해 생산 없이 바로 오는 세 예외만 허용한다. 소켓 타임아웃은 이미 20초를 쓴 뒤라 다시 시도하면
 * 응답만 늦어지므로 허용 목록에 넣지 않는다. 빌드된 BedrockRuntimeClient에서는 타임아웃 설정을 읽을 수 없어 값을 상수로 고정한다.
 */
public final class SystemDesignBedrockTimeouts {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(20);
    public static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(25);
    // 동시 실행 상한(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS)과 같다. 허가를 받은 호출이 연결을 기다리지 않는다.
    public static final int MAX_CONNECTIONS = 8;
    public static final int MAX_ATTEMPTS = 2;

    // 재시도는 한 번뿐이라 실제 대기는 0.5~1초다.
    private static final Duration BACKOFF_BASE = Duration.ofSeconds(1);
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(2);

    private SystemDesignBedrockTimeouts() {
    }

    public static StandardRetryStrategy retryStrategy() {
        return StandardRetryStrategy.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .useClientDefaults(false)
                .retryOnExceptionOrCauseInstanceOf(ThrottlingException.class)
                .retryOnExceptionOrCauseInstanceOf(InternalServerException.class)
                .retryOnExceptionOrCauseInstanceOf(ModelTimeoutException.class)
                .backoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(BACKOFF_BASE, BACKOFF_MAX))
                .throttlingBackoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(BACKOFF_BASE, BACKOFF_MAX))
                .build();
    }
}
