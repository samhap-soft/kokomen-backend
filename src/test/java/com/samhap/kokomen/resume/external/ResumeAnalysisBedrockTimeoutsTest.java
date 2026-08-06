package com.samhap.kokomen.resume.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 이력서 분석 전용 Bedrock 클라이언트의 타임아웃 상수와 재시도 전략을 검증한다.
 * 재시도 전략 검증이 이 설계의 핵심이다 — useClientDefaults(false)가 SDK 기본 재시도 조건을
 * 실제로 차단해야만 소켓 타임아웃이 재시도 대상에서 빠진다.
 */
class ResumeAnalysisBedrockTimeoutsTest {

    @Test
    void 타임아웃_상수는_커넥트_3초_소켓_360초_api콜_390초다() {
        assertThat(ResumeAnalysisBedrockTimeouts.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3));
        assertThat(ResumeAnalysisBedrockTimeouts.SOCKET_TIMEOUT).isEqualTo(Duration.ofSeconds(360));
        assertThat(ResumeAnalysisBedrockTimeouts.API_CALL_TIMEOUT).isEqualTo(Duration.ofSeconds(390));
        assertThat(ResumeAnalysisBedrockTimeouts.MAX_CONNECTIONS).isEqualTo(60);
    }

    @Test
    void 소켓_타임아웃은_apiCall_타임아웃보다_짧다() {
        assertThat(ResumeAnalysisBedrockTimeouts.SOCKET_TIMEOUT)
                .isLessThan(ResumeAnalysisBedrockTimeouts.API_CALL_TIMEOUT);
    }

    @Test
    void 재시도_전략은_최대_2회_시도하고_클라이언트_기본값을_쓰지_않는다() {
        RetryStrategy strategy = ResumeAnalysisBedrockTimeouts.retryStrategy();

        assertThat(strategy.maxAttempts()).isEqualTo(2);
        assertThat(strategy.useClientDefaults()).isFalse();
    }

    @Test
    void 스로틀링_예외는_재시도한다() {
        RefreshRetryTokenResponse refreshed = refreshWith(
                ThrottlingException.builder().message("throttled").build());

        assertThat(refreshed.token()).isNotNull();
    }

    @Test
    void 서버_내부_예외는_재시도한다() {
        RefreshRetryTokenResponse refreshed = refreshWith(
                InternalServerException.builder().message("internal").build());

        assertThat(refreshed.token()).isNotNull();
    }

    @Test
    void 모델_타임아웃_예외는_재시도한다() {
        RefreshRetryTokenResponse refreshed = refreshWith(
                ModelTimeoutException.builder().message("model timeout").build());

        assertThat(refreshed.token()).isNotNull();
    }

    @Test
    void 소켓_타임아웃은_재시도하지_않는다() {
        assertThatThrownBy(() -> refreshWith(new SocketTimeoutException("Read timed out")))
                .isInstanceOf(TokenAcquisitionFailedException.class);
    }

    @Test
    void 소켓_타임아웃을_감싼_SdkClientException도_재시도하지_않는다() {
        SdkClientException wrapped = SdkClientException.builder()
                .message("Unable to execute HTTP request: Read timed out")
                .cause(new SocketTimeoutException("Read timed out"))
                .build();

        assertThatThrownBy(() -> refreshWith(wrapped))
                .isInstanceOf(TokenAcquisitionFailedException.class);
    }

    private RefreshRetryTokenResponse refreshWith(Throwable failure) {
        RetryStrategy strategy = ResumeAnalysisBedrockTimeouts.retryStrategy();
        AcquireInitialTokenResponse initial = strategy.acquireInitialToken(
                AcquireInitialTokenRequest.create("resume-analysis"));

        return strategy.refreshRetryToken(RefreshRetryTokenRequest.builder()
                .token(initial.token())
                .failure(failure)
                .build());
    }
}
