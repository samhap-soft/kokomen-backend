# 이력서 분석 Bedrock 타임아웃 해소 + GPT 폴백 제거

신규 통합 이력서 분석 API(`POST /api/v1/resume-analyses`)의 평가 단계가 dev에서
100% 실패한다. 원인을 계측으로 확정했고, 그 수정과 함께 GPT 폴백을 제거한다.

## 1. 근인

`AwsConfig.bedrockRuntimeClient()`가 `ApacheHttpClient.socketTimeout`을 **60초**로
두는데, 이력서 평가 Converse 호출의 실제 소요는 **130~160초**다. 매 시도가 60초에
끊기고 AWS SDK 기본 재시도 정책이 4회 시도해 약 4분을 태운 뒤 GPT 폴백으로 넘어간다.

CloudWatch `AWS/Bedrock`(us-east-1, `us.anthropic.claude-sonnet-4-6`) 실측:

| 시각(KST) | InvocationLatency | OutputTokenCount |
|---|---|---|
| 2026-08-05 20:49 | 129,992ms | 8,358 |
| 2026-08-05 20:51 | 159,009ms | 10,000 (cap 도달) |
| 2026-08-05 20:52 | 156,098ms | 9,967 |
| 2026-08-05 21:01 | 142,322ms | 9,033 |
| 2026-08-05 21:02 | 151,585ms | 9,804 |
| 2026-08-05 21:03 | 155,455ms | 9,587 |
| 2026-08-05 21:04 | 133,549ms | 8,477 |

InputTokenCount는 3,639로 일정하다. 생성 속도는 **62~64 tok/s**로 매우 안정적이다
(9,587 / 155.5s = 61.7, 9,033 / 142.3s = 63.5, 8,358 / 130.0s = 64.3).

타임라인이 정확히 맞는다. analysisId 5는 `POST 20:59:18` → 실패 로그 `21:03:21`,
즉 **243초 ≈ 60초 × 4회**이고 예외 메시지가 `SDK Attempt Count: 4`다. 위 표의
21:01~21:04 invocation 4건이 그 4회 시도이며, 클라이언트가 60초에 끊은 뒤에도
Bedrock은 끝까지 생성하고 과금한다.

### 근인이 아닌 것

- **리전·모델 ID·모델 접근 권한**: us-east-1에 `us.anthropic.claude-sonnet-4-6`
  호출 지표가 실제로 쌓였다. 요청이 Bedrock에 도달해 완주했다는 뜻이다.
  inference profile도 `ACTIVE`다.
- **IAM·네트워크**: 위와 같은 이유. 연결이 3초 커넥트 타임아웃 안에 성립했고
  TLS 이후 응답을 기다리다 끊긴 것이다.
- **모델 ID 변경 전 실패와는 다른 문제**: analysisId 1~3(2026-08-04 22:27,
  08-05 00:39)의 실패는 `AccessDeniedException`이었고, 리전·모델 핫픽스
  (`26c6955`, `c4dede6`, `e372abe`, `56c9194`, `f3ff76e`)로 이미 해소됐다.
  이 문서가 다루는 것은 그 이후에 남은 `SdkClientException: Read timed out`이다.

### 왜 이력서 평가 호출만 실패하는가

같은 싱글턴 `bedrockRuntimeClient`를 쓰는 인터뷰 호출은 `maxTokens`가 2048/4096이라
prod 실측 5~25초(최대 24,781ms)로 60초 안에 끝난다. 이력서 평가만
`resume-evaluation-max-tokens: 10000`이고 실제로 8,300~10,000 토큰을 생성한다.
JD 제공 시 5차원 × 4필드(`_reasoning` 산문 + `_reason`/`_improvements` 각 2~6개
불릿) + `total_feedback` 구조이고 한국어라 토큰 소모가 크다.

## 2. 전송 계층 — 이력서 전용 Bedrock 클라이언트 빈

`AwsConfig`에 이력서 분석 전용 `BedrockRuntimeClient`를 추가한다. 인터뷰용 기존 빈은
`@Primary`로 두어 60초 빠른 실패 특성을 보존한다. 공용 빈의 타임아웃을 올리면
인터뷰 장애 시에도 최대 6분을 붙잡게 되므로 분리한다.

### 타임아웃 수치

| 값 | 근거 |
|---|---|
| maxTokens 16,000 ÷ 실측 62 tok/s | ≈ 258초 |
| `socketTimeout` | **360초** (258초 대비 여유 40%) |
| `apiCallTimeout` | **390초** |
| 스윕 `STALE_THRESHOLD` | 600초 → 210초 여유 |

