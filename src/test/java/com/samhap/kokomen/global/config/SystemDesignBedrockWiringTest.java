package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.global.fixture.bedrock.BedrockConversePropertiesFixture;
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
 * 시스템 설계 역질문 답(요청 스레드에서 부르는 동기 호출)이 공용 빈(소켓 60초, apiCall 타임아웃 없음, SDK 기본 재시도)이 아니라
 * 짧은 타임아웃의 전용 빈에 물리는지 고정한다. BaseTest는 Bedrock 클라이언트를 목으로 바꾸므로 실제 배선은
 * ApplicationContextRunner로 필요한 빈만 띄워 본다.
 */
class SystemDesignBedrockWiringTest {

    private static final BedrockConverseProperties PROPERTIES = BedrockConversePropertiesFixture.create();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withPropertyValues(BedrockConversePropertiesFixture.propertyValues(PROPERTIES))
            .withUserConfiguration(AwsConfig.class, TestBeans.class);

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

    // 전용 converse 빈의 @Qualifier가 빠져도 두 converse 객체는 여전히 다른 인스턴스라 위 비교는 통과한다.
    // 공용 런타임 클라이언트로 되돌아갔는지는 converse 클라이언트가 들고 있는 런타임 클라이언트를 직접 봐야 드러난다.
    @Test
    void 시스템_설계_전용_converse_클라이언트는_전용_런타임_클라이언트를_내부에_들고있다() {
        contextRunner.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean("systemDesignBedrockConverseClient"), "bedrockRuntimeClient"))
                .isSameAs(context.getBean("systemDesignBedrockRuntimeClient")));
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
