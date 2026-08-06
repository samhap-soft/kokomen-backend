package com.samhap.kokomen.resume.external;

import java.time.Duration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/**
 * 이력서 분석 전용 Bedrock 클라이언트의 타임아웃·재시도 사양.
 *
 * <p>평가 콜은 실측 62~64 tok/s로 최대 16,000 토큰을 생성해 약 258초가 걸린다. 공용 클라이언트의
 * 소켓 타임아웃 60초로는 매 시도가 끊기므로 360초를 준다(258초 대비 여유 40%). API_CALL_TIMEOUT 390초는
 * ResumeAnalysisRecoveryScheduler.STALE_THRESHOLD(10분) 안에 들어가므로, 아직 실행 중인 행이 스윕에
 * STALE_SWEEP으로 종단되는 경로가 열리지 않는다.
 *
 * <p>재시도는 StandardRetryStrategy.builder()로 빈 전략에서 시작한다. RetryStrategy.Builder의
 * retryOnException은 기존 조건에 더해지는 성질이라, SDK 기본 전략을 손봐서는 소켓 타임아웃을 재시도
 * 대상에서 뺄 수 없다. useClientDefaults(false)로 기본 조건 유입을 막고 아래 세 예외만 명시한다.
 * 소켓 타임아웃 재시도는 258초 생산을 통째로 다시 하는 낭비이고, 폐기된 응답이 그대로 과금된다.
 * 반면 세 예외는 생산 없이 즉시 오는 실패라 재시도가 싸다.
 *
 * <p>빌드된 BedrockRuntimeClient는 타임아웃 설정을 읽을 수 없어 상수로 분리했다. 이 상수는 의도값을
 * 고정할 뿐이고 SDK가 값을 지키는지는 배포 후 CloudWatch InvocationLatency로 검증한다.
 */
public final class ResumeAnalysisBedrockTimeouts {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(360);
    public static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(390);
    public static final int MAX_CONNECTIONS = 60;
    public static final int MAX_ATTEMPTS = 2;

    private static final Duration BACKOFF_BASE = Duration.ofSeconds(1);
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(5);

    private ResumeAnalysisBedrockTimeouts() {
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
