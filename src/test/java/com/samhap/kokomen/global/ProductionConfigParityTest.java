package com.samhap.kokomen.global;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

// 테스트가 운영 설정을 가리지 않고 그대로 따르는지의 회귀 가드다.
// src/test/resources에 운영과 같은 이름의 application.yml을 두면 클래스패스에서 운영 파일이 통째로 가려진다.
// 그러면 운영이 끈 OSIV가 테스트에서는 기본값(켜짐)으로 돌고, 운영 설정을 바꿔도 모든 테스트가 그대로 통과한다.
// 운영 파일은 클래스패스가 아니라 소스 경로로 읽는다. 클래스패스로 읽으면 운영 파일을 가린 테스트 파일을 읽게 된다.
class ProductionConfigParityTest extends BaseTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final List<String> PRODUCTION_CONFIG_FILES = List.of("application.yml", "application-common.yml");
    private static final String PROFILE_ACTIVATION_KEY = "spring.config.activate.on-profile";

    // 테스트가 의도적으로 운영과 다르게 두는 키와 그 이유다. 여기 없는 운영 공통 키는 테스트에서도 운영과 같아야 한다.
    private static final Map<String, String> INTENTIONAL_OVERRIDES = Map.of(
            "supertone.api-token", "운영은 환경 변수로 주입하는 비밀키다",
            "aws.bedrock.model-id", "실수로 실제 Bedrock을 호출해도 요청이 거절되도록 존재하지 않는 모델 ID를 쓴다",
            "spring.jpa.properties.hibernate.show_sql", "SQL 로그 출력 여부만 바꾼다"
    );

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 운영_공통_설정은_테스트에서도_같은_값으로_적용된다() throws IOException {
        Map<String, String> mismatches = new TreeMap<>();
        readProductionCommonProperties().forEach((key, productionValue) -> {
            String testValue = environment.getProperty(key);
            if (!INTENTIONAL_OVERRIDES.containsKey(key) && !productionValue.equals(testValue)) {
                mismatches.put(key, "운영=" + productionValue + ", 테스트=" + testValue);
            }
        });

        assertThat(mismatches).isEmpty();
    }

    // 운영은 OSIV를 끈다. 테스트에서 켜져 있으면 트랜잭션 밖 지연 로딩이 컨트롤러 테스트에서는 성공하고 운영에서만 터진다.
    @Test
    void 테스트에서도_운영처럼_OSIV가_꺼져_있다() {
        assertThat(applicationContext.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    // 운영은 spring.jpa.database: mysql로 MySQLDialect를 쓴다. 테스트만 다른 방언을 쓰면 생성 SQL이 운영과 갈린다.
    @Test
    void 테스트도_운영과_같은_DB_방언을_쓴다() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);

        assertThat(sessionFactory.getJdbcServices().getDialect()).isExactlyInstanceOf(MySQLDialect.class);
    }

    // 프로파일 전용 문서(spring.config.activate.on-profile)를 빼고, 모든 환경에 공통으로 적용되는 운영 설정만 모은다.
    private Map<String, String> readProductionCommonProperties() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        Map<String, String> properties = new TreeMap<>();
        for (String fileName : PRODUCTION_CONFIG_FILES) {
            FileSystemResource resource = new FileSystemResource(MAIN_RESOURCES.resolve(fileName));
            for (PropertySource<?> document : loader.load(fileName, resource)) {
                EnumerablePropertySource<?> source = (EnumerablePropertySource<?>) document;
                if (source.containsProperty(PROFILE_ACTIVATION_KEY)) {
                    continue;
                }
                for (String key : source.getPropertyNames()) {
                    properties.put(key, String.valueOf(source.getProperty(key)));
                }
            }
        }
        return properties;
    }
}
