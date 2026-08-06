# 이력서 분석 Bedrock 타임아웃 해소 + GPT 폴백 제거 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이력서 분석 평가 Bedrock 호출이 소켓 타임아웃으로 100% 실패하는 문제를 전용 클라이언트 빈으로 해소하고, 더는 필요 없는 GPT 폴백 경로를 제거한다.

**Architecture:** 이력서 분석 전용 `BedrockRuntimeClient`/`BedrockConverseClient` 빈 쌍을 추가해 긴 타임아웃(360초)과 좁은 재시도(스로틀링·5xx만 1회)를 부여하고, 인터뷰용 공용 빈의 60초 빠른 실패 특성은 `@Primary`로 보존한다. `maxTokens`를 16,000으로 올려 잘림을 막고, GPT 폴백 파일 10개와 `BEDROCK_UNHEALTHY` ThreadLocal을 제거해 Bedrock 단일 경로로 만든다.

**Tech Stack:** Java 17, Spring Boot 3.5.3, AWS SDK for Java v2 (BOM 2.31.69, apache-client 2.32.12), JUnit 5, Mockito, AssertJ

## Global Constraints

- 컬럼 제한 **120자**, 인덴트 4칸, 연속 인덴트 +8칸 (`docs/convention.md`)
- 테스트 메서드명은 **한국어**, `@DisplayName` 사용 금지
- 어노테이션 순서: Lombok → Spring (중요한 것이 아래)
- 메서드 선언 순서: 생성자 → 정적 팩터리 → 비즈니스 메서드(private은 호출하는 public 뒤) → Override
- 타임아웃 값: `CONNECT_TIMEOUT` 3초, `SOCKET_TIMEOUT` **360초**, `API_CALL_TIMEOUT` **390초**, `MAX_CONNECTIONS` 60, `MAX_ATTEMPTS` **2**
- `resume-evaluation-max-tokens`: **16000**
- 재시도 대상 예외 정확히 3개: `ThrottlingException`, `InternalServerException`, `ModelTimeoutException` (모두 `software.amazon.awssdk.services.bedrockruntime.model`)
- 테스트 실행 전 `docker compose -f test.yml up -d` 필요 (MySQL 13306, Redis 16379). Task 1·2는 Docker 불필요한 순수 단위 테스트다.
- 스펙: `docs/superpowers/specs/2026-08-07-bedrock-timeout-and-gpt-fallback-removal-design.md`

---

### Task 1: `ResumeAnalysisBedrockTimeouts` — 타임아웃 상수와 재시도 전략

빌드된 `BedrockRuntimeClient`는 `socketTimeout`·`apiCallTimeout`을 읽을 수 없어 설정값을 테스트로 고정할 수 없다. 삭제 예정인 `ResumeAnalysisGptTimeouts`와 같은 패턴으로 상수 클래스를 만들어 `AwsConfig`가 참조하게 하고, 재시도 전략은 정적 팩터리로 노출해 테스트가 전략 객체를 직접 검증할 수 있게 한다.

**Files:**
- Create: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisBedrockTimeouts.java`
- Test: `src/test/java/com/samhap/kokomen/resume/external/ResumeAnalysisBedrockTimeoutsTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `public static final Duration CONNECT_TIMEOUT` = 3초
  - `public static final Duration SOCKET_TIMEOUT` = 360초
  - `public static final Duration API_CALL_TIMEOUT` = 390초
  - `public static final int MAX_CONNECTIONS` = 60
  - `public static final int MAX_ATTEMPTS` = 2
  - `public static StandardRetryStrategy retryStrategy()`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/samhap/kokomen/resume/external/ResumeAnalysisBedrockTimeoutsTest.java`:

```java
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
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.samhap.kokomen.resume.external.ResumeAnalysisBedrockTimeoutsTest"`

Expected: 컴파일 실패 — `ResumeAnalysisBedrockTimeouts` 심볼을 찾을 수 없음

- [ ] **Step 3: 최소 구현 작성**

`src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisBedrockTimeouts.java`:

