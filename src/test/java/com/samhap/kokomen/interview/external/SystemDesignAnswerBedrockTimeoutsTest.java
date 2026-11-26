package com.samhap.kokomen.interview.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.config.AsyncConfig;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.retries.api.AcquireInitialTokenRequest;
import software.amazon.awssdk.retries.api.AcquireInitialTokenResponse;
import software.amazon.awssdk.retries.api.RefreshRetryTokenRequest;
import software.amazon.awssdk.retries.api.RefreshRetryTokenResponse;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.retries.api.TokenAcquisitionFailedException;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/**
 * 답변 채점(L2·L3) 전용 Bedrock 클라이언트의 타임아웃 상수와 재시도 전략을 고정한다.
 * 재시도 판정은 전략 객체를 직접 두드려 본다. 이 전략이 실제 클라이언트 실행 파이프라인에 연결됐을 때의 동작까지는 보증하지 않는다.
 */
class SystemDesignAnswerBedrockTimeoutsTest {

    @Test
    void 타임아웃_상수는_커넥트_3초_소켓_70초_api콜_85초다() {
        assertAll(
                () -> assertThat(SystemDesignAnswerBedrockTimeouts.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3)),
                () -> assertThat(SystemDesignAnswerBedrockTimeouts.SOCKET_TIMEOUT).isEqualTo(Duration.ofSeconds(70)),
                () -> assertThat(SystemDesignAnswerBedrockTimeouts.API_CALL_TIMEOUT).isEqualTo(Duration.ofSeconds(85))
        );
    }

    @Test
    void 소켓_타임아웃은_apiCall_타임아웃보다_짧다() {
        assertThat(SystemDesignAnswerBedrockTimeouts.SOCKET_TIMEOUT)
                .isLessThan(SystemDesignAnswerBedrockTimeouts.API_CALL_TIMEOUT);
    }

    @Test
    void 재시도_전략은_최대_2회_시도하고_클라이언트_기본값을_쓰지_않는다() {
        RetryStrategy strategy = SystemDesignAnswerBedrockTimeouts.retryStrategy();

        assertAll(
                () -> assertThat(SystemDesignAnswerBedrockTimeouts.MAX_ATTEMPTS).isEqualTo(2),
                () -> assertThat(strategy.maxAttempts()).isEqualTo(2),
                () -> assertThat(strategy.useClientDefaults()).isFalse()
        );
    }

    // 스로틀링·서버 내부 오류·서비스 불가는 대개 생성 전에 바로 와 다시 보내는 값이 싸다. 모델 타임아웃은 처리한 뒤에 올 수 있지만
    // 재시도까지 포함한 한 호출을 apiCall 85초가 묶는다. 역질문 답 클라이언트와 달리 서비스 불가(503)도 재시도한다.
    @Test
    void 스로틀링_서버_내부_서비스_불가_모델_타임아웃_예외는_재시도한다() {
        assertAll(
                () -> assertThat(refreshWith(ThrottlingException.builder().message("throttled").build()).token())
                        .isNotNull(),
                () -> assertThat(refreshWith(InternalServerException.builder().message("internal").build()).token())
                        .isNotNull(),
                () -> assertThat(refreshWith(ServiceUnavailableException.builder().message("unavailable").build())
                        .token())
                        .isNotNull(),
                () -> assertThat(refreshWith(ModelTimeoutException.builder().message("model timeout").build())
                        .token())
                        .isNotNull()
        );
    }

    // 소켓 타임아웃은 이미 70초 동안 생성한 뒤라, 다시 보내면 같은 채점을 한 번 더 과금하고 응답만 늦어진다.
    // 전체 호출 상한(apiCall 타임아웃)을 넘긴 호출도 SdkClientException이라 다시 보내지 않는다.
    @Test
    void 소켓_타임아웃과_이를_감싼_SdkClientException과_호출_상한_초과는_재시도하지_않는다() {
        SdkClientException wrapped = SdkClientException.builder()
                .message("Unable to execute HTTP request: Read timed out")
                .cause(new SocketTimeoutException("Read timed out"))
                .build();

        assertAll(
                () -> assertThatThrownBy(() -> refreshWith(new SocketTimeoutException("Read timed out")))
                        .isInstanceOf(TokenAcquisitionFailedException.class),
                () -> assertThatThrownBy(() -> refreshWith(wrapped))
                        .isInstanceOf(TokenAcquisitionFailedException.class),
                () -> assertThatThrownBy(() -> refreshWith(ApiCallTimeoutException.create(85_000L)))
                        .isInstanceOf(TokenAcquisitionFailedException.class)
        );
    }

    // 채점 실행기의 스레드 수만큼 연결을 두어, 워커가 연결을 기다리지 않게 한다.
    @Test
    void 최대_연결_수는_채점_실행기_스레드_수와_같다() {
        assertThat(SystemDesignAnswerBedrockTimeouts.MAX_CONNECTIONS)
                .isEqualTo(AsyncConfig.BEDROCK_FLOW_CALLBACK_POOL_SIZE);
    }

    private RefreshRetryTokenResponse refreshWith(Throwable failure) {
        RetryStrategy strategy = SystemDesignAnswerBedrockTimeouts.retryStrategy();
        AcquireInitialTokenResponse initial = strategy.acquireInitialToken(
                AcquireInitialTokenRequest.create("system-design-answer"));

        return strategy.refreshRetryToken(RefreshRetryTokenRequest.builder()
                .token(initial.token())
                .failure(failure)
                .build());
    }
}
