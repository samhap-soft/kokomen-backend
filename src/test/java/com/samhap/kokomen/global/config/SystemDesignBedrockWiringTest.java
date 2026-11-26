package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.global.fixture.bedrock.BedrockConversePropertiesFixture;
import com.samhap.kokomen.interview.external.SystemDesignAnswerBedrockClient;
import com.samhap.kokomen.interview.external.SystemDesignRequirementBedrockClient;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.AcquireInitialTokenRequest;
import software.amazon.awssdk.retries.api.AcquireInitialTokenResponse;
import software.amazon.awssdk.retries.api.RefreshRetryTokenRequest;
import software.amazon.awssdk.retries.api.RefreshRetryTokenResponse;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.retries.api.TokenAcquisitionFailedException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeServiceClientConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;

/**
 * 시스템 설계 역질문 답(요청 스레드에서 부르는 동기 호출)과 답변 채점(비동기 워커에서 부르는 L2·L3)이 공용 빈(소켓 60초, apiCall 타임아웃
 * 없음, SDK 기본 재시도 최대 4회)이 아니라 각자의 전용 빈에 물리는지 고정한다. 역질문 답은 짧은 타임아웃, 채점은 긴 소켓 타임아웃과
 * 전체 호출 상한을 쓴다. BaseTest는 Bedrock 클라이언트를 목으로 바꾸므로 실제 배선은 ApplicationContextRunner로 필요한 빈만 띄워 본다.
 */
class SystemDesignBedrockWiringTest {

    private static final BedrockConverseProperties PROPERTIES = BedrockConversePropertiesFixture.create();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
            .withPropertyValues(BedrockConversePropertiesFixture.propertyValues(PROPERTIES))
            .withUserConfiguration(AwsConfig.class, TestBeans.class)
            .withBean(SystemDesignRequirementBedrockClient.class)
            .withBean(SystemDesignAnswerBedrockClient.class);

    @Test
    void 컨텍스트가_모호성_없이_기동한다() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 시스템_설계_전용_converse_클라이언트가_공용_빈과_다른_인스턴스다() {
        contextRunner.run(context -> assertThat(context.getBean("systemDesignBedrockConverseClient"))
                .isNotSameAs(context.getBean(BedrockConverseClient.class)));
    }

    @Test
    void 시스템_설계_전용_런타임_클라이언트가_공용_런타임_클라이언트와_다른_인스턴스다() {
        contextRunner.run(context -> assertThat(context.getBean("systemDesignBedrockRuntimeClient"))
                .isNotSameAs(context.getBean("bedrockRuntimeClient"))
                .isNotSameAs(context.getBean("resumeAnalysisBedrockRuntimeClient")));
    }

    @Test
    void 채점_전용_런타임_클라이언트가_다른_런타임_클라이언트와_다른_인스턴스다() {
        contextRunner.run(context -> assertThat(context.getBean("systemDesignAnswerBedrockRuntimeClient"))
                .isNotSameAs(context.getBean("bedrockRuntimeClient"))
                .isNotSameAs(context.getBean("systemDesignBedrockRuntimeClient"))
                .isNotSameAs(context.getBean("resumeAnalysisBedrockRuntimeClient")));
    }