```java
package com.samhap.kokomen.resume.external;

import java.time.Duration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/**
 * 이력서 분석 전용 Bedrock 클라이언트의 타임아웃·재시도 사양.
 *
 * <p>평가 콜은 실측 62~64 tok/s로 최대 16,000 토큰을 생성해 약 258초가 걸린다. 공용 클라이언트의
 * 소켓 타임아웃 60초로는 매 시도가 끊기므로 360초를 준다(258초 대비 여유 40%). API_CALL_TIMEOUT 390초는
 * ResumeAnalysisRecoveryScheduler.STALE_THRESHOLD(10분) 안에 들어가므로, 아직 실행 중인 행이 스윕에
 * STALE_SWEEP으로 종단되는 경로가 열리지 않는다.
 *
 * <p>재시도는 StandardRetryStrategy.builder()로 빈 전략에서 시작한다. RetryStrategy.Builder의
 * retryOnException은 기존 조건에 더해지는 성질이라, SDK 기본 전략을 손봐서는 소켓 타임아웃을 재시도
 * 대상에서 뺄 수 없다. useClientDefaults(false)로 기본 조건 유입을 막고 아래 세 예외만 명시한다.
 * 소켓 타임아웃 재시도는 258초 생산을 통째로 다시 하는 낭비이고, 폐기된 응답이 그대로 과금된다.
 * 반면 세 예외는 생산 없이 즉시 오는 실패라 재시도가 싸다.
 *
 * <p>빌드된 BedrockRuntimeClient는 타임아웃 설정을 읽을 수 없어 상수로 분리했다. 이 상수는 의도값을
 * 고정할 뿐이고 SDK가 값을 지키는지는 배포 후 CloudWatch InvocationLatency로 검증한다.
 */
public final class ResumeAnalysisBedrockTimeouts {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(360);
    public static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(390);
    public static final int MAX_CONNECTIONS = 60;
    public static final int MAX_ATTEMPTS = 2;

    private static final Duration BACKOFF_BASE = Duration.ofSeconds(1);
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(5);

    private ResumeAnalysisBedrockTimeouts() {
    }

    public static StandardRetryStrategy retryStrategy() {
        return StandardRetryStrategy.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .useClientDefaults(false)
                .retryOnExceptionOrCauseInstanceOf(ThrottlingException.class)
                .retryOnExceptionOrCauseInstanceOf(InternalServerException.class)
                .retryOnExceptionOrCauseInstanceOf(ModelTimeoutException.class)
                .backoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(BACKOFF_BASE, BACKOFF_MAX))
                .throttlingBackoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(BACKOFF_BASE, BACKOFF_MAX))
                .build();
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `./gradlew test --tests "com.samhap.kokomen.resume.external.ResumeAnalysisBedrockTimeoutsTest"`

Expected: 8개 테스트 PASS

`build()`가 `throttlingBackoffStrategy` 미설정으로 NPE를 던지면 위 코드에 이미 설정돼 있으니 그대로 통과한다. 반대로 `circuitBreakerEnabled` 미설정으로 실패하면 `.circuitBreakerEnabled(false)`를 추가한다 — 재시도가 1회뿐이라 서킷 브레이커가 의미 없다.

- [ ] **Step 5: 가드가 동어반복이 아님을 검증**

`ResumeAnalysisBedrockTimeouts.retryStrategy()`에서 `.useClientDefaults(false)` 한 줄을 임시로 지우고 다시 실행한다.

Run: `./gradlew test --tests "com.samhap.kokomen.resume.external.ResumeAnalysisBedrockTimeoutsTest"`

Expected: `재시도_전략은_최대_2회_시도하고_클라이언트_기본값을_쓰지_않는다`가 FAIL. 그리고 `소켓_타임아웃은_재시도하지_않는다` 또는 `소켓_타임아웃을_감싼_SdkClientException도_재시도하지_않는다` 중 최소 하나가 FAIL(기본 조건이 유입되어 재시도가 허용됨).

두 소켓 테스트가 모두 통과해버리면 `useClientDefaults`가 이 SDK 버전에서 재시도 조건에 영향을 주지 않는다는 뜻이다. 그렇다면 두 소켓 테스트는 `retryOnExceptionOrCauseInstanceOf` 세 줄에만 의존하는 가드이므로, 세 줄 중 하나를 지웠을 때 해당 예외 테스트가 FAIL하는 것으로 대체 검증하고, `useClientDefaults` 단정은 그대로 남긴다(문서화 가치).

확인 후 지운 줄을 **반드시 복원**한다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisBedrockTimeouts.java \
        src/test/java/com/samhap/kokomen/resume/external/ResumeAnalysisBedrockTimeoutsTest.java
git commit -m "feat: 이력서 분석 전용 Bedrock 타임아웃 상수와 재시도 전략 추가"
```

---

### Task 2: 이력서 분석 전용 Bedrock 클라이언트 빈 배선

`BedrockConverseClient`가 인터뷰·이력서 공용이므로, 전용 런타임 클라이언트를 물린 두 번째 `BedrockConverseClient` 빈을 만들고 두 이력서 Bedrock 클라이언트가 `@Qualifier`로 그것을 받는다.

**스펙 대비 보정 1건**: 스펙은 "`BedrockConverseClient` 자체 코드는 변경하지 않는다"고 했으나, 같은 타입 빈이 2개가 되면 인터뷰 클라이언트의 무자격 주입이 모호해진다. 그래서 `BedrockConverseClient`에 `@Primary` 어노테이션 한 줄을 추가한다. 로직 변경은 없다.

**Files:**
- Modify: `src/main/java/com/samhap/kokomen/global/config/AwsConfig.java:18-30`
- Modify: `src/main/java/com/samhap/kokomen/global/external/bedrock/BedrockConverseClient.java:23-25` (`@Primary` 추가)
- Modify: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisEvaluationBedrockClient.java:24-30`
- Modify: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisQuestionBedrockClient.java:24-30`
- Test: `src/test/java/com/samhap/kokomen/global/config/ResumeAnalysisBedrockWiringTest.java`

**Interfaces:**
- Consumes: Task 1의 `ResumeAnalysisBedrockTimeouts.CONNECT_TIMEOUT`, `.SOCKET_TIMEOUT`, `.API_CALL_TIMEOUT`, `.MAX_CONNECTIONS`, `.retryStrategy()`
- Produces:
  - 빈 이름 `resumeAnalysisBedrockRuntimeClient` (타입 `BedrockRuntimeClient`)
  - 빈 이름 `resumeAnalysisBedrockConverseClient` (타입 `BedrockConverseClient`)
  - `bedrockRuntimeClient`와 `BedrockConverseClient` 컴포넌트가 `@Primary`

- [ ] **Step 1: 실패하는 테스트 작성**

`ApplicationContextRunner`를 쓴다. Docker 없이 돌고, `InstanceProfileCredentialsProvider`는 지연 초기화라 IMDS를 호출하지 않는다.

`src/test/java/com/samhap/kokomen/global/config/ResumeAnalysisBedrockWiringTest.java`:

```java
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 이력서 분석 Bedrock 클라이언트가 60초 공용 빈이 아니라 360초 전용 빈에 물리는지 고정한다.
 * 여기가 틀어지면 평가 콜이 조용히 다시 소켓 타임아웃으로 실패하고, 그 실패는 배포 후에야 드러난다.
 * BaseTest는 이력서 Bedrock 클라이언트를 목 빈으로 갈아끼우므로 실제 배선을 검증할 수 없어
 * ApplicationContextRunner로 필요한 빈만 띄운다.
 */
class ResumeAnalysisBedrockWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
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

        @Bean
        BedrockConverseProperties bedrockConverseProperties() {
            return new BedrockConverseProperties(
                    "test-model-id", 2048, 2048, 1024, 2048, 16000, 0.2f, 0.7f, 0.5f);
        }
    }
}
```

`bedrockConverseClient`의 `bedrockRuntimeClient` 파라미터는 `AwsConfig`의 `@Primary bedrockRuntimeClient`로
해소된다. `ObjectMapper`는 `JacksonAutoConfiguration`이 제공한다. 추가 import:
`org.springframework.context.annotation.Primary`,
`software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient`.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.samhap.kokomen.global.config.ResumeAnalysisBedrockWiringTest"`

Expected: `resumeAnalysisBedrockConverseClient` / `resumeAnalysisBedrockRuntimeClient` 빈이 없어
`NoSuchBeanDefinitionException`으로 FAIL

- [ ] **Step 3: `AwsConfig`에 전용 빈 2개 추가**

`src/main/java/com/samhap/kokomen/global/config/AwsConfig.java`의 `bedrockRuntimeClient()`에 `@Primary`를 붙이고, 기존 하드코딩된 타임아웃 뒤에 전용 빈 2개를 추가한다:

```java
    @Primary
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(60)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(java.time.Duration.ofSeconds(3))
                        .socketTimeout(java.time.Duration.ofSeconds(60))
                )
                .region(Region.US_EAST_1)
                .build();
    }

    /**
     * 이력서 분석 전용 클라이언트. 평가 콜이 최대 16,000 토큰을 생성해 약 258초가 걸리므로 공용 빈의
     * 소켓 타임아웃 60초로는 완주할 수 없다. 공용 빈을 올리면 인터뷰 호출도 장애 시 6분을 붙잡게 되어
     * 빠른 실패 특성을 잃으므로 분리한다. maxConnections 60은 resumeAnalysisExecutor의
     * corePoolSize·maxPoolSize와 같은 값이며, 인터뷰 풀과 분리되어야 서로 고갈시키지 않는다.
     */
    @Bean
    public BedrockRuntimeClient resumeAnalysisBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(ResumeAnalysisBedrockTimeouts.MAX_CONNECTIONS)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(ResumeAnalysisBedrockTimeouts.CONNECT_TIMEOUT)
                        .socketTimeout(ResumeAnalysisBedrockTimeouts.SOCKET_TIMEOUT)
                )
                .overrideConfiguration(override -> override
                        .apiCallTimeout(ResumeAnalysisBedrockTimeouts.API_CALL_TIMEOUT)
                        .retryStrategy(ResumeAnalysisBedrockTimeouts.retryStrategy()))
                .region(Region.US_EAST_1)
                .build();
    }

    @Bean
    public BedrockConverseClient resumeAnalysisBedrockConverseClient(
            @Qualifier("resumeAnalysisBedrockRuntimeClient") BedrockRuntimeClient bedrockRuntimeClient,
            BedrockConverseProperties properties,
            ObjectMapper objectMapper
    ) {
        return new BedrockConverseClient(bedrockRuntimeClient, properties, objectMapper);
    }
```

추가할 import: `com.fasterxml.jackson.databind.ObjectMapper`,
`com.samhap.kokomen.global.external.bedrock.BedrockConverseClient`,
`com.samhap.kokomen.resume.external.ResumeAnalysisBedrockTimeouts`,
`org.springframework.beans.factory.annotation.Qualifier`,
`org.springframework.context.annotation.Primary`

- [ ] **Step 4: `BedrockConverseClient`에 `@Primary` 추가**

`src/main/java/com/samhap/kokomen/global/external/bedrock/BedrockConverseClient.java`의 클래스 어노테이션을
다음으로 바꾼다(`import org.springframework.context.annotation.Primary;` 추가):

```java
@Slf4j
@Primary
@Component
public class BedrockConverseClient {
```

같은 타입 빈이 2개가 되므로 인터뷰 클라이언트의 무자격 주입을 이 어노테이션이 해소한다.

- [ ] **Step 5: 두 이력서 Bedrock 클라이언트에 `@Qualifier` 부여**

`ResumeAnalysisEvaluationBedrockClient` 생성자:

```java
    public ResumeAnalysisEvaluationBedrockClient(
            @Qualifier("resumeAnalysisBedrockConverseClient") BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }
```

`ResumeAnalysisQuestionBedrockClient` 생성자도 같은 형태로 바꾼다. 두 파일 모두
`import org.springframework.beans.factory.annotation.Qualifier;`를 추가한다.

- [ ] **Step 6: 테스트가 통과하는지 확인**

Run: `./gradlew test --tests "com.samhap.kokomen.global.config.ResumeAnalysisBedrockWiringTest"`

Expected: 4개 테스트 PASS

- [ ] **Step 7: 기존 테스트 회귀 확인**

Run: `docker compose -f test.yml up -d && ./gradlew test`