300초는 258초 대비 여유가 13%뿐이라 생성 속도가 조금만 떨어져도 같은 실패로
회귀한다. 360초는 `ResumeAnalysisRecoveryScheduler.STALE_THRESHOLD`(10분) 안에
안전하게 들어가므로, 아직 실행 중인 행이 스윕에 `STALE_SWEEP`으로 종단되는 경로가
열리지 않는다.

`maxConnections`는 전용 빈에도 60을 준다. `resumeAnalysisExecutor`가
core=max=60이므로 커넥션 풀이 워커 수와 일치해야 하고, 인터뷰 풀과 분리되어야
서로 고갈시키지 않는다.

### 상수 분리

빌드된 `BedrockRuntimeClient`는 `socketTimeout`·`apiCallTimeout`을 읽을 수 없어
설정값을 테스트로 고정할 수 없다. 그래서 삭제되는 `ResumeAnalysisGptTimeouts`와 같은
패턴으로 **`resume/external/ResumeAnalysisBedrockTimeouts`**를 신설해
`CONNECT_TIMEOUT`·`SOCKET_TIMEOUT`·`API_CALL_TIMEOUT`·`MAX_CONNECTIONS`를 공개
상수로 두고, `AwsConfig`가 그 상수를 참조한다. 재시도 전략도 이 클래스의 정적
팩터리(`retryStrategy()`)로 만들어 테스트가 전략 객체를 직접 받을 수 있게 한다.

### 빈 배선

`BedrockConverseClient`는 인터뷰·이력서가 공유한다. 따라서 전용 런타임 클라이언트를
물린 **두 번째 `BedrockConverseClient` 빈**을 `AwsConfig`에 `@Bean`으로 정의하고,
`ResumeAnalysisEvaluationBedrockClient`와 `ResumeAnalysisQuestionBedrockClient`가
`@Qualifier`로 그 빈을 주입받는다. `BedrockConverseClient` 자체 코드는 변경하지
않는다.

질문 콜(`maxTokens` 2048)도 같은 전용 빈을 쓴다. 별도 세 번째 빈을 만들 이유가
없고, 긴 타임아웃은 실패 시에만 관측되는 차이다.

## 3. 재시도 전략 — 스로틀링/5xx만 1회

`RetryStrategy.Builder.retryOnException`은 기존 조건에 **추가**되는 성질이므로,
`StandardRetryStrategy.builder()`로 **빈 전략에서 시작**해 필요한 예외만 명시한다.

```java
StandardRetryStrategy.builder()
        .maxAttempts(2)
        .useClientDefaults(false)
        .retryOnExceptionOrCauseInstanceOf(ThrottlingException.class)
        .retryOnExceptionOrCauseInstanceOf(InternalServerException.class)
        .retryOnExceptionOrCauseInstanceOf(ModelTimeoutException.class)
        .backoffStrategy(BackoffStrategy.exponentialDelayHalfJitter(
                Duration.ofSeconds(1), Duration.ofSeconds(5)))
        .build()
```

소켓 타임아웃 배제는 위 세 줄의 명시적 허용 목록만으로 성립한다 — 이 목록에
`SocketTimeoutException`/`SdkClientException`을 매칭하는 조건이 없으므로 재시도
없이 즉시 종단한다. `ResumeAnalysisBedrockTimeoutsTest`가 이 배제를 원본
`SocketTimeoutException`과 이를 감싼 `SdkClientException` 양쪽 경로로
검증했다(SDK 2.31.69 기준). `useClientDefaults(false)`는 이중 안전장치로 남긴다 —
전략 객체를 직접 두드리는 단위 테스트로는 관측되지 않는 영역, 즉 이 전략이 실제
클라이언트 실행 파이프라인에 연결됐을 때의 동작까지는 이 테스트가 보증하지
않는다. 258초 생산을 통째로 다시 하는 것은 순수 낭비이고, 지금 발생 중인 4배
과금의 진범이다.

위 세 예외는 생산 없이 즉시 오는 실패라 재시도가 싸고 효과가 크다. GPT 안전망이
사라지는 만큼 이 한 겹은 남긴다.

API 가용성은 SDK 2.31.69에서 확인했다 — `ClientOverrideConfiguration.Builder`에
`retryStrategy(RetryStrategy)`·`apiCallTimeout(Duration)`이 있고,
`RetryStrategy.Builder`에 `maxAttempts`·`useClientDefaults`·
`retryOnExceptionOrCauseInstanceOf`가 있다. 세 예외 타입은 모두
`software.amazon.awssdk.services.bedrockruntime.model` 패키지에 존재한다.

## 4. 잘림 방지

`application.yml`의 `aws.bedrock.resume-evaluation-max-tokens`를
**10,000 → 16,000**으로 올린다. 프롬프트와 tool 스키마는 손대지 않는다.

