package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 배포가 컨테이너를 멈출 때 주는 유예(SIGTERM 뒤 SIGKILL까지)가 짧으면, 채점 실행기가 진행 중인 채점을 기다리는 도중에 컨테이너가 죽어
 * 기다림이 소용없다. 채점은 늦어도 웹 서버의 우아한 종료 단계(spring.lifecycle.timeout-per-shutdown-phase까지) 안에 맡겨지고, 맡긴 때부터
 * 전체 호출 상한 + 저장 안에 끝난다. 실행기의 종료 대기는 그보다 길다. 그래서 유예를 종료 단계 상한과 실행기 대기를 더한 값보다 길게 두면
 * 진행 중인 채점이 끝난다(앱 전체 종료는 이력서 분석이 돌고 있으면 더 걸릴 수 있다). 유예를 정하는 배포 파일이 이 값과 함께 움직이도록
 * 고정한다.
 * dev는 배포 워크플로가 docker rm -f(SIGKILL)로 멈춰 compose의 유예가 적용되지 않지만, 수동 재시작·compose 재생성에는 적용된다.
 */
class ShutdownGracePeriodParityTest {

    private static final Path APPLICATION_YML = Path.of("src", "main", "resources", "application.yml");
    private static final Path PROD_DEPLOY_SCRIPT = Path.of("docker", "prod", "api", "deploy.sh");
    private static final Path PROD_ROLLBACK_SCRIPT = Path.of("docker", "prod", "api", "rollback.sh");
    private static final Path PROD_COMPOSE = Path.of("docker", "prod", "api", "docker-compose-prod.yml");
    private static final Path DEV_COMPOSE = Path.of("docker", "dev", "docker-compose-dev.yml");
    private static final Pattern GRACE_VARIABLE = Pattern.compile("(?m)^GRACEFUL_SHUTDOWN_WAIT=(\\d+)$");
    private static final Pattern DOCKER_STOP_TIMEOUT = Pattern.compile("docker stop -t (\\S+)");
    private static final String GRACE_VARIABLE_REFERENCE = "$GRACEFUL_SHUTDOWN_WAIT";

    @Test
    void 배포_스크립트의_종료_유예는_웹_서버_종료_단계와_채점_실행기_종료_대기를_더한_것보다_길다() throws IOException {
        Duration required = requiredGracePeriod();

        assertAll(
                () -> assertThat(stopTimeoutOf(PROD_DEPLOY_SCRIPT)).isGreaterThan(required),
                () -> assertThat(stopTimeoutOf(PROD_ROLLBACK_SCRIPT)).isGreaterThan(required)
        );
    }

    @Test
    void compose의_API_컨테이너_종료_유예는_웹_서버_종료_단계와_채점_실행기_종료_대기를_더한_것보다_길다() throws IOException {
        Duration required = requiredGracePeriod();

        assertAll(
                () -> assertThat(stopGracePeriodOf(PROD_COMPOSE, "kokomen-api-blue")).isGreaterThan(required),
                () -> assertThat(stopGracePeriodOf(PROD_COMPOSE, "kokomen-api-green")).isGreaterThan(required),
                () -> assertThat(stopGracePeriodOf(DEV_COMPOSE, "kokomen-interview-api-dev")).isGreaterThan(required)
        );
    }

    private static Duration requiredGracePeriod() throws IOException {
        Duration lifecyclePhase = DurationStyle.detectAndParse(
                readYamlValue(APPLICATION_YML, "spring.lifecycle.timeout-per-shutdown-phase"));
        ThreadPoolTaskExecutor executor = new AsyncConfig().bedrockFlowCallbackExecutor();
        try {
            return lifecyclePhase.plusMillis((Long) ReflectionTestUtils.getField(executor, "awaitTerminationMillis"));
        } finally {
            executor.shutdown();
        }
    }

    // docker stop -t는 모두 GRACEFUL_SHUTDOWN_WAIT 변수를 써야 한다. 숫자를 바로 쓴 곳이 남으면 그 경로만 짧은 유예로 멈춘다.
    private static Duration stopTimeoutOf(Path script) throws IOException {
        String content = Files.readString(script);
        Matcher stop = DOCKER_STOP_TIMEOUT.matcher(content);
        int stopCount = 0;
        while (stop.find()) {
            assertThat(stop.group(1)).as("%s의 docker stop -t", script).isEqualTo(GRACE_VARIABLE_REFERENCE);
            stopCount++;
        }
        assertThat(stopCount).as("%s의 docker stop 개수", script).isPositive();
        Matcher variable = GRACE_VARIABLE.matcher(content);
        assertThat(variable.find()).as("%s의 GRACEFUL_SHUTDOWN_WAIT", script).isTrue();
        return Duration.ofSeconds(Long.parseLong(variable.group(1)));
    }

    private static Duration stopGracePeriodOf(Path compose, String service) throws IOException {
        return DurationStyle.detectAndParse(readYamlValue(compose, "services." + service + ".stop_grace_period"));
    }

    private static String readYamlValue(Path yaml, String key) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(yaml.toString(),
                new FileSystemResource(yaml));
        return sources.stream()
                .map(source -> source.getProperty(key))
                .filter(value -> value != null)
                .map(String::valueOf)
                .findFirst()
                .orElseThrow(() -> new AssertionError(yaml + "에 " + key + "가 없습니다."));
    }
}