Expected: 전체 PASS. 실패하면 대개 빈 모호성이므로 `@Primary`/`@Qualifier` 누락 지점을 찾는다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/samhap/kokomen/global/config/AwsConfig.java \
        src/main/java/com/samhap/kokomen/global/external/bedrock/BedrockConverseClient.java \
        src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisEvaluationBedrockClient.java \
        src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisQuestionBedrockClient.java \
        src/test/java/com/samhap/kokomen/global/config/ResumeAnalysisBedrockWiringTest.java
git commit -m "fix: 이력서 분석 Bedrock 호출을 360초 전용 클라이언트로 분리"
```

---

### Task 3: 평가 maxTokens 16,000으로 상향

관측 출력 토큰 중앙값이 ~9,500이고 7건 중 1건이 정확히 cap 10,000에 걸렸다. cap에 걸리면
`stopReason=MAX_TOKENS`로 `extractToolUse`가 실패하므로 타임아웃만 늘려서는 부족하다.

**Files:**
- Modify: `src/main/resources/application.yml:50` (`resume-evaluation-max-tokens`)
- Modify: `src/test/resources/application.yml:51` (같은 키 — 테스트 리소스가 main을 완전히 덮으므로 함께 바꿔야 실제 값이 테스트에 반영된다)
- Modify: `src/test/java/com/samhap/kokomen/resume/external/ResumeAnalysisWiringTest.java:52`

**Interfaces:**
- Consumes: 없음
- Produces: `BedrockConverseProperties.resumeEvaluationMaxTokens()` = 16000

- [ ] **Step 1: 테스트를 새 기대값으로 바꿔 실패시키기**

`ResumeAnalysisWiringTest`의 상수와 테스트 이름을 바꾼다.

```java
    private static final int EVALUATION_MAX_TOKENS = 16000;
