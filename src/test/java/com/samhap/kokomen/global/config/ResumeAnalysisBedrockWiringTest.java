package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.resume.external.ResumeAnalysisEvaluationBedrockClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisQuestionBedrockClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * 이력서 분석 Bedrock 클라이언트가 60초 공용 빈이 아니라 360초 전용 빈에 물리는지 고정한다.
 * 여기가 틀어지면 평가 콜이 조용히 다시 소켓 타임아웃으로 실패하고, 그 실패는 배포 후에야 드러난다.
 * BaseTest는 이력서 Bedrock 클라이언트를 목 빈으로 갈아끼우므로 실제 배선을 검증할 수 없어
 * ApplicationContextRunner로 필요한 빈만 띄운다.
 */
class ResumeAnalysisBedrockWiringTest {

    /**
     * AwsConfig의 @EnableConfigurationProperties(BedrockConverseProperties.class)는 TestBeans의
     * bedrockConverseProperties() 빈과 별개로 "aws.bedrock-..." 이름의 BedrockConverseProperties
     * 빈을 하나 더 등록한다. 이 값들이 없으면 그 빈이 @NotBlank/@NotNull 검증에서 컨텍스트 기동 중
     * BindValidationException으로 죽으므로, 실제로 쓰이지 않는 빈이라도 바인딩이 통과하도록 채운다.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withPropertyValues(
                    "aws.bedrock.model-id=test-model-id",
                    "aws.bedrock.proceed-max-tokens=2048",
                    "aws.bedrock.end-max-tokens=2048",
                    "aws.bedrock.answer-feedback-max-tokens=1024",
                    "aws.bedrock.resume-question-max-tokens=2048",
                    "aws.bedrock.resume-evaluation-max-tokens=16000",
                    "aws.bedrock.evaluation-temperature=0.2",
                    "aws.bedrock.generation-temperature=0.7",
                    "aws.bedrock.feedback-temperature=0.5")
            .withUserConfiguration(AwsConfig.class, TestBeans.class)
            .withBean(ResumeAnalysisEvaluationBedrockClient.class)
            .withBean(ResumeAnalysisQuestionBedrockClient.class);

    @Test
    void 컨텍스트가_모호성_없이_기동한다() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 이력서_전용_converse_클라이언트가_공용_빈과_다른_인스턴스다() {
        contextRunner.run(context -> {
            BedrockConverseClient primary = context.getBean(BedrockConverseClient.class);
            BedrockConverseClient resumeDedicated =
                    (BedrockConverseClient) context.getBean("resumeAnalysisBedrockConverseClient");

            assertThat(resumeDedicated).isNotSameAs(primary);
        });
    }

    @Test
    void 두_이력서_Bedrock_클라이언트는_전용_converse_클라이언트를_주입받는다() {
        contextRunner.run(context -> {
            Object resumeDedicated = context.getBean("resumeAnalysisBedrockConverseClient");

            assertThat(ReflectionTestUtils.getField(
                    context.getBean(ResumeAnalysisEvaluationBedrockClient.class), "converseClient"))
                    .isSameAs(resumeDedicated);
            assertThat(ReflectionTestUtils.getField(
                    context.getBean(ResumeAnalysisQuestionBedrockClient.class), "converseClient"))
                    .isSameAs(resumeDedicated);
        });
    }

    @Test
    void 전용_런타임_클라이언트가_공용_런타임_클라이언트와_다른_인스턴스다() {
        contextRunner.run(context -> assertThat(context.getBean("resumeAnalysisBedrockRuntimeClient"))
                .isNotSameAs(context.getBean("bedrockRuntimeClient")));
    }

    /**
     * 위 인스턴스 비교만으로는 부족하다: resumeAnalysisBedrockConverseClient의 @Qualifier가 지워져도
     * 두 BedrockConverseClient 객체 자체는 여전히 서로 다른 인스턴스이므로 그 검증은 통과해버린다.
     * 실제로 60초 공용 런타임 클라이언트로 되돌아갔는지는 converse 클라이언트가 내부에 들고 있는
     * bedrockRuntimeClient 필드가 전용 런타임 빈을 가리키는지를 직접 봐야 드러난다.
     */
    @Test
    void 이력서_전용_converse_클라이언트는_전용_런타임_클라이언트를_내부에_들고있다() {
        contextRunner.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean("resumeAnalysisBedrockConverseClient"), "bedrockRuntimeClient"))
                .isSameAs(context.getBean("resumeAnalysisBedrockRuntimeClient")));
    }

    /**
     * BedrockConverseClient는 @Component라 컴포넌트 스캔 없이는 등록되지 않으므로 여기서 직접 빈으로
     * 올린다. @Primary를 붙여 프로덕션의 컴포넌트와 같은 조건을 만든다 — 그래야 이력서 클라이언트가
     * @Qualifier 없이도 통과해버리는 위양성이 생기지 않는다.
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

        /**
         * AwsConfig의 @EnableConfigurationProperties(BedrockConverseProperties.class)가 별도 이름
         * (aws.bedrock-...)으로 같은 타입의 빈을 하나 더 등록해 버려서, 이 빈이 @Primary 없이는
         * NoUniqueBeanDefinitionException으로 컨텍스트가 뜨지 못한다. 이 클래스가 검증하는 대상은
         * BedrockConverseProperties 바인딩이 아니라 BedrockRuntimeClient/BedrockConverseClient
         * 배선이므로, 여기서 @Primary로 모호성만 해소한다.
         */
        @Primary
        @Bean
        BedrockConverseProperties bedrockConverseProperties() {
            return new BedrockConverseProperties(
                    "test-model-id", 2048, 2048, 1024, 2048, 16000, 0.2f, 0.7f, 0.5f);
        }
    }
}