    // 빌드된 클라이언트에서 읽을 수 있는 설정(리전, apiCall 타임아웃, 재시도 전략)이 전용 사양으로 걸렸는지 본다. 이력서 상수를 잘못
    // 넣거나 overrideConfiguration을 빠뜨리면 여기서 드러난다. 연결·소켓 타임아웃과 최대 연결 수는 HTTP 클라이언트 안에 있어 읽을 수 없다.
    @Test
    void 시스템_설계_전용_런타임_클라이언트는_공용과_같은_리전에_전용_타임아웃과_재시도를_쓴다() {
        contextRunner.run(context -> {
            BedrockRuntimeServiceClientConfiguration dedicated = context.getBean(
                    "systemDesignBedrockRuntimeClient", BedrockRuntimeClient.class).serviceClientConfiguration();
            BedrockRuntimeServiceClientConfiguration primary = context.getBean(
                    "bedrockRuntimeClient", BedrockRuntimeClient.class).serviceClientConfiguration();
            ClientOverrideConfiguration override = dedicated.overrideConfiguration();
            assertAll(
                    () -> assertThat(dedicated.region()).isEqualTo(Region.US_EAST_1),
                    () -> assertThat(primary.region()).isEqualTo(Region.US_EAST_1),
                    () -> assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(25)),
                    () -> assertThat(override.retryStrategy()).hasValueSatisfying(strategy -> assertAll(
                            () -> assertThat(strategy.maxAttempts()).isEqualTo(2),
                            () -> assertThat(strategy.useClientDefaults()).isFalse()
                    ))
            );
        });
    }

    // 역질문 답 전용 상수(25초, 재시도 세 예외)를 잘못 넣거나 overrideConfiguration을 빠뜨리면 여기서 드러난다. 재시도 전략은 빌드된
    // 클라이언트에서 꺼낸 객체를 두드려, 역질문 답 전략에는 없는 서비스 불가(503)를 재시도하고 소켓 타임아웃은 재시도하지 않는지 본다.
    @Test
    void 채점_전용_런타임_클라이언트는_공용과_같은_리전에_채점_전용_타임아웃과_재시도를_쓴다() {
        contextRunner.run(context -> {
            BedrockRuntimeServiceClientConfiguration dedicated = context.getBean(
                    "systemDesignAnswerBedrockRuntimeClient", BedrockRuntimeClient.class).serviceClientConfiguration();
            ClientOverrideConfiguration override = dedicated.overrideConfiguration();
            RetryStrategy strategy = override.retryStrategy().orElseThrow();
            assertAll(
                    () -> assertThat(dedicated.region()).isEqualTo(Region.US_EAST_1),
                    () -> assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(85)),
                    () -> assertThat(strategy.maxAttempts()).isEqualTo(2),
                    () -> assertThat(strategy.useClientDefaults()).isFalse(),
                    () -> assertThat(refreshWith(strategy,
                            ServiceUnavailableException.builder().message("unavailable").build()).token()).isNotNull(),
                    () -> assertThatThrownBy(() -> refreshWith(strategy, new SocketTimeoutException("Read timed out")))
                            .isInstanceOf(TokenAcquisitionFailedException.class)
            );
        });
    }

    // 전용 converse 빈의 @Qualifier가 빠져도 두 converse 객체는 여전히 다른 인스턴스라 위 비교는 통과한다.
    // 공용 런타임 클라이언트로 되돌아갔는지는 converse 클라이언트가 들고 있는 런타임 클라이언트를 직접 봐야 드러난다.
    @Test
    void 시스템_설계_전용_converse_클라이언트는_전용_런타임_클라이언트를_내부에_들고있다() {
        contextRunner.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean("systemDesignBedrockConverseClient"), "bedrockRuntimeClient"))
                .isSameAs(context.getBean("systemDesignBedrockRuntimeClient")));
    }

    @Test
    void 채점_전용_converse_클라이언트는_채점_전용_런타임_클라이언트를_내부에_들고있다() {
        contextRunner.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean("systemDesignAnswerBedrockConverseClient"), "bedrockRuntimeClient"))
                .isSameAs(context.getBean("systemDesignAnswerBedrockRuntimeClient")));
    }

    // @Qualifier가 생성자 파라미터에서 빠지면 공용 converse 빈(@Primary)이 조용히 주입된다. BaseTest는 이 클라이언트를 목으로 바꾸므로
    // 일반 테스트로는 드러나지 않는다.
    @Test
    void 요구사항_클라이언트는_시스템_설계_전용_converse_클라이언트를_주입받는다() {
        contextRunner.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean(SystemDesignRequirementBedrockClient.class), "converseClient"))
                .isSameAs(context.getBean("systemDesignBedrockConverseClient")));
    }

    // 답변 채점도 @Qualifier가 빠지면 공용 converse 빈(@Primary)이 조용히 주입된다. 공용 빈은 생성이 60초를 넘으면 같은 채점을 최대
    // 4번 보내 4번 과금한다. 역질문 답 전용 빈을 받으면 apiCall 25초가 출력이 긴 마지막 답변 평가를 끊고 연결 8개를 역질문과 나눠 쓴다.
    @Test
    void 답변_클라이언트는_채점_전용_converse_클라이언트를_주입받는다() {
        contextRunner.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean(SystemDesignAnswerBedrockClient.class), "converseClient"))
                .isSameAs(context.getBean("systemDesignAnswerBedrockConverseClient"))
                .isNotSameAs(context.getBean(BedrockConverseClient.class))
                .isNotSameAs(context.getBean("systemDesignBedrockConverseClient"))
                .isNotSameAs(context.getBean("resumeAnalysisBedrockConverseClient")));
    }

    private static RefreshRetryTokenResponse refreshWith(RetryStrategy strategy, Throwable failure) {
        AcquireInitialTokenResponse initial = strategy.acquireInitialToken(
                AcquireInitialTokenRequest.create("system-design-answer-wiring"));
        return strategy.refreshRetryToken(RefreshRetryTokenRequest.builder()
                .token(initial.token())
                .failure(failure)
                .build());
    }

    /**
     * BedrockConverseClient는 컴포넌트 스캔으로 등록되는 @Primary 빈이라 여기서 같은 조건으로 직접 올린다. @Primary가 있어야
     * 시스템 설계 클라이언트가 @Qualifier 없이도 통과해 버리는 위양성이 생기지 않는다.
     */
    @Configuration
    static class TestBeans {

        @Primary
        @Bean
        BedrockConverseClient bedrockConverseClient(
                BedrockRuntimeClient bedrockRuntimeClient,
                BedrockConverseProperties properties,
                ObjectMapper objectMapper
        ) {
            return new BedrockConverseClient(bedrockRuntimeClient, properties, objectMapper);
        }
    }
}