```

```java
    @Test
    void 평가_콜은_temperature_0점2와_maxTokens_16000으로_호출된다() {
```

메서드 본문은 그대로 둔다 — 이미 `EVALUATION_MAX_TOKENS` 상수를 참조한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.samhap.kokomen.resume.external.ResumeAnalysisWiringTest"`

Expected: 이 테스트는 지역 상수로 `BedrockConverseProperties`를 만들므로 그대로 PASS한다.
설정 파일 반영을 확인하려면 다음 스텝의 통합 테스트로 넘어간다. 이 스텝의 목적은 테스트가 프로덕션
설정값과 어긋난 채 남지 않게 하는 것이다.

- [ ] **Step 3: 설정 파일 두 곳 수정**

`src/main/resources/application.yml`:

```yaml
    resume-evaluation-max-tokens: 16000
```

`src/test/resources/application.yml`도 같은 키를 `16000`으로 바꾼다.

- [ ] **Step 4: 실제 설정값이 반영됐는지 확인**

`ResumeAnalysisAsyncServiceTest`가 실제 `BedrockConverseProperties`를 주입받으므로 이 테스트로 확인한다.

Run: `docker compose -f test.yml up -d && ./gradlew test --tests "com.samhap.kokomen.resume.*"`

Expected: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/application.yml src/test/resources/application.yml \
        src/test/java/com/samhap/kokomen/resume/external/ResumeAnalysisWiringTest.java
git commit -m "fix: 이력서 평가 maxTokens를 16000으로 올려 응답 잘림 방지"
```

---

### Task 4: GPT 폴백 로직 제거 (프로덕션 + 관련 테스트)

`ResumeAnalysisAsyncService`에서 폴백 분기와 `BEDROCK_UNHEALTHY` ThreadLocal을 제거한다. GPT 클라이언트
클래스 파일은 이 태스크에서 지우지 않는다 — 주입만 끊어 이 태스크가 독립적으로 컴파일·통과하게 한다.

**Files:**
- Modify: `src/main/java/com/samhap/kokomen/resume/service/ResumeAnalysisAsyncService.java`
- Modify: `src/test/java/com/samhap/kokomen/resume/service/ResumeAnalysisAsyncServiceTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `ResumeAnalysisAsyncService` 생성자가 4-인자에서 2-인자로 축소
  — `ResumeAnalysisAsyncService(ResumeAnalysisService, ResumeAnalysisStateService, ResumeAnalysisEvaluationBedrockClient, ResumeAnalysisQuestionBedrockClient)`
  (Lombok `@RequiredArgsConstructor`가 필드 순서대로 생성한다)

- [ ] **Step 1: 테스트를 새 계약으로 고쳐 실패시키기**

`ResumeAnalysisAsyncServiceTest`에서 다음을 수행한다.

(a) 필드·목 생성·조립 정리:

```java
    private ResumeAnalysisEvaluationBedrockClient evaluationBedrockClient;
    private ResumeAnalysisQuestionBedrockClient questionBedrockClient;
    private ResumeAnalysisAsyncService asyncService;

    /**
     * 2개 Bedrock 클라이언트만 평범한 Mockito 목으로 두고 서비스를 수동 조립한다. BaseTest에 목 빈을 추가하지 않으므로
     * 컨텍스트 fork가 늘지 않고, InOrder 검증도 가능하다. 필드명을 asyncService로 둔 것은 BaseTest에 같은 타입의
     * 목 필드가 생겨도 이 수동 조립 인스턴스가 가려지지 않게 하기 위해서다.
     */
    @BeforeEach
    void setUpAsyncService() {
        evaluationBedrockClient = mock(ResumeAnalysisEvaluationBedrockClient.class);
        questionBedrockClient = mock(ResumeAnalysisQuestionBedrockClient.class);
        asyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, resumeAnalysisStateService,
                evaluationBedrockClient, questionBedrockClient);
    }
```

`ResumeAnalysisEvaluationGptClient`·`ResumeAnalysisQuestionGptClient` import 2개를 제거한다.

(b) 테스트 2개를 **삭제**한다 — 검증 대상 동작 자체가 없어진다:
`Bedrock_평가가_실패하면_GPT_폴백으로_완료되고_질문_콜은_Bedrock을_건너뛴다`,
`Bedrock_질문생성이_실패하면_GPT_폴백으로_질문이_완료된다`

(c) `평가가_실패하면_EVALUATION_FAILED이고_질문_콜은_호출되지_않는다`를 다음으로 바꾼다.
GPT 스터빙 없이 Bedrock 실패 하나만으로 종단에 도달해야 한다:

```java
    @Test
    void 평가가_실패하면_EVALUATION_FAILED이고_질문_콜은_호출되지_않는다() {
        // given - 폴백이 없으므로 Bedrock 실패 하나가 곧 종단이다
        Long analysisId = saveGuestAnalysis("11.22.33.83").getId();
        willThrow(new ExternalApiException("Bedrock 호출 실패"))
                .given(evaluationBedrockClient).evaluate(any(ResumeAnalysisCommand.class));

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(found.getFailureReason())
                        .isEqualTo(ResumeAnalysisFailureReason.EVALUATION_LLM),
                () -> assertThat(found.getTotalScore()).isNull()
        );
        verify(questionBedrockClient, never()).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
    }
```

(d) `MAX_TOKENS로_잘린_실제_Bedrock_응답도_OUTPUT_TRUNCATED로_기록된다`의 given에서 GPT 스터빙 2줄
(`willThrow(...).given(evaluationGptClient)...`)을 지우고 수동 조립을 2-인자로 바꾼다:

```java
        ResumeAnalysisAsyncService bedrockWiredAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, resumeAnalysisStateService,
                new ResumeAnalysisEvaluationBedrockClient(converseClient, bedrockConverseProperties),
                new ResumeAnalysisQuestionBedrockClient(converseClient, bedrockConverseProperties));
```

(e) `평가는_성공하고_질문만_실패하면_QUESTION_FAILED이고_평가_결과가_보존된다`에서 GPT 스터빙 2줄
(`willThrow(new ExternalApiException("GPT 질문 생성 실패")).given(questionGptClient)...`)을 지운다.
나머지는 그대로 둔다.

(f) `질문_재시도는_readCommand로_복원한_커맨드를_쓰므로_토큰이_다시_차감되지_않는다`에서 GPT 스터빙 2줄을
지우고, given 주석을 `// given - 첫 실행은 평가 성공 + 질문 콜 실패로 QUESTION_FAILED를 만든다`로 바꾼다.
`questionBedrockClient`의 `willThrow(...).willReturn(...)` 연쇄 스터빙은 그대로 둔다 — 첫 호출은
실패하고 재시도 호출은 성공해야 한다.

(g) 나머지 테스트에서 `new ResumeAnalysisAsyncService(...)` 호출을 모두 2-인자 형태로 바꾼다
(`evaluationGptClient`, `questionGptClient` 인자 제거). 다음 위치에 있다:
410, 456, 480, 505, 529, 550, 575, 598, 620, 751행 부근.

Run: `grep -n "evaluationGptClient\|questionGptClient" src/test/java/com/samhap/kokomen/resume/service/ResumeAnalysisAsyncServiceTest.java`
로 잔여 참조가 0이 될 때까지 확인한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew compileTestJava`

Expected: 컴파일 실패 — `ResumeAnalysisAsyncService`의 4-인자 생성자에 2개만 넘겨 인자 개수 불일치

- [ ] **Step 3: `ResumeAnalysisAsyncService` 정리**

(a) 클래스 Javadoc 첫 단락에서 GPT 폴백 언급을 지운다. 25행의
`GPT 폴백도 재제출이 아니라 같은 스레드 내 순차 호출이다.`를 삭제하고 앞 문장을
`resumeAnalysisExecutor에 제출되는 단일 태스크. 평가 콜과 질문 콜을 같은 스레드에서 순차 실행한다.`로
합친다.

(b) `BEDROCK_UNHEALTHY` 필드 선언과 그 Javadoc(53-57행)을 삭제한다.

(c) GPT 클라이언트 필드 2개(`evaluationGptClient`, `questionGptClient`)와 import 2개를 삭제한다.

(d) `run`을 `try/finally` 없이 되돌린다 — finally의 유일한 용도가 ThreadLocal 정리였다:

```java
    public void run(ResumeAnalysisCommand command) {
        ResumeAnalysisEvaluation evaluation = runEvaluationHop(command);
        if (evaluation != null) {
            runQuestionHop(command, evaluation);
        }
    }
```

(e) `runEvaluationHop`의 폴백 분기를 제거한다:

```java
    ResumeAnalysisEvaluation runEvaluationHop(ResumeAnalysisCommand command) {
        ResumeAnalysisEvaluation evaluation;
        try {
            evaluation = evaluationBedrockClient.evaluate(command);
        } catch (Exception bedrockException) {
            log.error("Bedrock 이력서 분석 평가 실패 - analysisId: {}, exception: {}",
                    command.analysisId(), bedrockException.getClass().getName(), bedrockException);
            resumeAnalysisStateService.failEvaluation(
                    command.analysisId(), classifyEvaluationFailure(bedrockException));
            return null;
        }
        try {
            if (!completeEvaluationWithRetry(command.analysisId(), evaluation)) {
                return null;
            }
        } catch (RuntimeException e) {
            log.error("이력서 분석 평가 저장 실패 - analysisId: {}, exception: {}",
                    command.analysisId(), e.getClass().getName(), e);
            resumeAnalysisStateService.failEvaluation(
                    command.analysisId(), ResumeAnalysisFailureReason.PERSISTENCE);
            return null;
        }
        resumeAnalysisStateService.chargeTokensIfNeeded(command.analysisId(), command.billingMemberId());
        return evaluation;
    }
```

(f) `proceedQuestionHop`에서 `finally { BEDROCK_UNHEALTHY.remove(); }` 블록을 제거하고 호출부를
`generateQuestions`로 바꾼다:

```java
        List<GeneratedQuestionDto> questions;
        try {
            questions = questionBedrockClient.generateQuestions(questionCommand).questions();
        } catch (Exception e) {
            log.error("이력서 분석 질문 생성 실패 - analysisId: {}, exception: {}",
                    command.analysisId(), e.getClass().getName(), e);
            resumeAnalysisStateService.failQuestions(
                    command.analysisId(), ResumeAnalysisFailureReason.QUESTION_LLM);
            return;
        }
```

(g) `generateQuestionsWithFallback` 메서드 전체를 삭제한다. 위 (f)가 Bedrock 클라이언트를 직접 부르므로
래퍼가 필요 없다. `ResumeAnalysisQuestionResult` import가 남아 있으면 삭제한다.

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `docker compose -f test.yml up -d && ./gradlew test --tests "com.samhap.kokomen.resume.service.ResumeAnalysisAsyncServiceTest"`

Expected: 24개 테스트 PASS (기존 26개 - 삭제 2개)

- [ ] **Step 5: 전체 테스트 회귀 확인**

Run: `./gradlew test`

Expected: 전체 PASS. `BaseTest`의 GPT 목 빈은 아직 남아 있고 클래스도 존재하므로 컴파일된다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/samhap/kokomen/resume/service/ResumeAnalysisAsyncService.java \
        src/test/java/com/samhap/kokomen/resume/service/ResumeAnalysisAsyncServiceTest.java
git commit -m "refactor: 이력서 분석 GPT 폴백 분기와 BEDROCK_UNHEALTHY 플래그 제거"
```

---

### Task 5: GPT 폴백 파일 10개 삭제와 잔여 참조 정리

Task 4로 프로덕션 참조가 끊겼다. 이제 파일을 지우고 테스트에 남은 참조를 정리한다.

**Files:**
- Delete: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisEvaluationGptClient.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisQuestionGptClient.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisGptResponses.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisGptTimeouts.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/dto/ResumeAnalysisEvaluationGptRequest.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/dto/ResumeAnalysisQuestionGptRequest.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptMessage.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptResponse.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptChoice.java`
- Delete: `src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptResponseMessage.java`
- Modify: `src/test/java/com/samhap/kokomen/global/BaseTest.java:11-14,58-63`
- Modify: `src/test/java/com/samhap/kokomen/resume/external/ResumeAnalysisWiringTest.java`
- Modify: `src/test/java/com/samhap/kokomen/resume/external/dto/ResumeAnalysisFlatSchemaTest.java`

**Interfaces:**
- Consumes: Task 4가 끊어놓은 프로덕션 참조 0건
- Produces: 없음 (삭제 전용)

**유지할 것** — 이 파일들은 Bedrock 경로가 쓰므로 지우지 않는다:
`BaseGptClient`(`InterviewProceedGptClient`가 상속), `ResumeAnalysisEvaluationFlatResponse`,
`ResumeAnalysisQuestionsFlatResponse`, `GptProperties`와 `open-ai` 설정 4필드
(`InterviewProceedGptClient`가 `evaluationTemperature()`를 두 곳에서 쓴다),
`ResumeAnalysisFailureReason` 6개 상수 전부.

- [ ] **Step 1: `BaseTest` 목 빈 제거**

`src/test/java/com/samhap/kokomen/global/BaseTest.java`에서 다음 4줄을 삭제한다:

```java
    @MockitoBean
    protected ResumeAnalysisEvaluationGptClient resumeAnalysisEvaluationGptClient;
```
```java
    @MockitoBean
    protected ResumeAnalysisQuestionGptClient resumeAnalysisQuestionGptClient;
```

그리고 import 2개를 삭제한다:
```java
import com.samhap.kokomen.resume.external.ResumeAnalysisEvaluationGptClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisQuestionGptClient;
```

- [ ] **Step 2: `ResumeAnalysisWiringTest`에서 GPT 테스트 7개 제거**

다음 테스트를 **삭제**한다:
- `평가_user_메시지는_Bedrock과_GPT가_단일_소스에서_나온다`
- `질문_user_메시지는_Bedrock과_GPT가_단일_소스에서_나온다`
- `평가_시스템_메시지는_GPT와_Bedrock이_단일_소스에서_나온다`
- `질문_시스템_메시지는_GPT와_Bedrock이_단일_소스에서_나온다`
- `GPT_평가_요청은_평가_도구를_강제하고_temperature를_전달한다`
- `GPT_질문_요청은_질문_도구를_강제한다`
- `GPT_클라이언트는_커넥트_3초_리드_90초_타임아웃을_명시한다`
- `GPT_타임아웃_적용은_주입받은_빌더를_변형하지_않는다`

프롬프트 회귀 가드를 잃지 않도록 **대체 테스트 2개를 추가**한다. 삭제한 단일-소스 테스트가 지키던 것은
"Bedrock 프롬프트가 `ResumeAnalysisSystemMessages`/`ResumeAnalysisUserMessages` 단일 소스에서
나온다"이므로, 대응 provider 없이 그 소스와 직접 비교한다:

```java
    @Test
    void 평가_시스템_메시지는_단일_소스에서_나온다() {
        for (boolean jdProvided : List.of(true, false)) {
            String bedrockSystem = ResumeAnalysisBedrockRequestFactory.createEvaluationSystem(jdProvided)
                    .get(0).text();

            assertThat(bedrockSystem).as("jdProvided=%s의 system 메시지", jdProvided)
                    .isEqualTo(ResumeAnalysisSystemMessages.evaluation(jdProvided));
        }
    }

    @Test
    void 질문_시스템_메시지는_단일_소스에서_나온다() {
        String bedrockSystem = ResumeAnalysisBedrockRequestFactory.createQuestionGenerationSystem()
                .get(0).text();

        assertThat(bedrockSystem).isEqualTo(ResumeAnalysisSystemMessages.questionGeneration());
    }
```

`import com.samhap.kokomen.resume.tool.ResumeAnalysisSystemMessages;`를 추가하고,
`ResumeAnalysisEvaluationGptRequest`·`ResumeAnalysisQuestionGptRequest`·`RestClient`·`Duration` import를
삭제한다(남은 테스트에서 쓰지 않으면).

클래스 Javadoc에서 `system 메시지가 GPT·Bedrock 양쪽에서 같은 단일 소스에서 나오는지도 이 파일이
단정한다.`를 `system 메시지가 ResumeAnalysisSystemMessages 단일 소스에서 나오는지도 이 파일이
단정한다.`로 바꾼다.

- [ ] **Step 3: `ResumeAnalysisFlatSchemaTest`에서 GPT 참조 제거**

테스트 3개를 **삭제**한다 — 대응 provider가 없어 검증 대상이 사라진다:
`GPT_평가_스키마도_jdProvided에_따라_required_개수가_같다`,
`평가_스키마의_required_집합은_Bedrock과_GPT가_완전히_동일하다`,
`질문_스키마의_minItems와_maxItems는_Bedrock과_GPT가_같다`

테스트 5개에서 GPT 절만 잘라낸다. Bedrock 단정은 이미 각 테스트에 있으므로 남는다:

- `평가_스키마는_JD_유무와_무관하게_중첩_object가_없다`: 뒤 2줄
  (`assertNoNestedObject(ResumeAnalysisEvaluationGptRequest...)`) 삭제
- `점수_필드는_integer이고_최소0_최대100이다`: `gptScoreField` 블록(110-115행) 삭제
- `근거_배열은_최소2개_최대6개다`: `gptReasonField` 블록(126-130행) 삭제
- `질문과_이유_필드에는_maxLength가_설정되어_있다`: `gptItemProperties` 블록(164-168행) 삭제
- `구지표_이름은_신규_스키마에_존재하지_않는다`: 뒤 `assertThat(ResumeAnalysisEvaluationGptRequest...)`
  블록(187-189행) 삭제

`import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;`를 삭제한다.
`castMap` 헬퍼는 156행에서 Bedrock 중첩 맵에 계속 쓰므로 남긴다.

클래스 Javadoc에서 `Bedrock과 GPT가 완전히 같은 사양을 렌더하는지 검증한다.`를
`중첩 object 없이 flat으로 구성되는지 검증한다.`로 바꾼다.

- [ ] **Step 4: 프로덕션 파일 10개 삭제**

```bash
git rm src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisEvaluationGptClient.java \
       src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisQuestionGptClient.java \
       src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisGptResponses.java \
       src/main/java/com/samhap/kokomen/resume/external/ResumeAnalysisGptTimeouts.java \
       src/main/java/com/samhap/kokomen/resume/external/dto/ResumeAnalysisEvaluationGptRequest.java \
       src/main/java/com/samhap/kokomen/resume/external/dto/ResumeAnalysisQuestionGptRequest.java \
       src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptMessage.java \
       src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptResponse.java \
       src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptChoice.java \
       src/main/java/com/samhap/kokomen/resume/external/dto/ResumeGptResponseMessage.java
```

- [ ] **Step 5: 잔여 참조가 0인지 확인**

Run:
```bash
grep -rn "ResumeAnalysisEvaluationGptClient\|ResumeAnalysisQuestionGptClient\|ResumeAnalysisGptResponses\|ResumeAnalysisGptTimeouts\|ResumeAnalysisEvaluationGptRequest\|ResumeAnalysisQuestionGptRequest\|ResumeGptMessage\|ResumeGptResponse\|ResumeGptChoice\|ResumeGptResponseMessage" src/ docs/api/ 2>/dev/null
```

Expected: 출력 없음. `docs/api/index.adoc`에 참조가 있으면 함께 지운다.

- [ ] **Step 6: 전체 테스트 통과 확인**

Run: `docker compose -f test.yml up -d && ./gradlew clean build`

Expected: BUILD SUCCESSFUL. 컴파일 에러가 남으면 Step 5의 grep을 다시 돌려 누락 참조를 찾는다.

- [ ] **Step 7: 커밋**

```bash
git add -A src/main/java/com/samhap/kokomen/resume/external \
           src/test/java/com/samhap/kokomen/global/BaseTest.java \
           src/test/java/com/samhap/kokomen/resume/external
git commit -m "refactor: 이력서 분석 GPT 폴백 파일 10개 삭제"
```

---

### Task 6: 배포 후 지표 검증

이 태스크는 코드 변경이 없다. dev 배포 후 실제 제출 1건으로 근인이 해소됐는지 확인한다. 이번 문제를
잡아낸 계측을 그대로 검증에 쓴다.

**Files:** 없음

**Interfaces:**
- Consumes: Task 1~5가 dev에 배포된 상태
- Produces: 없음

- [ ] **Step 1: dev에 배포하고 실제 이력서 분석 1건 제출**

- [ ] **Step 2: 제출당 invocation이 1건인지 확인**

Run (제출 시각을 UTC로 환산해 `--start-time`/`--end-time`을 잡는다):

```bash
aws cloudwatch get-metric-statistics --namespace AWS/Bedrock \
  --metric-name Invocations \
  --dimensions Name=ModelId,Value=us.anthropic.claude-sonnet-4-6 \
  --start-time <제출시각-5분>Z --end-time <제출시각+10분>Z \
  --period 60 --statistics Sum \
  --profile kokomen --region us-east-1 \
  --query "sort_by(Datapoints,&Timestamp)[].[Timestamp,Sum]" --output text
```

Expected: 평가 콜 1건 + 질문 콜 1건 = 합계 2건. 4건이 나오면 재시도가 여전히 소켓 타임아웃에
걸린 것이므로 Task 1의 재시도 전략을 다시 본다.

- [ ] **Step 3: 지연과 출력 토큰이 한계 안인지 확인**

Run: 위 명령에서 `--metric-name`을 `InvocationLatency`, `OutputTokenCount`로 바꿔 각각 실행한다.

Expected: `InvocationLatency` 최대값 < 360,000ms, `OutputTokenCount` 최대값 < 16,000.
`OutputTokenCount`가 정확히 16,000이면 여전히 잘리는 것이므로 상향폭을 재검토한다.

- [ ] **Step 4: 분석이 COMPLETED로 끝났고 실패 로그가 없는지 확인**

Run:
```bash
ssh kokomen-dev 'cd /home/ubuntu/kokomen-backend/docker/dev/interview/api/app/logs/ \
  && grep -E "Read timed out|Bedrock 이력서 분석 평가 실패|이력서 분석 질문 생성 실패" app.log | tail -20'
```

Expected: 출력 없음

Run:
```bash
ssh kokomen-dev 'cd /home/ubuntu/kokomen-backend/docker/dev/interview/api/app/logs/ \
  && grep -E "LoggingFilter.*resume-analyses" app.log | tail -20'
```

Expected: `POST /api/v1/resume-analyses (202 ACCEPTED)`와 뒤이은 `GET .../{id} (200 OK)` 폴링.
API 응답으로 상태가 `COMPLETED`인지 확인한다.

---

## Self-Review

**1. 스펙 커버리지** — 스펙 8개 절을 태스크에 매핑했다.

| 스펙 절 | 태스크 |
|---|---|
| 1. 근인 | (배경. 코드 변경 없음) |
| 2. 전송 계층 — 전용 빈 + 상수 분리 | Task 1, Task 2 |
| 3. 재시도 전략 | Task 1 |
| 4. 잘림 방지 | Task 3 |
| 5. GPT 폴백 제거 | Task 4, Task 5 |
| 6. 테스트 | Task 1·2의 신규 테스트, Task 4·5의 테스트 개정 |
| 7. 배포 후 검증 | Task 6 |
| 8. 범위 밖 | (해당 태스크 없음이 의도) |

누락 없음. 스펙 6절이 언급한 항목 중 `ResumeAnalysisFlatSchemaTest`는 스펙에 "GPT 스키마 참조 제거,
Bedrock 측 단정만 유지"로만 적혀 있었는데, 계획에서는 삭제 3개·개정 5개로 행 번호까지 특정했다.

**2. 플레이스홀더 스캔** — TBD·TODO·"적절히 처리" 없음. 모든 코드 스텝에 실제 코드 블록이 있다.
Task 6 Step 2의 `<제출시각±N분>`은 실행 시점에만 알 수 있는 값이므로 플레이스홀더가 아니라 파라미터다.

**3. 타입 일관성** — Task 1이 노출하는 5개 상수와 `retryStrategy()`를 Task 2가 정확히 그 이름으로
쓴다. Task 4가 만드는 2-인자 생성자를 Task 4의 테스트가 같은 인자 순서
(`resumeAnalysisService, resumeAnalysisStateService, evaluationBedrockClient, questionBedrockClient`)로
호출한다 — Lombok `@RequiredArgsConstructor`가 필드 선언 순서를 따르므로 필드 삭제 후 순서가 이와 같다.
빈 이름 `resumeAnalysisBedrockRuntimeClient`·`resumeAnalysisBedrockConverseClient`가 Task 2의 `@Bean`
메서드명, `@Qualifier` 문자열, 테스트의 `getBean` 인자에서 모두 동일하다.

**발견해 고친 것**: Task 2의 테스트 초안이 `withBean(BedrockConverseClient.class,
BedrockConverseClient::new)`로 3-인자 생성자를 무인자 팩터리에 넘겨 컴파일되지 않았고, "이 줄은 안 되니
바꿔라"는 지시가 코드 블록 뒤에 따라붙어 구현자가 두 버전을 함께 보게 돼 있었다. `TestBeans` 중첩
설정에서 `@Primary @Bean`으로 등록하는 정본 하나로 합쳤다. `@Primary`를 테스트 빈에도 붙인 이유를
Javadoc에 남겼다 — 그게 없으면 이력서 클라이언트가 `@Qualifier` 없이도 통과해 위양성이 된다.
