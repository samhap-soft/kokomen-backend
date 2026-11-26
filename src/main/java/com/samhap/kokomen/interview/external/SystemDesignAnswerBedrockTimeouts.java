package com.samhap.kokomen.interview.external;

import java.time.Duration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/**
 * 시스템 설계 답변 채점(L2: 1~5번째 답변, L3: 6번째 답변과 리포트) 전용 Bedrock 클라이언트의 타임아웃·재시도 사양.
 *
 * <p>채점은 비동기 워커에서 부르고, 프론트는 1~5번째 답변을 30번, 6번째 답변을 90번까지 1초마다 폴링한다. 공용 클라이언트(소켓 60초,
 * apiCall 타임아웃 없음, SDK 기본 재시도 최대 4회)는 생성이 60초를 넘으면 같은 채점을 네 번 보내 네 번 모두 과금하고, 실패로 끝날 때까지
 * 4분 가까이 걸려 폴링 한도와 진행 락(5분)을 거의 다 쓴다.
 *
 * <p>Converse는 생성을 마친 뒤 응답을 한 번에 보내므로 소켓 타임아웃이 곧 한 번 시도의 생성 시간 상한이다. L3 출력 상한 3,200토큰을 약
 * 62 tok/s로 끝까지 쓰면 약 52초라, 입력 처리 시간과 느린 시간대를 감안해 소켓 타임아웃을 70초로 둔다. API_CALL_TIMEOUT 85초는 재시도까지
 * 포함한 한 호출의 상한이고, 6번째 답변 폴링 한도(90초) 안에 결과가 저장되거나 LLM_FAILED가 보이게 한다.
 *
 * <p>L2와 L3가 이 클라이언트 하나를 같이 쓰고, L2에 30초 폴링에 맞춘 상한을 따로 두지 않는다. 요청 단위
 * {@code overrideConfiguration(apiCallTimeout)}으로 L2만 짧게 끊을 수는 있다(SDK가 요청 값을 클라이언트 값보다 먼저 쓴다). 하지만 L2 출력
 * 상한 2,048토큰을 같은 속도로 쓰면 약 33초라, 폴링에 맞춘 상한(예: 28초)은 늦지만 정상인 채점을 LLM_FAILED로 바꾸고, Bedrock이 느린 동안에는
 * 다시 내도 또 끊겨 그 질문을 넘길 수 없다. 상한이 없으면 그 채점은 30초를 조금 넘겨 저장되고 프론트는 폴링 한도 뒤 면접 조회로 이어 간다.
 *
 * <p>재시도는 빈 전략에서 시작해 네 예외만 허용한다. 스로틀링(429)·서버 내부 오류(500)·서비스 불가(503)는 대개 생성 전에 바로 온다. 모델
 * 타임아웃(408)은 처리한 뒤에 올 수 있지만 재시도까지 포함한 한 호출을 apiCall 타임아웃이 묶는다. 소켓 타임아웃과 이를 감싼
 * SdkClientException(apiCall 타임아웃 초과 포함)은 허용 목록에 없어 재시도하지 않는다. 이미 70초를 쓴 생성을 다시 하면 과금과 지연만 는다.
 * 연결·소켓 타임아웃과 최대 연결 수는 빌드된 BedrockRuntimeClient에서 읽을 수 없어 상수로 고정한다. 리전·apiCall 타임아웃·재시도 전략은
 * 빌드된 클라이언트에서 읽히므로 배선 테스트가 실제 빈에서 확인한다.
 */
public final class SystemDesignAnswerBedrockTimeouts {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(70);
    public static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(85);
    // 채점 실행기(bedrockFlowCallbackExecutor)의 스레드 수와 같다. 채점 워커가 연결을 기다리지 않는다.
    public static final int MAX_CONNECTIONS = 100;
    public static final int MAX_ATTEMPTS = 2;

    // 재시도는 한 번뿐이라 실제 대기는 0.5~1초다.
    private static final Duration BACKOFF_BASE = Duration.ofSeconds(1);
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(2);

    private SystemDesignAnswerBedrockTimeouts() {
    }

    public static StandardRetryStrategy retryStrategy() {
        return StandardRetryStrategy.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .useClientDefaults(false)
                .retryOnExceptionOrCauseInstanceOf(ThrottlingException.class)
                .retryOnExceptionOrCauseInstanceOf(InternalServerException.class)
                .retryOnExceptionOrCauseInstanceOf(ServiceUnavailableException.class)
                .retryOnExceptionOrCauseInstanceOf(ModelTimeoutException.class)
                .backoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(BACKOFF_BASE, BACKOFF_MAX))
                .throttlingBackoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(BACKOFF_BASE, BACKOFF_MAX))
                .build();
    }
}
