package com.samhap.kokomen.interview.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.interview.service.systemdesign.SystemDesignLlmBulkhead;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.retries.api.AcquireInitialTokenRequest;
import software.amazon.awssdk.retries.api.AcquireInitialTokenResponse;
import software.amazon.awssdk.retries.api.RefreshRetryTokenRequest;
import software.amazon.awssdk.retries.api.RefreshRetryTokenResponse;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.retries.api.TokenAcquisitionFailedException;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/**
 * 역질문 답(동기 호출) 전용 Bedrock 클라이언트의 타임아웃 상수와 재시도 전략을 고정한다.
 * 재시도 판정은 전략 객체를 직접 두드려 본다. 이 전략이 실제 클라이언트 실행 파이프라인에 연결됐을 때의 동작까지는 보증하지 않는다.
 */
class SystemDesignBedrockTimeoutsTest {

    @Test
    void 타임아웃_상수는_커넥트_3초_소켓_20초_api콜_25초다() {
        assertAll(
                () -> assertThat(SystemDesignBedrockTimeouts.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3)),
                () -> assertThat(SystemDesignBedrockTimeouts.SOCKET_TIMEOUT).isEqualTo(Duration.ofSeconds(20)),
                () -> assertThat(SystemDesignBedrockTimeouts.API_CALL_TIMEOUT).isEqualTo(Duration.ofSeconds(25))
        );
    }

    @Test
    void 소켓_타임아웃은_apiCall_타임아웃보다_짧다() {
        assertThat(SystemDesignBedrockTimeouts.SOCKET_TIMEOUT)
                .isLessThan(SystemDesignBedrockTimeouts.API_CALL_TIMEOUT);
    }

    @Test
    void 재시도_전략은_최대_2회_시도하고_클라이언트_기본값을_쓰지_않는다() {
        RetryStrategy strategy = SystemDesignBedrockTimeouts.retryStrategy();

        assertAll(
                () -> assertThat(SystemDesignBedrockTimeouts.MAX_ATTEMPTS).isEqualTo(2),
                () -> assertThat(strategy.maxAttempts()).isEqualTo(2),
                () -> assertThat(strategy.useClientDefaults()).isFalse()
        );
    }

    @Test
    void 스로틀링_서버_내부_모델_타임아웃_예외는_재시도한다() {
        assertAll(
                () -> assertThat(refreshWith(ThrottlingException.builder().message("throttled").build()).token())
                        .isNotNull(),
                () -> assertThat(refreshWith(InternalServerException.builder().message("internal").build()).token())
                        .isNotNull(),
                () -> assertThat(refreshWith(ModelTimeoutException.builder().message("model timeout").build())
                        .token())
                        .isNotNull()
        );
    }

    // 소켓 타임아웃은 이미 20초를 쓴 뒤라 다시 시도하면 응답이 apiCall 타임아웃(25초)까지 늘어나기만 한다.
    @Test
    void 소켓_타임아웃과_이를_감싼_SdkClientException은_재시도하지_않는다() {
        SdkClientException wrapped = SdkClientException.builder()
                .message("Unable to execute HTTP request: Read timed out")
                .cause(new SocketTimeoutException("Read timed out"))
                .build();

        assertAll(
                () -> assertThatThrownBy(() -> refreshWith(new SocketTimeoutException("Read timed out")))
                        .isInstanceOf(TokenAcquisitionFailedException.class),
                () -> assertThatThrownBy(() -> refreshWith(wrapped))
                        .isInstanceOf(TokenAcquisitionFailedException.class)
        );
    }

    // 동시 실행 상한만큼 연결을 두어, 허가를 받은 호출이 연결을 기다리지 않게 한다.
    @Test
    void 최대_연결_수는_동시_실행_상한과_같다() {
        assertThat(SystemDesignBedrockTimeouts.MAX_CONNECTIONS).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
    }

    private RefreshRetryTokenResponse refreshWith(Throwable failure) {
        RetryStrategy strategy = SystemDesignBedrockTimeouts.retryStrategy();
        AcquireInitialTokenResponse initial = strategy.acquireInitialToken(
                AcquireInitialTokenRequest.create("system-design"));

        return strategy.refreshRetryToken(RefreshRetryTokenRequest.builder()
                .token(initial.token())
                .failure(failure)
                .build());
    }
}