관측 중앙값 ~9,500에 대해 68% 여유가 생긴다. 7건 중 1건이 정확히 10,000에 걸렸으므로
현재 cap은 실제로 부족하다.

그래도 cap에 걸리면 `BedrockConverseClient.extractToolUse`가
`stopReason=MAX_TOKENS`로 `ExternalApiException`을 던지고, 기존
`ResumeAnalysisAsyncService.classifyEvaluationFailure`가 `TRUNCATED_RESPONSE_MARKER`로
잡아 `OUTPUT_TRUNCATED`로 기록한다. 이 경로는 이미 구현돼 있어 추가 작업이 없다.

## 5. GPT 폴백 제거

### 삭제 (10개 파일)

- `resume/external/ResumeAnalysisEvaluationGptClient.java`
- `resume/external/ResumeAnalysisQuestionGptClient.java`
- `resume/external/ResumeAnalysisGptResponses.java`
- `resume/external/ResumeAnalysisGptTimeouts.java`
- `resume/external/dto/ResumeAnalysisEvaluationGptRequest.java`
- `resume/external/dto/ResumeAnalysisQuestionGptRequest.java`
- `resume/external/dto/ResumeGptMessage.java`
- `resume/external/dto/ResumeGptResponse.java`
- `resume/external/dto/ResumeGptChoice.java`
- `resume/external/dto/ResumeGptResponseMessage.java`

### `ResumeAnalysisAsyncService` 정리

- `runEvaluationHop`: 폴백 `catch` 블록이 사라지고, Bedrock 실패 시 곧바로
  `failEvaluation(analysisId, classifyEvaluationFailure(e))` 후 `null` 반환.
  `classifyEvaluationFailure`는 그대로 남는다 — `OUTPUT_TRUNCATED`와
  `EVALUATION_LLM`을 가르는 것은 폴백과 무관하게 여전히 유효하다.
- `generateQuestionsWithFallback`: Bedrock 직호출로 축약. 메서드 이름에서
  `WithFallback`을 뗀다.
- `BEDROCK_UNHEALTHY` ThreadLocal: 폴백 라우팅만을 위해 존재했으므로 필드 선언과
  `run`·`proceedQuestionHop`의 `remove()` 호출 두 곳을 함께 제거한다. `run`의
  `try/finally`도 남길 이유가 없어진다.
- 필드에서 `evaluationGptClient`·`questionGptClient` 제거.

### 유지

- `BaseGptClient` — `InterviewProceedGptClient`가 계속 상속한다.
- `ResumeAnalysisEvaluationFlatResponse`·`ResumeAnalysisQuestionsFlatResponse` —
  Bedrock `parseToolInput`이 사용한다. `toEvaluation`의 응답 변형 방어
  (`_score` 누락 시 `ExternalApiException`)도 Bedrock 경로에 그대로 유효하다.
- `GptProperties`와 `open-ai` 설정 — **네 필드 모두 손대지 않는다.**
  `InterviewProceedGptClient`가 `evaluationTemperature()`를 두 곳
  (`createProceedGptRequest`, `createEndGptRequest`)에서 쓰고, `generationTemperature`·
  `feedbackTemperature`도 인터뷰 Bedrock 클라이언트가 쓴다. 이력서 GPT 클라이언트를
  지워도 고아가 되는 프로퍼티는 없다.
- `ResumeAnalysisFailureReason` 6개 상수 전부. GPT 전용 상수는 애초에 없다.

## 6. 테스트

- `global/BaseTest`: resume GPT 클라이언트 `@MockitoBean` 2개와 import 2개 제거.
- `ResumeAnalysisWiringTest`: "Bedrock과 GPT가 단일 소스에서 나온다" 4개 테스트는
  대응 provider가 없어져 삭제하고, **Bedrock system/user 메시지 내용 단정으로
  대체해 프롬프트 회귀 가드를 유지**한다. GPT 요청 배선·타임아웃 테스트 3개 삭제.
