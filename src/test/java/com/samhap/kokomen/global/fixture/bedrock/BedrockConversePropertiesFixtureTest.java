package com.samhap.kokomen.global.fixture.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class BedrockConversePropertiesFixtureTest {

    private static final Path PRODUCTION_APPLICATION_YML = Path.of("src", "main", "resources", "application.yml");

    // 키 목록이 컴포넌트 하나라도 빠뜨리면 @NotNull 검증에서 기동이 실패하고, 값이 어긋나면 비교에서 드러난다.
    @Test
    void 키_목록으로_바인딩한_값이_create와_같다() {
        BedrockConverseProperties expected = BedrockConversePropertiesFixture.create();

        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(BedrockConversePropertiesFixture.propertyValues(expected))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BedrockConverseProperties.class)).isEqualTo(expected);
                });
    }

    // 운영 파일은 클래스패스가 아니라 소스 경로로 읽는다. 테스트 리소스가 같은 이름으로 가려도 운영 값과 비교하게 한다.
    @Test
    void create는_운영_application_yml과_같은_값이다() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader().load("application.yml",
                new FileSystemResource(PRODUCTION_APPLICATION_YML));

        BedrockConverseProperties production = new Binder(ConfigurationPropertySources.from(documents))
                .bind("aws.bedrock", BedrockConverseProperties.class)
                .get();

        assertThat(BedrockConversePropertiesFixture.create()).isEqualTo(production);
    }

    @Configuration
    @EnableConfigurationProperties(BedrockConverseProperties.class)
    static class PropertiesConfiguration {
    }
}