- `ResumeAnalysisAsyncServiceTest`(26개 테스트): 변경량이 가장 크다.
  - `ResumeAnalysisAsyncService` 생성자가 4-인자 → 2-인자로 줄어 **약 15개 호출
    지점**을 함께 수정한다. 목 필드·`mock(...)` 생성 2개도 제거.
  - **삭제 2개**: `Bedrock_평가가_실패하면_GPT_폴백으로_완료되고_질문_콜은_Bedrock을_건너뛴다`,
    `Bedrock_질문생성이_실패하면_GPT_폴백으로_질문이_완료된다`. 검증 대상 동작 자체가
    없어진다.
  - **개정 4개**: `평가가_실패하면_EVALUATION_FAILED이고_질문_콜은_호출되지_않는다`,
    `MAX_TOKENS로_잘린_실제_Bedrock_응답도_OUTPUT_TRUNCATED로_기록된다`,
    `평가는_성공하고_질문만_실패하면_QUESTION_FAILED이고_평가_결과가_보존된다`,
    `질문_재시도는_readCommand로_복원한_커맨드를_쓰므로_토큰이_다시_차감되지_않는다`.
    지금은 "Bedrock 실패 + GPT도 실패"를 함께 스터빙해야 종단에 도달하는데, 개정 후에는
    **Bedrock 실패 스터빙 하나만으로** 같은 결론에 도달해야 한다. 이 단순화 자체가
    폴백 제거가 실제로 반영됐다는 증거다.
  - 나머지 20개(과금·상태 가드·영속 재시도·MDC 전파)는 폴백과 무관해 생성자 인자만
    바뀐다. `MAX_TOKENS ... OUTPUT_TRUNCATED` 테스트가 이미 존재하므로 4절의 잘림
    처리 경로는 신규 테스트 없이 회귀가 막힌다.
- `ResumeAnalysisFlatSchemaTest`: GPT 스키마 참조 제거, Bedrock 측 단정만 유지.
- **신규** 재시도 전략 단위 테스트 — 이 설계의 유일한 불확실 지점을 못 박는 테스트다.
  `useClientDefaults(false)`가 실제로 기본 재시도 조건을 차단하는지가 검증 대상이다.
  `RetryStrategy` 공개 API로 직접 단정할 수 있음을 확인했다:
  `acquireInitialToken(AcquireInitialTokenRequest.create(scope))`로 토큰을 받고
  `refreshRetryToken(... .failure(예외) ...)`를 호출해, `ThrottlingException`에는
  `RefreshRetryTokenResponse`가 반환되고 `SocketTimeoutException`에는
  `TokenAcquisitionFailedException`이 던져지는지 본다. `maxAttempts()`와
  `useClientDefaults()`도 전략 객체에서 직접 읽어 단정한다.
  `retryOnExceptionOrCauseInstanceOf` 세 줄을 지우면 이 테스트가 실패하는지 확인해
  동어반복이 아님을 검증할 것.
- **신규** 타임아웃 상수 테스트: `ResumeAnalysisBedrockTimeouts`의 네 상수가
  의도값(3초 / 360초 / 390초 / 60)인지 단정한다. 삭제되는
  `GPT_클라이언트는_커넥트_3초_리드_90초_타임아웃을_명시한다` 테스트와 같은 성격이다.
  **한계를 명시한다** — 이 테스트는 의도값을 고정할 뿐이고, SDK가 그 값을 실제로
  지키는지는 7절의 배포 후 지표 확인으로 검증한다.

## 7. 배포 후 검증

dev 배포 후 실제 제출 1건으로 다음을 확인한다.

1. CloudWatch `AWS/Bedrock` invocation이 **제출당 1건**(4건이 아님)
2. `InvocationLatency`가 360초 미만, `OutputTokenCount`가 16,000 미만
3. 분석 행이 `COMPLETED`로 종단
4. 애플리케이션 로그에 `Read timed out`과 GPT 폴백 로그가 없음

이 지표가 이번 근인을 잡아낸 계측이므로 그대로 검증에 쓴다.

prod·sub에는 이 신규 API가 아직 배포되지 않았다 — prod 로그에는 구 플로우
(`/api/v1/resumes/evaluations`, `/api/v1/interviews/resume-based/...`)만 찍히고,
prod 저장소 HEAD는 `f3ff76e`이며 JVM은 2026-08-04 기동(리전 핫픽스 이전 빌드)이다.
sub 서버는 API 컨테이너 없이 Redis·모니터링만 돌고 있다. 즉 현재는 dev 전용 증상이지만
고치지 않고 배포하면 prod에서 동일하게 재현된다.

## 8. 범위 밖 (YAGNI)

- `ConverseStream` 전환 — 청크마다 read 타이머가 리셋되어 소켓 타임아웃 문제를
  구조적으로 제거하지만, 응답 핸들러·`toolUse` 부분 JSON 누적·파싱 코드가 새로
  필요하다. 전용 빈 + 타임아웃 상향으로 실패 모드가 사라지므로 지금은 하지 않는다.
- 평가 hop 재시도 엔드포인트 신설 — 평가 실패는 `chargeTokensIfNeeded`가 평가 커밋
  이후에 실행되므로 미과금이다. 사용자 재제출에 부담이 없어 별도 수단이 필요하지 않다.
- 프롬프트·불릿 상한(`BULLET_MAX_ITEMS`) 조정 — 결과물 분량은 의도된 산출물이므로
  유지한다.
- `BedrockConverseClient` 리팩터링 — 두 번째 빈 주입만으로 목적이 달성된다.
