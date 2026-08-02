<!-- 이 문서는 사용자와의 브레인스토밍으로 확정한 결정(D1~D12)을 코드 구조로 옮긴 설계안이며, -->
<!-- 2026-07-30 방향 재조정(M1~M5)으로 하위호환 전제가 폐기된 뒤 개정됐다. -->
<!-- 확정 결정 요약: (1) 구 이력서 평가·구 질문생성 API 12개를 삭제하고 신규 통합 7개만 남긴다 -->
<!-- (2) 구 테이블 2개 DROP + 과거 RESUME_BASED 면접 기록 전량 삭제 -->
<!-- (3) generated_question은 resume_analysis 단일 부모(analysis_id NOT NULL, XOR CHECK 없음) -->
<!-- (4) recruit 도메인 완전 제거 (5) JD는 job_description 자유 텍스트 선택 -->
<!-- (6) 가중치 2세트·소프트스킬 중립 기준점 (7) LLM 2콜 순차 + 단계적 공개 -->
<!-- (8) 게스트는 신규 API가 유일 경로·토큰 5·IP당 1회 365일 -->
<!-- 폐기된 결정: D1(기존 11개 유지), D2(구 프롬프트·스키마·테스트 동결), N1~N5(RENAME 방식) -->
<!-- 마이그레이션은 V51(원복·무변경) / V52(recruit DROP) / V53(퍼지 DML) / V54(M3+구 테이블 DROP) -->

# 이력서 분석 · 면접 질문 통합 API 설계

## 0. 확정 명칭

### 0-1. DB

| 종류 | 확정 이름 |
|---|---|
| 부모 테이블 | `resume_analysis` (38컬럼 / 인덱스 7 / FK 3 / CHECK 2) |
| 원문 사이드 테이블 (1:1) | `resume_analysis_source_text` (5컬럼, `uk_rast_analysis_id` UNIQUE, `fk_rast_analysis` ON DELETE CASCADE) |
| 질문 저장 | **기존 `generated_question` 재사용 — 단일 부모.** 최종 형상: `analysis_id BIGINT NOT NULL` + `fk_generated_question_analysis` + `idx_generated_question_analysis_id`. `generation_id` / `fk_gq_generation` / `idx_generated_question_generation_id` / `chk_generated_question_parent` **전부 부재** |
| 마이그레이션 | **4개** — `V51`(원복·무변경) / `V52`(recruit DROP) / `V53`(퍼지 DML) / `V54`(M3 + 구 테이블 DROP). 상세는 §3-2~§3-4 |
| DROP 테이블 | **11개 확정.** `resume_evaluation`, `resume_question_generation`, `recruit_education`, `recruit_employee_type`, `recruit_employment`, `recruit_region`, `ocr_waiting_list`, `recruit`, `affiliate`, `company`, `crawling_request` |
| 테이블 총수 (test, `flyway_schema_history` 포함) | 현재 31 → **20** |
| 5지표 컬럼 | `{problem_solving,project_experience,technical_skills,soft_skills,jd_fit}_{score,reason,improvements}` (15개) + `total_score`, `total_feedback` |
| 소유/게스트 컬럼 | `member_id`(NULL 허용), `guest_token` CHAR(36) UNIQUE, `guest_ip` VARCHAR(45), `guest_lock_value` CHAR(36) |
| 상태/과금/재시도 컬럼 | `state` VARCHAR(30), `failure_reason` VARCHAR(30), `jd_provided`, `billing_required`, `charged_token_count` SMALLINT, `token_charge_failed`, `question_retry_count`, `evaluation_completed_at`, `question_started_at`, `completed_at`, `created_at` |
| 만들지 않는 컬럼 | `updated_at`(BaseEntity 미매핑), `public_id`, `{dim}_reasoning`, `weight_percent`, `token_charged`(→`charged_token_count`로 통합) |

### 0-2. Java 타입

| 종류 | 확정 이름 |
|---|---|
| 엔티티 | `resume.domain.ResumeAnalysis`, `resume.domain.ResumeAnalysisSourceText` |
| 상태 enum | `resume.domain.ResumeAnalysisState` = `PENDING`, `EVALUATION_COMPLETED`, `COMPLETED`, `EVALUATION_FAILED`, `QUESTION_FAILED` |
| 실패 원인 enum | `resume.domain.ResumeAnalysisFailureReason` = `EVALUATION_LLM`, `OUTPUT_TRUNCATED`, `QUESTION_LLM`, `PERSISTENCE`, `CAPACITY`, `STALE_SWEEP`, `GUEST_LIMIT` |
| 지표 enum | `resume.domain.ResumeAnalysisDimension` = `PROBLEM_SOLVING`, `PROJECT_EXPERIENCE`, `TECHNICAL_SKILLS`, `SOFT_SKILLS`, `JD_FIT` (선언 순서 = 표시 순서). 지표 키 단일 소스 = `toolKey()` |
| 가중치 | `resume.domain.ResumeAnalysisWeights` enum { `JD_PROVIDED`, `JD_ABSENT` } |
| 값객체 | `resume.domain.ResumeAnalysisJobInput(jobPosition, jobDescription, jobCareer)`, `resume.domain.ResumeAnalysisEvaluation(...)`, `resume.domain.DimensionScore(score, reason, improvements)` |
| 프롬프트/스키마 | `resume.tool.ResumeAnalysisPromptFragments`, `resume.tool.ResumeAnalysisSystemMessages`, `resume.tool.ResumeAnalysisToolNames`, `resume.tool.ResumeAnalysisEvaluationResultRenderer`, `resume.external.dto.ResumeAnalysisSchema`(public) |
| LLM 클라이언트 (4개) | `ResumeAnalysisEvaluationBedrockClient`, `ResumeAnalysisEvaluationGptClient`, `ResumeAnalysisQuestionBedrockClient`, `ResumeAnalysisQuestionGptClient` |
| 파싱 DTO | `ResumeAnalysisEvaluationFlatResponse`, `ResumeAnalysisQuestionsFlatResponse` |
| 서비스 | `ResumeAnalysisFacadeService`, `ResumeAnalysisService`, `ResumeAnalysisStateService`, `ResumeAnalysisAsyncService`, `ResumeAnalysisRecoveryScheduler`, `ResumeAnalysisCleanupScheduler` |
| 커맨드 | `ResumeAnalysisCommand` |
| Executor 빈 | `resumeAnalysisExecutor` (prefix `Async-Resume-Analysis-`) |
| 신규 예외 | `global.exception.ServiceUnavailableException`(503) |

### 0-3. 툴 이름 / 프로퍼티

| 종류 | 확정 값 |
|---|---|
| 평가 도구 | `submit_resume_analysis_evaluation` |
| 질문 도구 | `submit_resume_analysis_questions` |
| 평가 콜 | `aws.bedrock.resume-evaluation-max-tokens`(10000) + `evaluation-temperature`(0.2) |
| 질문 콜 | `aws.bedrock.resume-question-max-tokens`(2048) + `generation-temperature`(0.7) |
| 신규 프로퍼티 | **없음** (`BedrockConverseProperties`는 `@Validated` 전 필드 `@NotNull`이라 추가 시 test yml 동시 수정 없으면 전 통합테스트 기동 실패) |

### 0-4. 엔드포인트

| # | Method | Path | 인증 |
|---|---|---|---|
| 1 | POST | `/api/v1/resume-analyses` | `@Authentication(required = false)` + `ClientIp` |
| 2 | GET | `/api/v1/resume-analyses/{analysisId}` (`?guest_token=`) | `@Authentication(required = false)` |
| 3 | GET | `/api/v1/resume-analyses` | `@Authentication` |
| 4 | POST | `/api/v1/resume-analyses/claim` | `@Authentication` |
| 5 | POST | `/api/v1/resume-analyses/{analysisId}/questions/retry` (`?guest_token=`) | `@Authentication(required = false)` |
| 6 | GET | `/api/v1/resume-analyses/usage-status` | `@Authentication` |
| 7 | POST | `/api/v1/interviews/resume-analyses/{analysisId}` | `@Authentication` |

`analysisId` = `resume_analysis.id` (Long). **컨트롤러는 `@PathVariable String`으로 받아 직접 파싱**한다(비숫자 → 404). `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException` 핸들러를 추가하면 존치되는 무관 도메인 엔드포인트 다수(`interview`, `answer`, `member`, `payment`, `token`, `admin`)의 `@PathVariable Long` 응답 코드까지 500→400으로 바뀌므로 금지(§11-A).

**삭제 12개 / 존치 1개:**

| # | Method | Path | 컨트롤러 | 대체 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/resumes/evaluations` | `CareerMaterialsController` | `POST /api/v1/resume-analyses` |
| 2 | GET | `/api/v1/resumes/evaluations/{evaluationId}/state` | " | `GET /api/v1/resume-analyses/{analysisId}` |
| 3 | GET | `/api/v1/resumes/evaluations` | " | `GET /api/v1/resume-analyses` |
| 4 | GET | `/api/v1/resumes/evaluations/{evaluationId}` | " | #2 |
| 5 | POST | `/api/v1/interviews/resume-based/questions/generate` | `ResumeBasedInterviewController` | #1 (평가+질문 통합) |
| 6 | GET | `/api/v1/interviews/resume-based/usage-status` | " | `GET /api/v1/resume-analyses/usage-status` |
| 7 | GET | `/api/v1/interviews/resume-based/questions/generations` | " | #3 |
| 8 | GET | `/api/v1/interviews/resume-based/{id}/check` | " | #2 |
| 9 | GET | `/api/v1/interviews/resume-based/{id}` | " | #2의 `questions[]` |
| 10 | POST | `/api/v1/interviews/resume-based/{id}` | " | `POST /api/v1/interviews/resume-analyses/{analysisId}` |
| 11 | GET | `/api/v1/recruits/filters` | `RecruitController` | 없음 (폐기) |
| 12 | GET | `/api/v1/recruits` | " | 없음 (폐기) |
| — | GET | `/api/v1/resumes` | `CareerMaterialsController` | **존치** (`getCareerMaterials`) |

`CareerMaterialsController`는 실측 매핑 5개 → 1개로 축소되며 파일은 존치한다.

### 0-5. JSON 키

전역 `SNAKE_CASE` + `default-property-inclusion: non_null`. 이 문서의 "null"은 "키 부재"와 동의어다. 신규 DTO에 `@JsonProperty`를 쓰지 않는다.

`analysis_id`, `guest_token`, `state`, `jd_provided`, `interview_available`, `question_retryable`, `resume`, `portfolio`, `job_position`, `job_description`, `job_career`, `evaluation.{problem_solving,project_experience,technical_skills,soft_skills,jd_fit}.{score,weight,reason,improvements}`, `evaluation.total_score`, `evaluation.total_feedback`, `questions[].{generated_question_id,question_order,question,reason}`, `created_at`.

### 0-6. Redis 키 상수 (`ResumeAnalysisFacadeService`의 `public static final`)

| 상수 | 값 |
|---|---|
| `GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX` | `"guest:resume-analysis:started:"` |
| `GUEST_RESUME_ANALYSIS_LOCK_TTL` | `Duration.ofDays(365)` |
| `GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX` | `"guest:resume-analysis:attempt:"` |
| `GUEST_MAX_ATTEMPTS_PER_HOUR` | `5` |
| 회원 제출 락 | `@DistributedLock(prefix = "resume-analysis", key = "#memberId")` |
| 재시도 락 | `@DistributedLock(prefix = "resume-analysis-retry", key = "#analysisId")` |
| sweep 락 | `lock:resume-analysis:sweep:scheduler` (TTL 4분) |
| cleanup 락 | `lock:resume-analysis:cleanup:scheduler` (TTL 1시간) |

**테스트는 이 상수를 리터럴로 복제하지 않고 반드시 참조한다**(선례: `InterviewControllerTest:1408`).

---

## 1. 개요와 범위

### 1-1. 만드는 것

이력서 상세 분석(평가)과 이력서 기반 면접 질문 생성을 **하나의 리소스(`resume_analysis`)와 7개 엔드포인트로 통합하고, 구 두 플로우를 코드·테이블·문서에서 완전히 제거한다.** 하나의 제출이 LLM 2콜(평가 temp 0.2 → 질문 temp 0.7)을 순차 실행하고, 평가가 끝나는 즉시 폴링으로 평가 결과를 공개한 뒤 질문을 채운다. 게스트는 평가+질문 모두 무료로 받고 회원 귀속(claim) 후 면접을 시작한다.

| 축 | 내용 |
|---|---|
| 가산 | 신규 7개 엔드포인트 + 2테이블 + 신규 5지표 + 신규 프롬프트·스키마 |
| 삭제 | 구 엔드포인트 12개, 구 테이블 11개, **프로덕션 90파일 + 테스트 10파일 전삭제**, **프로덕션 Java 7 + 설정·문서 6 + 테스트 5파일 부분 삭제**, RestDocs identifier 22개 |

**과거 데이터는 이전하지 않는다.** `resume_evaluation`, `resume_question_generation`, `interview_type='RESUME_BASED'`와 그 후손 전부, `generated_question` 전량을 삭제한다. 사용자 관점의 "이력서 분석 이력"·"이력서 기반 면접 기록"은 릴리스 시점에 0건에서 다시 시작한다(§3-9).

### 1-2. 삭제하는 것과 존치하는 것 (M1~M5)

**D1·D2는 폐기됐다. "동결"은 이 문서에서 어떤 대상에도 더 이상 적용되지 않는다.** 구체적인 파일별 삭제·부분삭제 목록(프로덕션 90파일 + 테스트 10파일 전삭제, 프로덕션 Java 7 + 설정·문서 6 + 테스트 5파일 부분 삭제)은 구현 계획 문서에서 관리하며, 이 절에는 존치 대상만 남긴다.

| 존치 대상 | 근거 |
|---|---|
| `PdfUploadService`, `PdfValidator`, `PdfTextExtractor`, `CareerMaterialsPathResolver` | 신규 파사드가 사용 |
| `MemberResume`/`MemberPortfolio`(+리포지토리), `CareerMaterialsType`, `CareerMaterialsResponse` | 신규 파사드·존치 엔드포인트가 사용 |
| `resume/external/dto/ResumeGpt{Message,Response,ResponseMessage,Choice}` | `ResumeAnalysis{Evaluation,Question}Gpt{Request,Client}`가 실참조 |
| `resume/service/dto/{ResumeInfo,PortfolioInfo,ResumeResponse,PortfolioResponse}` | `ResumeAnalysisResponse`·`CareerMaterialsResponse` 구성요소. **`interview/service/dto/resumebased/{ResumeInfo,PortfolioInfo}`는 동명 별개 클래스이며 삭제 대상이다 — 혼동 금지** |
| `interview/external/dto/response/GeneratedQuestionDto` | `ResumeAnalysisQuestionResult`·`ResumeAnalysisQuestionsFlatResponse`가 사용 |
| `interview/service/resume/ResumeContentService` | 신규 파사드가 `getOrExtractResumeContent`/`getOrExtractPortfolioContent` 호출 |
| `interview` 테이블, `Interview`, `InterviewType`, `getDisplayQuestion()`/`getDisplayCategory()` | **M4: 0바이트 수정** |
| `@EnableScheduling`, `spring.task.scheduling.pool.size: 3` | 잔존 스케줄러 3개(`PaymentRecoveryScheduler`, `MemberSchedulerService`, `InterviewSchedulerService`) |
| `MySQLDatabaseCleaner`, `H2AutoIncrementCleaner` | 각각 `INFORMATION_SCHEMA` 동적 조회 / `getMetamodel()` 순회 → 수정 불필요 |
| Bedrock/GPT 프로퍼티 4개 (`resume-evaluation-max-tokens`, `resume-question-max-tokens`, `evaluation-temperature`, `generation-temperature`) | 전부 신규 클라이언트가 사용. `BedrockConverseProperties`/`GptProperties`가 `@Validated` record + 전 필드 `@NotNull`이라 **하나만 지워도 전 프로파일 기동 실패** |

### 1-3. 신규 플로우가 요구하는 기존 코드 가산 변경 (총 5곳)

삭제 변경은 구현 계획 문서가, M3(질문 저장 구조 전환)는 §3-4가 담당한다. 이 절은 **가산만** 다룬다.

1. `GeneratedQuestion.java`: `analysis` `@ManyToOne(LAZY, optional = false)` + `@JoinColumn(name="analysis_id", nullable=false)`, `@Index(name="idx_generated_question_analysis_id")`, 정적 팩토리 `forAnalysis(...)`.
2. `GeneratedQuestionRepository.java`: 메서드 4개(`findByAnalysisIdOrderByQuestionOrder`, `findByIdAndAnalysisId`, `deleteByAnalysisIdIn`, `countByAnalysisIdIn`). 상세는 §3-7.
3. `AsyncConfig`에 `resumeAnalysisExecutor` 빈 추가, `global/exception`에 `ServiceUnavailableException`(503) + 전용 핸들러.
4. `PdfTextExtractor.java`: `extractTextWithLinks(MultipartFile)` / `extractTextWithLinks(byte[])` 가산(§6-2-1). 기존 `extractText` 2개와 공유 private `extractText(PDDocument)`는 **여전히 0바이트 수정 — 근거는 D2가 아니라**, 그 메서드를 존치되는 `ResumeContentService`(신규 파사드의 저장-자료 텍스트 추출 경로)가 계속 쓰므로 고치면 신규 플로우의 LLM 입력이 하이퍼링크 유무로 두 갈래가 되기 때문이다.
5. `InterviewStartFacadeService.startResumeAnalysisInterview(...)` 가산 + 필드 2개(`resumeAnalysisService`, `generatedQuestionRepository`).

`recruit`은 어디서도 참조하지 않으며 이번에 완전 제거된다(§3-3). JD는 요청 파트 `job_description` 자유 텍스트가 유일한 소스다.

### 1-4. 구 기능 삭제와 배포 조율

**이 릴리스는 프론트엔드에 대해 breaking change다.** 구 12개가 404가 되고 과거 이력이 사라진다.

**A. 프론트 대응 목록**

| # | 구 호출 | 신규 | 형상 변화 |
|---|---|---|---|
| 1 | `POST /api/v1/resumes/evaluations` | `POST /api/v1/resume-analyses` | 파트에 `job_position`(필수)·`job_description`(선택) 추가. 응답 `{evaluation_id}` → `{analysis_id, guest_token?}` |
| 2 | `POST .../resume-based/questions/generate` | #1과 통합 | **별도 제출 소멸** |
| 3 | 폴링 2종(`.../state`, `.../check`) | `GET /api/v1/resume-analyses/{analysisId}` | 폴링과 상세가 동일 URL. `state` 값 집합 교체 |
| 4 | `GET .../evaluations/{id}` | #3과 통합 | 지표 키 2개 교체(`career_growth`→`soft_skills`, `documentation`→`jd_fit`). JD 미제공 시 `jd_fit` **키 부재**. bullets `\n` 문자열 → `List<String>` |
| 5 | 목록 2종 | `GET /api/v1/resume-analyses` | 페이지 응답 형상은 기존 관례 유지 |
| 6 | `GET .../resume-based/{id}` | #3의 `questions[]` | 왕복 소멸 |
| 7 | `POST .../resume-based/{id}` | `POST /api/v1/interviews/resume-analyses/{analysisId}` | **요청 바디·응답 무변경. path만 교체** |
| 8 | `GET .../resume-based/usage-status` | `GET /api/v1/resume-analyses/usage-status` | `is_first_use` → `first_use_free`, `token_cost` 추가 |
| 9 | 비회원 평가(`uuid-` 접두) | #1·#3 + `?guest_token=` | 저장소 Redis(TTL 5분) → DB 행. **게스트도 질문까지 무료** |
| 10 | `/api/v1/recruits*` | 없음 | 채용 공고 화면 전체 제거 |

**B. 배포 순서 — 두 시나리오. §10의 X-4 판정 전까지 어느 것도 확정이 아니다.**

| 시나리오 | 내용 | 마이그레이션 영향 |
|---|---|---|
| **1 (동시 배포, M1~M5 원안)** | 프론트·백엔드 동시 릴리스. 짧은 오류 창 또는 유지보수 창 | V51~V54 4개뿐. nullable/XOR 과도기 없음 |
| **2 (프론트 선행)** | ① 신규 가산 배포(구 12개 유지) → ② 프론트 전환 → ③ 구 경로 호출 0건 관측 → ④ 삭제 배포 | ①이 곧 하위호환이므로 **M3의 예외를 만드는 결정 — 인간 파트너 승인 필수**. 과도기 마이그레이션 1개 증가, 삭제가 두 PR·두 배포로 갈린다 |

어느 시나리오든 **마이그레이션 적용 시점에 구 인스턴스가 0대여야 한다**(롤링 금지). 근거는 §3-9-G(락·MDL).

**C. API 버저닝을 하지 않는 근거.** `/api/v2/...` + v1 유지는 폐기된 하위호환(D1)이며 구 테이블 DROP(M1)과 양립 불가하다. 경로 이름이 리소스 이름과 함께 바뀌므로(`resume-based` → `resume-analyses`) 버전 접두어 없이도 충돌하지 않는다.

---

## 2. API 계약

이 7개가 **이력서 관련 기능의 전부**다. 구 12개는 삭제되고(§0-4) `GET /api/v1/resumes`만 존치한다. 특히 **비로그인 사용자가 접근할 수 있는 이력서 경로는 #1 제출·#2 조회·#5 재시도 3개뿐이며, 구 비회원 평가 API(Redis `resume:evaluation:nonmember:{uuid}`, TTL 5분)가 사라지므로 게스트 결과의 저장소가 Redis에서 DB(`resume_analysis` + `guest_token`)로 완전히 이동한다.** 게스트 결과 수명이 5분 → 30일(§7-7 정리 스케줄러)로 늘고, 게스트도 평가에 더해 질문 생성까지 받는다.

### 2-1. #1 제출 — `POST /api/v1/resume-analyses` (multipart, 202)

| 파트 | 타입 | 필수 |
|---|---|---|
| `resume` | file(PDF) | `resume` 또는 `resume_id` 중 하나 |
| `resume_id` | Long | 위와 동일. **회원 전용** |
| `portfolio` | file(PDF) | 선택 |
| `portfolio_id` | Long | 선택. **회원 전용** |
| `job_position` | String ≤500 | **필수** |
| `job_description` | String ≤10,000 | **선택**(D4). 비공백이면 `jd_provided = true` |
| `job_career` | String ≤100 | **필수** |

```java
public record ResumeAnalysisSubmitRequest(
        MultipartFile resume, MultipartFile portfolio, Long resumeId, Long portfolioId,
        String jobPosition, String jobDescription, String jobCareer
) {
    private static final int JOB_POSITION_MAX_LENGTH = 500;
    private static final int JOB_DESCRIPTION_MAX_LENGTH = 10_000;
    private static final int JOB_CAREER_MAX_LENGTH = 100;

    public ResumeAnalysisSubmitRequest {
        if (isEmptyFile(resume) && resumeId == null) {
            throw new BadRequestException("이력서 파일 또는 이력서 ID는 필수입니다.");
        }
        validateRequiredLength(jobPosition, "지원 직무", JOB_POSITION_MAX_LENGTH);
        validateRequiredLength(jobCareer, "경력 사항", JOB_CAREER_MAX_LENGTH);
        validateOptionalLength(jobDescription, "채용 공고", JOB_DESCRIPTION_MAX_LENGTH);
    }

    public boolean hasSavedMaterialId() { return resumeId != null || portfolioId != null; }

    public boolean isJdProvided() { return jobDescription != null && !jobDescription.isBlank(); }
}
```

`isJdProvided()`는 **제출 시점 1회만** 호출되어 `resume_analysis.jd_provided`에 물화된다. 이후 프롬프트 분기(D6)·가중치 세트 선택(D5)·응답 `jd_provided`는 **전부 컬럼(또는 커맨드에 실린 그 값)만** 읽는다. 워커에서 `StringUtils.hasText(jobDescription)`로 재계산하는 것은 금지 — 재계산과 컬럼이 갈리면 4지표로 채점한 응답을 5지표 가중치로 합산하는 경로가 열린다.

응답: `202` + `ResumeAnalysisSubmitResponse(Long analysisId, String guestToken)`. 회원은 `guest_token` 키 부재.

### 2-2. #2 조회(폴링 = 상세) — `GET /api/v1/resume-analyses/{analysisId}`

폴링과 상세를 하나로 통일한다. 상태가 바뀐 순간 그 결과를 같은 URL에서 받으므로 D9의 단계적 공개가 왕복 1회로 완결되고, `state` 값의 소스가 1개로 유지된다.

```java
public record ResumeAnalysisResponse(
        Long analysisId,
        ResumeAnalysisState state,
        boolean jdProvided,
        boolean interviewAvailable,
        Boolean questionRetryable,
        ResumeInfo resume,
        PortfolioInfo portfolio,
        String jobPosition,
        String jobDescription,
        String jobCareer,
        ResumeAnalysisEvaluationResponse evaluation,
        List<ResumeAnalysisQuestionResponse> questions,
        LocalDateTime createdAt
) {
    public static ResumeAnalysisResponse of(ResumeAnalysis analysis, List<GeneratedQuestion> questions,
                                            boolean questionRetryable) { ... }
}

public record ResumeAnalysisEvaluationResponse(
        ResumeAnalysisDimensionResponse problemSolving,
        ResumeAnalysisDimensionResponse projectExperience,
        ResumeAnalysisDimensionResponse technicalSkills,
        ResumeAnalysisDimensionResponse softSkills,
        ResumeAnalysisDimensionResponse jdFit,
        Integer totalScore,
        String totalFeedback
) {
    public static ResumeAnalysisEvaluationResponse fromNullable(ResumeAnalysis analysis) {
        if (!analysis.getState().isEvaluationRevealed()) {
            return null;
        }
        ResumeAnalysisWeights weights = ResumeAnalysisWeights.of(analysis.isJdProvided());
        return new ResumeAnalysisEvaluationResponse(
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getProblemSolvingScore(),
                        weights.weightOf(PROBLEM_SOLVING), analysis.getProblemSolvingReason(),
                        analysis.getProblemSolvingImprovements()),
                // project_experience / technical_skills / soft_skills 동일 패턴
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getJdFitScore(),
                        weights.weightOf(JD_FIT), analysis.getJdFitReason(), analysis.getJdFitImprovements()),
                analysis.getTotalScore(), analysis.getTotalFeedback());
    }
}

public record ResumeAnalysisDimensionResponse(
        Integer score, Double weight, List<String> reason, List<String> improvements
) {
    public static ResumeAnalysisDimensionResponse fromNullable(Integer score, Double weight,
                                                              List<String> reason, List<String> improvements) {
        if (score == null || weight == null) {
            return null;                       // 차원 미산출 → 키 자체가 소멸
        }
        return new ResumeAnalysisDimensionResponse(score, weight, reason, improvements);
    }
}

public record ResumeAnalysisQuestionResponse(
        Long generatedQuestionId, Integer questionOrder, String question, String reason
) {
    public static ResumeAnalysisQuestionResponse from(GeneratedQuestion question) {
        return new ResumeAnalysisQuestionResponse(question.getId(), question.getQuestionOrder(),
                question.getContent(), question.getReason());
    }
}
```

- `score`는 반드시 `Integer`. primitive + `nullToZero`는 "차원 미산출"을 "0점"으로 오독시켜 D4를 표현 불가능하게 만든다(구 `TechnicalSkillsResponse`가 그 형태다).
- bullets는 `\n` join하지 않고 `List<String>`을 유지한다. DB(`JSON`)·툴 스키마(`minItems 2`)와 형이 일치하고, `joinWithNewline`의 null→`""` 치환이 `non_null` 정책을 무력화하는 문제가 사라진다.
- `jdProvided`/`interviewAvailable`은 primitive라 항상 키가 존재한다(클라이언트 분기의 안정된 기준점). `interviewAvailable = !isGuest() && state == COMPLETED`.
- `questionRetryable`은 `Boolean`. `state == QUESTION_FAILED && question_retry_count < 2 && 원문 사이드 테이블 존재`일 때만 값이 실린다.
- `questions`는 빈 리스트가 아니라 null(키 부재)로 내보낸다.

**필드 공개 매트릭스**

| 필드 | PENDING | EVALUATION_COMPLETED | COMPLETED | EVALUATION_FAILED | QUESTION_FAILED |
|---|---|---|---|---|---|
| `analysis_id`/`state`/`jd_provided`/`job_position`/`job_career`/`created_at` | 값 | 값 | 값 | 값 | 값 |
| `interview_available` | false | false | 회원 true / 게스트 false | false | false |
| `question_retryable` | 없음 | 없음 | 없음 | 없음 | true 또는 false |
| `job_description` | JD 있을 때만 | 동일 | 동일 | 동일 | 동일 |
| `resume`/`portfolio` | 회원+파일/저장자료일 때만 | 동일 | 동일 | 동일 | 동일 |
| **`evaluation`** | **없음** | 값 | 값 | **없음** | 값 |
| `evaluation.jd_fit` | — | JD 없으면 **없음** | JD 없으면 **없음** | — | JD 없으면 **없음** |
| **`questions`** | **없음** | **없음** | 값(5~7) | **없음** | **없음** |

가중치 표기(D5, 런타임 재정규화 없음):

| 차원 | JD 있음 `weight` | JD 없음 `weight` |
|---|---|---|
| `problem_solving` | 0.25 | 0.30 |
| `project_experience` | 0.25 | 0.30 |
| `technical_skills` | 0.25 | 0.30 |
| `soft_skills` | 0.10 | 0.10 |
| `jd_fit` | 0.15 | (키 부재) |

**조회 권한** — 게스트 행은 `guest_token` 일치를 요구한다. `analysis_id`가 순차 정수이므로 "식별자 지식 = 읽기 권한"을 그대로 쓰면 `GET /1..N` 열거로 타인의 이력서 평가 전문을 수집할 수 있다.

| 행 상태 | 요청자 | 결과 |
|---|---|---|
| `member_id IS NULL` | `guest_token` 일치 | 200 |
| `member_id IS NULL` | 토큰 없음/불일치 | 403 |
| `member_id = X` | 세션 X | 200 |
| `member_id = X` | 세션 Y 또는 비로그인 | **403** (`guest_token`을 제시해도 403) |
| 미존재 id / 비숫자 path | 무관 | 404 |

**`guest_token`의 인증 효력은 `member_id IS NULL` 동안만이다.** claim 후에는 세션 인증만 허용한다(컬럼은 `billing_required` 판정과 재claim 멱등 판정을 위해 남긴다). 이 가드가 없으면 claim된 뒤에도 localStorage·히스토리에 남은 옛 토큰으로 제3자가 남의 평가 전문을 열람하고 무과금 질문 재시도를 트리거할 수 있다.

```java
private void validateAccessible(ResumeAnalysis analysis, MemberAuth auth, String guestToken) {
    if (analysis.isGuest()) {
        if (!analysis.isSameGuestToken(guestToken)) {
            throw new ForbiddenException("본인의 이력서 분석만 조회할 수 있습니다.");
        }
        return;
    }
    if (!auth.isAuthenticated() || !analysis.isOwner(auth.memberId())) {
        throw new ForbiddenException("본인의 이력서 분석만 조회할 수 있습니다.");
    }
}
```

게스트 읽기에 `guest_ip` 일치는 요구하지 않는다(모바일 네트워크 전환 시 자기 결과를 못 보게 된다). `guest_ip`는 D12 남용 방어와 사후 추적 전용이다.

### 2-3. #3 목록 — `GET /api/v1/resume-analyses` (회원)

query: `state`(선택), `page`, `size`, `sort` — `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)`.
`state`는 **`String`으로 받아 서비스에서 파싱**하고 실패 시 `BadRequestException`으로 처리한다(전역 `MethodArgumentTypeMismatchException` 핸들러를 추가하지 않는 이유는 §2 서두와 동일).

```java
public record ResumeAnalysisSummaryResponse(
        Long analysisId, ResumeAnalysisState state, String jobPosition, String jobCareer,
        boolean jdProvided, Integer totalScore, Integer questionCount, LocalDateTime createdAt) {}

public record ResumeAnalysisPageResponse(
        List<ResumeAnalysisSummaryResponse> data, int currentPage, long totalCount,
        int totalPages, boolean hasNext) {

    public static ResumeAnalysisPageResponse of(List<ResumeAnalysisSummaryResponse> data, Page<?> page) {
        return new ResumeAnalysisPageResponse(data, page.getNumber(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }
}
```

`Page<?>`를 그대로 받는다(평가 플로우의 손계산은 0건일 때 `total_pages = 0`을 내보내는 결함이 있다). 목록은 `repository/dto` 프로젝션(`id, state, jobPosition, jobCareer, jdProvided, totalScore, createdAt`)으로 조회해 `job_description`·`total_feedback`(TEXT)과 JSON 10컬럼을 끌고 오지 않는다. `questionCount`는 `generatedQuestionRepository.countByAnalysisIdIn(ids)` **1회**로 병합한다(행당 N+1 금지).

### 2-4. #4 claim — `POST /api/v1/resume-analyses/claim` (회원, 200)

```java
public record ResumeAnalysisClaimRequest(
        @NotBlank(message = "게스트 토큰은 필수입니다.") String guestToken) {}

public record ResumeAnalysisClaimResponse(Long analysisId, ResumeAnalysisState state) {}
```

```java
@Transactional
public ResumeAnalysisClaimResponse claimGuestAnalysis(String guestToken, MemberAuth memberAuth) {
    Member member = memberService.readById(memberAuth.memberId());
    validateClaimQuota(member.getId());                          // 회원당 1건
    resumeAnalysisRepository.claimByGuestToken(member, guestToken);   // D10: UPDATE 한 줄
    ResumeAnalysis analysis = resumeAnalysisRepository.findByGuestToken(guestToken)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 이력서 분석입니다."));
    if (!analysis.isOwner(memberAuth.memberId())) {
        throw new ForbiddenException("이미 다른 회원에게 귀속된 이력서 분석입니다.");
    }
    return new ResumeAnalysisClaimResponse(analysis.getId(), analysis.getState());
}
```

| 케이스 | UPDATE 행수 | 결과 |
|---|---|---|
| 미claim 게스트 행 | 1 | 200 |
| 이미 같은 회원이 claim | 0 | 후속 SELECT로 자기 소유 확인 → **200 (멱등)** |
| 다른 회원이 claim한 행 | 0 | 403 |
| 없는 토큰 | 0 | 404 |
| 동시 claim 2건(같은 회원) | 1 / 0 | 행 배타 잠금으로 직렬화, 둘 다 200 |
| 동시 claim 2건(다른 회원) | 1 / 0 | 하나 200, 하나 403. 조건절 `member_id IS NULL`이 CAS 역할 → 분산 락 불필요 |
| `PENDING`/`EVALUATION_COMPLETED` 중 claim | 1 | 허용. 워커는 `analysisId`와 커맨드만 쓰고 `member_id`를 읽지 않는다 |

`validateClaimQuota`: `existsByMemberIdAndGuestTokenIsNotNull(memberId)`이면 `BadRequestException("이미 연결된 비회원 분석이 있습니다.")`. IP를 바꿔 게스트 무료 분석을 반복 수령하고 한 계정에 무한히 흡수하는 파밍 경로를 막는다. claim은 과금하지 않는다(제출 시 `billing_required = false`로 고정됐다).

claim URL·`analysis_id`는 claim 전후로 **바뀌지 않는다**(같은 행의 `member_id`만 채운다). 클라이언트는 claim 성공 후 `guest_token`을 폐기한다. 면접 시작에 `guest_token`을 받지 않는다(소유권 이전과 면접 생성은 실패 모드가 다르고, 결과만 보고 면접을 시작하지 않는 사용자도 claim이 완료돼야 한다).

### 2-5. #5 질문 재생성 — `POST /api/v1/resume-analyses/{analysisId}/questions/retry` (202)

```java
public record ResumeAnalysisQuestionRetryResponse(
        Long analysisId, ResumeAnalysisState state, int questionRetryCount) {}
```

허용 상태 `QUESTION_FAILED`만, 최대 2회, **무과금**, 게스트도 `guest_token`으로 허용. 상세는 §7-4.

### 2-6. #6 usage-status — `GET /api/v1/resume-analyses/usage-status` (회원)

```java
public record ResumeAnalysisUsageStatusResponse(boolean firstUseFree, int tokenCost) {}
```

`tokenCost`는 항상 5(D11). `is_first_use`라는 이름은 답습하지 않는다(`is_` 접두사 + boolean은 Jackson 왕복이 불안정해 기존 코드가 `@JsonProperty`로 방어해야 했다). 판정식은 §7-3.

### 2-7. #7 면접 시작 — `POST /api/v1/interviews/resume-analyses/{analysisId}` (회원, 201)

```java
public record ResumeAnalysisInterviewStartRequest(
        @NotNull(message = "질문 ID는 필수입니다.") Long generatedQuestionId,
        @NotNull(message = "최대 질문 개수는 필수입니다.") Integer maxQuestionCount,
        @NotNull(message = "면접 모드는 필수입니다.") InterviewMode mode) {}
```

와이어 형상(`generated_question_id`, `max_question_count`, `mode`)이 기존 `ResumeBasedInterviewStartRequest`와 동일하므로 프론트의 시작 요청 코드를 그대로 재사용한다. 응답은 `InterviewStartResponse`(Text/Voice) **무수정 재사용**.

처리 순서: 소유권(§2-2) → `state == COMPLETED` → `generatedQuestionRepository.findByIdAndAnalysisId(questionId, analysisId)` 귀속 검증 → `validateEnoughTokens(memberId, maxQuestionCount * mode.getRequiredTokenCount())` → `interview` INSERT(`generated_question_id`) + 첫 `question` 생성. `InterviewStartFacadeService.startResumeAnalysisInterview(...)`를 신규 메서드로 추가한다(기존 메서드 무수정). `interviewType`은 기존 `RESUME_BASED`를 재사용하고 enum에 상수를 추가하지 않는다.

`ResumeBasedInterviewService.readGeneratedQuestion(questionId, generationId)`는 **삭제된다.** 그 메서드의 마지막 검증이 `question.getGeneration().getId().equals(generationId)`이고 M3로 `generation` 필드가 사라지므로 재사용이 아니라 존재가 불가능하다. 신규는 2단계: `existsById(questionId)` false → 404(`존재하지 않는 질문입니다.`), true인데 `findByIdAndAnalysisId(questionId, analysisId)` empty → 400(`해당 이력서 분석에 속하지 않는 질문입니다.`).

`InterviewStartFacadeService` 최종 변경 (실측 기반):

```
삭제: import ForbiddenException                                  (validateGenerationOwnership 단독 사용)
삭제: import ...interview.domain.ResumeQuestionGeneration
삭제: import ...service.dto.resumebased.ResumeBasedInterviewStartRequest
삭제: import ...service.resume.ResumeBasedInterviewService
삭제: private final ResumeBasedInterviewService resumeBasedInterviewService;
삭제: startResumeBasedInterview(...)
삭제: validateGenerationOwnership(...)
삭제: validateGenerationCompleted(...)
존치: import GeneratedQuestion, InterviewType
존치: public 4개 — startInterview / startGuestInterview /
      static createGuestInterviewStartedLockKey / startRootQuestionCustomInterview
존치: private 3개 — resolveInterviewType / validateLiveCodingNotVoice /
      validateModeSupportedForRootQuestion
존치: 나머지 필드 전부, 선언 순서 불변
가산: resumeAnalysisService, generatedQuestionRepository 필드 2개 (redisService 뒤)
가산: startResumeAnalysisInterview + private 검증 3개
```

실측 public은 5개(삭제 후 **4개**)다 — `createGuestInterviewStartedLockKey`가 `public static`이므로 위 목록에 포함한다. `@RequiredArgsConstructor`이므로 생성자는 자동 갱신된다(−1 +2). `InterviewType`은 무수정(M4).

### 2-8. 게스트 → claim → 면접 전체 시퀀스

| 단계 | 주체 | 요청 | 서버 | 응답 |
|---|---|---|---|---|
| 1 | 게스트 | `POST /api/v1/resume-analyses` | 시도 카운터 → PDF 검증 → 추출(세마포어) → **365일 락 획득** → `PENDING` INSERT(REQUIRES_NEW 커밋) → executor 제출 | 202 `{analysis_id, guest_token}` |
| 2 | 게스트 | `GET /{id}?guest_token=` | `member_id IS NULL` + 토큰 일치 | 200 `PENDING` |
| 3 | 게스트 | 동일 | 평가 커밋 후 | 200 `EVALUATION_COMPLETED` + `evaluation` |
| 4 | 게스트 | 동일 | 질문 커밋 후 | 200 `COMPLETED` + `questions`, `interview_available=false` |
| 5 | 프론트 | — | `interview_available=false` → 로그인 유도. `analysis_id` + `guest_token` 보관 | — |
| 6 | 회원 | OAuth 로그인 | 세션 발급 | — |
| 7 | 회원 | `POST /claim` `{guest_token}` | `UPDATE ... SET member_id=? WHERE guest_token=? AND member_id IS NULL` | 200 `{analysis_id(1단계와 동일), state}` |
| 8 | 회원 | `GET /{id}` (토큰 없이) | 세션 일치 | 200 `interview_available=true` |
| 9 | 회원 | `POST /api/v1/interviews/resume-analyses/{id}` | §2-7 | 201 `InterviewStartResponse` |

### 2-9. 에러 규약

본문은 전 구간 `ErrorResponse(String message)`.

| 규칙 | 예외 | 상태 |
|---|---|---|
| R1 미존재 / 식별자 형식 오류 | `NotFoundException` | 404 |
| R2 소유자 아님(주인 있는 리소스에 비로그인 포함) | `ForbiddenException` | 403 |
| R3 요청값·상태·정책 위반 | `BadRequestException` | 400 |
| R4 서버 용량 부족 | `ServiceUnavailableException` | 503 |

| # | 상황 | 상태 | 메시지 |
|---|---|---|---|
| 1 | `analysisId` 비숫자/미존재 | 404 | `존재하지 않는 이력서 분석입니다.` |
| 2 | 회원 소유 분석을 타인/비로그인 조회 | 403 | `본인의 이력서 분석만 조회할 수 있습니다.` |
| 3 | 게스트 행에 토큰 없음/불일치 | 403 | 동일 |
| 4 | 목록·claim·usage-status·면접시작에 세션 없음 | 401 | `로그인이 필요합니다` (리졸버 기존 문구) |
| 5 | 이력서 파일·ID 둘 다 없음 | 400 | `이력서 파일 또는 이력서 ID는 필수입니다.` |
| 6~10 | `job_position`/`job_career` 누락·초과, `job_description` 초과 | 400 | `지원 직무는 필수입니다.` / `경력 사항은 필수입니다.` / `지원 직무는 500자를 초과할 수 없습니다.` / `경력 사항은 100자를 초과할 수 없습니다.` / `채용 공고는 10000자를 초과할 수 없습니다.` |
| 11 | PDF 아님 / 빈 파일 / 페이지 수 초과 | 400 | `PdfValidator` 메시지 |
| 12 | 파일 10MB 초과 | **500** | `spring.servlet.multipart.max-file-size: 10MB`에 걸려 `MaxUploadSizeExceededException` → 전역 `Exception` 핸들러. `PdfValidator`의 50MB 상한은 도달 불가 상수다. 400으로 내리려면 전역 핸들러 추가가 필요하며, 채택 여부는 인간 판정 대상이다(§10 X-7) |
| 13 | `resume_id`/`portfolio_id` 형식 오류 | 400 | `잘못된 ID 형식입니다: {값}` |
| 14 | 게스트가 저장 자료 ID 사용 | 400 | `비회원은 저장된 이력서를 사용할 수 없습니다.` |
| 15 | 게스트 2회째 제출(락 실패) | 400 | `비회원 이력서 분석은 1회만 가능합니다.` |
| 16 | 게스트 시간당 시도 초과 | 400 | `요청이 너무 많습니다. 잠시 후 다시 시도해주세요.` |
| 17 | 회원 진행 중 분석 존재 | 400 | `이미 진행 중인 이력서 분석이 있습니다.` |
| 18 | 회원 토큰 부족 | 400 | 기존 `useTokens`/`validateEnoughTokens` 문구 |
| 19 | 텍스트 추출 실패 | 400 | `이력서 PDF에서 텍스트를 추출할 수 없습니다.` |
| 20 | 추출 동시 실행 한도 초과 / executor 포화 | 503 | `이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.` |
| 21 | claim `guest_token` 공백 | 400 | `게스트 토큰은 필수입니다.` |
| 22 | claim 토큰 미존재 | 404 | `존재하지 않는 이력서 분석입니다.` |
| 23 | claim 대상이 다른 회원 소유 | 403 | `이미 다른 회원에게 귀속된 이력서 분석입니다.` |
| 24 | claim 이미 본인 소유 | 200 | (멱등) |
| 25 | claim 회원당 한도 초과 | 400 | `이미 연결된 비회원 분석이 있습니다.` |
| 26 | 면접 시작: 미claim 게스트 행 | 400 | `먼저 이력서 분석을 내 계정에 연결해야 합니다.` |
| 27 | 면접 시작: `state != COMPLETED` | 400 | `질문 생성이 완료되지 않았습니다.` |
| 28 | 면접 시작: 질문 미존재 | 404 | `존재하지 않는 질문입니다.` |
| 29 | 면접 시작: 질문이 이 분석 소속 아님 | 400 | `해당 이력서 분석에 속하지 않는 질문입니다.` |
| 30 | 면접 시작: 토큰 부족 / `max_question_count` 범위 | 400 | 기존 문구 |
| 31 | 재시도: `state != QUESTION_FAILED` | 400 | `질문 재생성이 필요한 상태가 아닙니다.` |
| 32 | 재시도: 상한 초과 또는 원문 만료 | 400 | `질문 재생성 가능 횟수를 초과했습니다.` |
| 33 | 목록 `state` 파싱 실패 | 400 | `잘못된 상태 값입니다: {값}` |
| 34 | LLM 실패 | 200 | 예외 아님. `state = EVALUATION_FAILED` / `QUESTION_FAILED` |

`ServiceUnavailableException extends KokomenException(message, HttpStatus.SERVICE_UNAVAILABLE)`이므로 `handleKokomenException`이 자동 매핑하지만, 용량 포화는 즉시 알람 대상이라 `log.warn`에 묻히지 않게 **전용 핸들러를 추가해 `log.error` + 카운터 메트릭**으로 올린다(기존 예외의 처리 경로는 불변).

---

## 3. 데이터 모델과 마이그레이션

### 3-1. 구조 결정 요약

| 항목 | 결정 | 근거 |
|---|---|---|
| 5지표 | `resume_analysis`의 **flat 15컬럼** | 자식 테이블이 해결하려던 두 문제는 이미 다른 층에서 해소된다 — 17 위치인자는 `completeEvaluation(ResumeAnalysisEvaluation)` 1인자로, "0점 vs 미산출"은 `ResumeAnalysisDimensionResponse.fromNullable`로. 반면 자식 테이블은 `weight_percent`를 가중치의 두 번째 소스로 만들고, "정확히 4행 또는 5행"을 DB가 보장하지 못하며, 고빈도 폴링에 조인을 추가한다 |
| `{dim}_reasoning` | **영속화하지 않는다** | 파싱 DTO에 필드를 선언하지 않으므로 값이 엔티티에 도달할 경로가 없다. 컬럼을 만들면 영구 NULL이 된다 |
| `weight_percent` | **컬럼 없음** | 가중치는 `ResumeAnalysisWeights` 한 곳에만 수치로 존재한다. 응답 `weight`도 그 값을 그대로 쓴다 |
| 원문 텍스트 | **1:1 사이드 테이블 `resume_analysis_source_text`** | 부모 행에 LONGTEXT를 두면 2초 간격 폴링이 매번 수백 KB를 끌고 온다(JPA 기본 매핑은 지연 로딩 불가). 사이드 테이블이면 폴링은 부모만 읽고, 질문 재시도는 재추출·S3 재다운로드 없이 동작한다. 게스트는 보관처가 여기밖에 없다(`member_resume.member_id NOT NULL`이라 게스트 행 생성 불가) |
| 질문 | **기존 `generated_question` 재사용, 부모는 `resume_analysis` 단일** | `interview.generated_question_id` FK(V38)와 `Interview(Member, GeneratedQuestion, …)` 생성자를 그대로 쓰기 위함(M4). 구 부모를 남기지 않으므로 nullable FK 2개와 XOR CHECK가 불필요하고, `analysis_id NOT NULL` + `@JoinColumn(nullable=false)`이 "부모는 항상 존재"를 DB·엔티티 양쪽에서 강제한다 |
| 구 질문 행 | **전량 삭제** (`DELETE FROM generated_question`) | 이 테이블의 모든 행은 구 `resume_question_generation` 플로우 산물이고 그 부모가 DROP된다(M1). 남길 행이 정의상 0이라 NOT NULL 승격이 가능하다 |
| 과금 표시 | `charged_token_count SMALLINT` **하나** | boolean과 int를 병존시키지 않는다. 선점은 `WHERE charged_token_count = 0` CAS |
| `failure_reason` | **enum + VARCHAR(30)** | 자유 텍스트로 두면 200자 넘는 SDK 예외 메시지에서 `Data too long` → **실패 기록 트랜잭션 자체가 롤백**되어 행이 `PENDING`에 남는다. 상세 메시지는 로그로만 |
| `state` 길이 | VARCHAR(30) | `EVALUATION_COMPLETED`가 정확히 20자라 관례인 VARCHAR(20)은 여유 0 |
| `updated_at` | **만들지 않는다** | `BaseEntity`에 매핑이 없다. `resume_evaluation`이 만들어 놓고 매핑하지 않은 죽은 컬럼을 반복하지 않는다. 단계 시각은 `evaluation_completed_at`/`question_started_at`/`completed_at` 명시 컬럼으로만 |
| 엔티티명 = 테이블명 | 강제 | `H2AutoIncrementCleaner`가 `EntityType.getName()`을 스네이크 변환해 `ALTER TABLE {name} ALTER COLUMN ID RESTART WITH 1`을 돌린다. 불일치하거나 **`id` 컬럼이 없으면** `docs` 프로파일(`InterviewDocsTest`, `InterviewDocsV2Test`)이 `@BeforeEach`에서 즉사한다 → `resume_analysis_source_text`도 `id` AUTO_INCREMENT PK + `analysis_id` UNIQUE 구조로 만든다 |

### 3-2. 마이그레이션 파일 구성과 `V51`·`V52` 전문

**4분할의 이점은 "롤백 불가 구간의 격리"가 아니라 "실패 지점의 문장 단위 특정과 단계 적용 가능성"이다.** 비가역 구간은 V52·V53·V54 **세 개**이며 V51만 가역이다(§3-9-B). 단일 파일(약 32문장, 그중 14문장이 비가역)은 20번째 문장에서 실패하면 반쯤 뜯긴 스키마 + `success = 0` 이력을 남겨 `flyway repair` 없이 재시도가 불가능하다. MySQL은 DDL 트랜잭션을 지원하지 않으므로(`supportsDdlTransactions() == false`) DML과 DDL을 한 파일에 섞으면 첫 DDL의 암묵 커밋 지점에서 DML의 원자성이 끊긴다.

**단계 적용은 Gradle 플러그인이 아니라 Spring 프로퍼티로 한다.** `build.gradle`에 `org.flywaydb.flyway` 플러그인이 없고 의존성은 `flyway-core`/`flyway-mysql` 11.9.1 **라이브러리뿐**이다(실측). `./gradlew flywayMigrate`는 `Task 'flywayMigrate' not found`로 즉사한다.

```bash
# 1차 기동: 퍼지까지만 적용하고 멈춘다
SPRING_FLYWAY_TARGET=53 <배포 커맨드>
# -> §3-9-E 감사 쿼리 재실행, 삭제 결과를 기대치와 대조

# 2차 기동: 나머지(V54) 적용
unset SPRING_FLYWAY_TARGET     # 또는 SPRING_FLYWAY_TARGET=latest
```

**단계 적용은 배포를 두 번 하는 것과 같다.** 두 번 배포할 수 없다면 4분할의 운영상 이점은 실패 지점 특정으로 국한된다.

의존성:

```
V51 CREATE + 과도기 ALTER (M1 build)   의존: 없음  (원복·무변경)
V52 recruit 제거 (M5)                  의존: 없음
V53 DML 퍼지 (M2)                      의존: 없음
V54 M3 + 구 테이블 DROP (M1 teardown)  의존: V51(부착 대상) + V53(0행 보장)
```

**`V51__create_resume_analysis.sql` — 원복·무변경.** `git show d1eae65:src/main/resources/db/migration/V51__create_resume_analysis.sql` 로 복원한 97줄이며 편집 금지(checksum `-2144793090` 보존이 재기동 0회의 전제, §3-8). 전문:

```sql
-- 이력서 통합 분석(평가 + 질문 생성) 테이블
-- member_id NULL = 게스트. guest_token으로 소유를 식별하고, 회원가입 시 claim으로 member_id를 채운다.
-- 지표는 flat 컬럼이며, JD 미제공 시 jd_fit_* 3개는 NULL로 남는다(미산출과 0점을 DTO 경계에서 구분한다).
CREATE TABLE resume_analysis
(
    id                              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id                       BIGINT       NULL,
    guest_token                     CHAR(36)     NULL,
    guest_ip                        VARCHAR(45)  NULL,
    guest_lock_value                CHAR(36)     NULL,
    member_resume_id                BIGINT       NULL,
    member_portfolio_id             BIGINT       NULL,
    job_position                    VARCHAR(500) NOT NULL,
    job_description                 TEXT         NULL,
    job_career                      VARCHAR(100) NOT NULL,
    jd_provided                     BOOLEAN      NOT NULL,
    state                           VARCHAR(30)  NOT NULL,
    failure_reason                  VARCHAR(30)  NULL,
    problem_solving_score           INT          NULL,
    problem_solving_reason          JSON         NULL,
    problem_solving_improvements    JSON         NULL,
    project_experience_score        INT          NULL,
    project_experience_reason       JSON         NULL,
    project_experience_improvements JSON         NULL,
    technical_skills_score          INT          NULL,
    technical_skills_reason         JSON         NULL,
    technical_skills_improvements   JSON         NULL,
    soft_skills_score               INT          NULL,
    soft_skills_reason              JSON         NULL,
    soft_skills_improvements        JSON         NULL,
    jd_fit_score                    INT          NULL,
    jd_fit_reason                   JSON         NULL,
    jd_fit_improvements             JSON         NULL,
    total_score                     INT          NULL,
    total_feedback                  TEXT         NULL,
    billing_required                BOOLEAN      NOT NULL DEFAULT FALSE,
    charged_token_count             SMALLINT     NOT NULL DEFAULT 0,
    token_charge_failed             BOOLEAN      NOT NULL DEFAULT FALSE,
    question_retry_count            INT          NOT NULL DEFAULT 0,
    evaluation_completed_at         DATETIME(6)  NULL,
    question_started_at             DATETIME(6)  NULL,
    completed_at                    DATETIME(6)  NULL,
    created_at                      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- (member_id, created_at): 회원 목록 조회 + 진행 중 1건 검사 + 첫 사용 판정 + claim 한도 검사를 모두 커버한다.
    -- leftmost가 member_id라서 아래 member FK가 요구하는 인덱스도 이것이 겸한다.
    KEY idx_resume_analysis_member_id_created_at (member_id, created_at),
    -- 잔류 PENDING 회수: WHERE state = 'PENDING' AND created_at < ?
    KEY idx_resume_analysis_state_created_at (state, created_at),
    -- 잔류 질문 단계 회수: WHERE state = 'EVALUATION_COMPLETED' AND question_started_at < ?
    KEY idx_resume_analysis_state_question_started_at (state, question_started_at),
    CONSTRAINT uk_resume_analysis_guest_token UNIQUE (guest_token),
    CONSTRAINT fk_resume_analysis_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_resume_analysis_member_resume FOREIGN KEY (member_resume_id) REFERENCES member_resume (id),
    CONSTRAINT fk_resume_analysis_member_portfolio FOREIGN KEY (member_portfolio_id) REFERENCES member_portfolio (id),
    -- XOR가 아니라 OR. claim은 member_id만 채우고 guest_token을 지우지 않으므로 claim 후에는 둘 다 존재한다.
    CONSTRAINT chk_resume_analysis_owner CHECK (member_id IS NOT NULL OR guest_token IS NOT NULL),
    CONSTRAINT chk_resume_analysis_scores CHECK (
        (problem_solving_score    IS NULL OR problem_solving_score    BETWEEN 0 AND 100) AND
        (project_experience_score IS NULL OR project_experience_score BETWEEN 0 AND 100) AND
        (technical_skills_score   IS NULL OR technical_skills_score   BETWEEN 0 AND 100) AND
        (soft_skills_score        IS NULL OR soft_skills_score        BETWEEN 0 AND 100) AND
        (jd_fit_score             IS NULL OR jd_fit_score             BETWEEN 0 AND 100))
);

-- 추출 원문. 폴링이 부모 행을 고빈도로 읽으므로 LONGTEXT를 부모에서 분리한다.
-- 질문 재생성(무과금)이 재추출·S3 재다운로드 없이 동작하는 근거이며, 게스트의 유일한 원문 보관처다.
-- id 컬럼은 H2AutoIncrementCleaner(docs 프로파일)가 ALTER TABLE ... ALTER COLUMN ID를 요구하므로 필수다.
CREATE TABLE resume_analysis_source_text
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    analysis_id       BIGINT      NOT NULL,
    resume_content    LONGTEXT    NOT NULL,
    portfolio_content LONGTEXT    NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rast_analysis_id UNIQUE (analysis_id),
    CONSTRAINT fk_rast_analysis FOREIGN KEY (analysis_id) REFERENCES resume_analysis (id) ON DELETE CASCADE
);

-- 질문은 기존 generated_question을 재사용한다.
-- interview.generated_question_id FK(V38)와 Interview(Member, GeneratedQuestion, ...) 생성자를 그대로 쓰기 위한
-- 결정이며, interview 테이블·Interview 엔티티·InterviewType·getDisplayQuestion()은 0바이트 수정한다.
-- 이 시점의 generated_question은 여전히 구 플로우와 공유 상태다(부모 2개, XOR). V54가 이를 단일 부모로 정리한다(§3-4).
ALTER TABLE generated_question MODIFY COLUMN generation_id BIGINT NULL;
ALTER TABLE generated_question ADD COLUMN analysis_id BIGINT NULL;

-- FK 추가 전에 인덱스를 먼저 만들어 MySQL의 자동 인덱스 생성을 피한다.
CREATE INDEX idx_generated_question_analysis_id ON generated_question (analysis_id);

ALTER TABLE generated_question
    ADD CONSTRAINT fk_generated_question_analysis FOREIGN KEY (analysis_id) REFERENCES resume_analysis (id);

-- 부모는 정확히 하나(구 플로우 = generation_id, 신규 = analysis_id).
-- 기존 행은 전부 generation_id NOT NULL / analysis_id NULL 이므로 무중단 통과한다.
ALTER TABLE generated_question
    ADD CONSTRAINT chk_generated_question_parent
        CHECK ((generation_id IS NULL) <> (analysis_id IS NULL));
```

`technical_skills_reason` 줄의 JSON 키워드 정렬 1칸 어긋남은 **교정하지 않는다** — 교정하면 checksum이 바뀌어 컨테이너 재기동이 필요해지고 "Task 1 무변경"의 기계적 증명(`git diff d1eae65` 빈 출력)이 깨진다(영구 보존 확정, §10 X-8).

`generated_question.analysis_id` FK에 `ON DELETE CASCADE`를 **걸지 않는다** — `interview`가 이 행을 참조할 수 있어 삭제는 항상 명시적·검증적이어야 한다(§7-6).

**`V52__drop_recruit_domain.sql` (M5) 전문:**

```sql
-- ============================================================================
-- M5: recruit 도메인 완전 제거. 크롤링 서비스가 중단되어 코드 참조 0건이 되었다.
--
-- 패키지 밖 참조 0건 검증됨(AwsConfig의 Region은 software.amazon.awssdk.regions.Region으로 무관).
--
-- !! 비가역 !! 역마이그레이션이 없다. 재크롤링도 RecruitmentScheduler가 삭제되어 불가하다.
--
-- DROP 순서는 자식부터다. recruit를 참조하는 inbound FK는 M5가 센 4개가 아니라 5개다
-- (information_schema.key_column_usage 실측):
--   recruit_region.recruit_id        [fk_recruit_region_recruit]
--   recruit_employee_type.recruit_id [fk_recruit_employee_type_recruit]
--   recruit_education.recruit_id     [fk_recruit_education_recruit]
--   recruit_employment.recruit_id    [fk_recruit_employment_recruit]
--   ocr_waiting_list.recruit_id      [fk_ocr_recruit]   <-- V25:7 생성, V26에서 개명
--
-- ocr_waiting_list를 함께 지운다: Java 엔티티·리포지토리·픽스처가 0건인 고아 테이블이고
-- recruit 기업 이미지 OCR 대기열이라는 유일한 용도가 recruit와 함께 사라진다.
-- 남기면 DROP TABLE recruit이 ERROR 3730으로 죽는다.
-- (M5 목록의 집계 누락이며 M5의 결정이 아니다. 변형 B는 이 파일 하단 주석 참조.)
--
-- crawling_request는 FK가 전혀 없는 독립 테이블이지만 recruit 크롤링 전용이므로 같이 지운다.
-- 역시 Java 엔티티 0건(실측).
--
-- affiliate/company에 대한 inbound FK는 fk_recruit_affiliate/fk_recruit_company(V22:34-35)
-- 둘뿐이며 recruit DROP으로 함께 사라진다.
--
-- 기존 마이그레이션 V22·V24·V25·V26은 지우지 않는다. 이 파일이 DROP을 담당한다.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. recruit의 자식 5개. 서로 독립이므로 이 5개 사이의 순서는 무관하다.
-- ---------------------------------------------------------------------------
DROP TABLE recruit_education;
DROP TABLE recruit_employee_type;
DROP TABLE recruit_employment;
DROP TABLE recruit_region;
DROP TABLE ocr_waiting_list;

-- ---------------------------------------------------------------------------
-- 2. recruit. 이 시점에 inbound FK가 0이다.
-- ---------------------------------------------------------------------------
DROP TABLE recruit;

-- ---------------------------------------------------------------------------
-- 3. recruit의 부모 2개. recruit가 사라졌으므로 inbound FK가 0이다.
-- ---------------------------------------------------------------------------
DROP TABLE affiliate;
DROP TABLE company;

-- ---------------------------------------------------------------------------
-- 4. crawling_request. FK 없음, 독립.
-- ---------------------------------------------------------------------------
DROP TABLE crawling_request;
```

**변형 B(테이블 존치)를 택할 경우 1단계를 아래로 교체한다.** `ocr_waiting_list.recruit_id`가 `NOT NULL`(실측)이라 컬럼을 남기면 신규 INSERT가 불가하므로 컬럼도 함께 뗀다. 이때 §0-1의 기대 테이블 총수는 20이 아니라 **21**이 된다.

```sql
ALTER TABLE ocr_waiting_list DROP FOREIGN KEY fk_ocr_recruit;
ALTER TABLE ocr_waiting_list DROP COLUMN recruit_id;

DROP TABLE recruit_education;
DROP TABLE recruit_employee_type;
DROP TABLE recruit_employment;
DROP TABLE recruit_region;
```

인덱스 정당화:

| 쿼리 | 사용 인덱스 |
|---|---|
| 회원 목록 `WHERE member_id = ? ORDER BY created_at DESC` | `idx_resume_analysis_member_id_created_at` |
| 진행 중 1건 `WHERE member_id = ? AND state IN (...) AND created_at > ?` | 동일 |
| 첫 사용 판정 / claim 한도 `WHERE member_id = ? AND guest_token IS (NOT) NULL` | 동일(등가 범위 후 필터) |
| 게스트 폴링·claim `WHERE guest_token = ?` | `uk_resume_analysis_guest_token` |
| 잔류 회수 2종 | `idx_resume_analysis_state_created_at`, `idx_..._state_question_started_at` |
| 게스트 정리 `WHERE member_id IS NULL AND created_at < ?` | `idx_resume_analysis_member_id_created_at`(InnoDB는 NULL도 인덱싱하고 선두에 모인다) |
| 질문 조립·귀속검증·집계 | `idx_generated_question_analysis_id` |
| 폴링 단건 | PK |

`guest_ip`에 인덱스를 만들지 않는다 — 소유 판정은 `guest_token`, 1회 제한은 Redis 락이므로 IP로 조회하는 쿼리가 없다.

`MySQLDatabaseCleaner`는 `INFORMATION_SCHEMA.TABLES`를 동적으로 훑고 `SET FOREIGN_KEY_CHECKS = 0` 후 TRUNCATE하므로 신규 2개 테이블이 자동 포함된다(수정 불필요).

### 3-3. recruit 도메인 제거 서술

**A. 코드 — 프로덕션 33파일 / 테스트 4파일 전삭제.** `src/main/java/com/samhap/kokomen/recruit/` 패키지 전체(`controller` 1, `domain` 9, `repository` 3, `service` 6, `schedular` 14) + `RecruitControllerTest` + `global/fixture/recruit/` 3파일. 빈 디렉터리 2개 제거.

**B. 함께 지울 것 / 존치할 것**

| 대상 | 처분 | 실측 근거 |
|---|---|---|
| `RecruitmentScheduler` | 삭제 | `@Scheduled`가 이미 주석 처리 — 활성 등록 0건이므로 파일 삭제로 끝 |
| `KokomenApplication`의 `@EnableScheduling` | **존치** | 잔존 스케줄러 3개 사용 |
| `spring.task.scheduling.pool.size: 3` | **존치** | 동일(§10 X-7 A안) |
| 별도 `SchedulingConfig`/`TaskSchedulerCustomizer` | 없음 | `global/config/` 8파일 전수 확인 |
| `aws.company-s3-path` (5파일 5줄) | 삭제 | 유일 소비자가 `RecruitPathResolver`의 `@Value` |
| 외부 API 키/URL 프로퍼티 | 없음 | `RecruitmentApiClient`가 `BASE_URL`을 코드에 하드코딩, `@Value` 0건 |
| 기존 마이그레이션 V22·V24·V25·V26 | **존치** | 새 DROP 마이그레이션(V52)으로 처리 |
| RestDocs identifier 7개 | 삭제 | §8-6 |

**C. 코드와 마이그레이션은 같은 커밋이어야 한다.** 엔티티를 남기면 `docs` 프로파일의 `create-drop`이 H2에 recruit 테이블을 만들어 마이그레이션과 스키마가 갈리고, `RecruitControllerTest`를 남기면 `test` 프로파일에서 "table doesn't exist"로 실패한다.

`ocr_waiting_list` 처리(변형 A: 테이블째 DROP 대 변형 B: FK·컬럼만 제거하고 테이블 존치)는 인간 판정 대상이다(§10 X-1). 기본값은 A다.

### 3-4. `V53`·`V54` 전문과 엔티티 동반 변경

**질문은 기존 `generated_question` 재사용, 부모는 `resume_analysis` 단일이다.** `interview.generated_question_id` FK(V38)와 `Interview(Member, GeneratedQuestion, …)` 생성자를 그대로 쓰기 위함(M4). 구 부모를 남기지 않으므로 nullable FK 2개와 XOR CHECK가 불필요하고, `analysis_id NOT NULL` + `@JoinColumn(nullable=false)`이 "부모는 항상 존재"를 DB·엔티티 양쪽에서 강제한다. **구 질문 행은 전량 삭제**(`DELETE FROM generated_question`) — 이 테이블의 모든 행은 구 `resume_question_generation` 플로우 산물이고 그 부모가 DROP된다(M1). 남길 행이 정의상 0이라 NOT NULL 승격이 가능하다.

**`V53__purge_resume_based_interviews.sql` (M2) 전문:**

```sql
-- ============================================================================
-- M2: 과거 이력서 기반 면접 기록 전량 삭제.
--
-- 이 파일은 순수 DML이다. DDL을 한 문장도 섞지 않는다 -- MySQL은 DDL에서 암묵 커밋하므로
-- DDL이 섞이면 이 블록의 원자성이 첫 DDL 지점에서 끊긴다. DDL은 V52·V54가 담당한다.
--
-- !! 비가역 !! 역마이그레이션이 없다. 적용 전 논리 백업과 §3-9 사전 점검 전량 통과가 필수다.
--
-- 모든 DELETE는 멱등이다(같은 WHERE를 다시 돌리면 0행 삭제). 중간 실패 시
-- flyway repair 후 이 파일을 재실행하면 수렴한다. 안전성 근거를 트랜잭션에 두지 않는 이유는
-- MySQL에서 Flyway가 순수 DML 마이그레이션을 단일 트랜잭션으로 감싸는지 미확인이기 때문이다.
--
-- 삭제 순서는 자식부터다. InnoDB의 FK 검사는 문장 단위 즉시 검사이며 지연(deferred)이 없으므로
-- 순서를 바꾸면 ERROR 1451(Cannot delete or update a parent row)로 죽는다.
--
-- 검증된 연쇄 트리 (information_schema.key_column_usage + 전 마이그레이션 FK 전수 추출, 누락 0):
--     interview ├─ interview_like (interview_id)              [V6]
--               └─ question (interview_id)                    [V1]
--                    └─ answer (question_id)                  [V1]
--                         ├─ answer_like (answer_id)
--                         └─ answer_memo (answer_id)
--     generated_question <- interview.generated_question_id    [V38]
--     (resume_based_root_question[V33]은 V36:17에서 이미 DROP됐다)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 락 범위 축소 및 실패 시점 단축.
--
--    innodb_lock_wait_timeout 기본값은 50초, lock_wait_timeout(MDL) 기본값은 31536000초(1년)다.
--    걸어 두지 않으면 락 대기가 사실상 무한이 되어 뒤따르는 모든 쿼리가 큐에 쌓인다.
--
--    아래 DELETE들은 서브쿼리 소스 테이블(interview / question / answer)의 인덱스 구간에
--    공유 넥스트키 락을 건다(performance_schema.data_locks 실측: 소스 테이블에 S 락 3건,
--    대상 테이블에 X,GAP 2건). READ COMMITTED로 내리면 갭 락이 사라진다
--    (X,GAP -> X,REC_NOT_GAP, S -> S,REC_NOT_GAP).
--
--    !! 확인 필요 !! MySQL 문서에 따르면 SET SESSION TRANSACTION ISOLATION LEVEL은
--    트랜잭션 내에서 허용되지만 "현재 진행 중인 트랜잭션에는 영향을 주지 않는다".
--    Flyway가 이 파일을 트랜잭션으로 감싸면 이 문장은 무효가 된다. 따라서 락 문제의
--    1차 통제 수단은 이 문장이 아니라 "적용 시점에 구 인스턴스 0대"(유지보수 창)다.
--    isolation을 확실히 낮추려면 마이그레이션 전용 배포의 JDBC URL에
--    sessionVariables=transaction_isolation='READ-COMMITTED' 를 붙인다.
-- ---------------------------------------------------------------------------
SET SESSION innodb_lock_wait_timeout = 10;
SET SESSION lock_wait_timeout = 15;
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- ---------------------------------------------------------------------------
-- 1. answer_like / answer_memo — 손자 세대. 서로 독립이므로 이 둘의 순서는 무관하다.
-- ---------------------------------------------------------------------------
DELETE FROM answer_like
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED');

DELETE FROM answer_memo
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 2. answer
-- ---------------------------------------------------------------------------
DELETE FROM answer
WHERE question_id IN (SELECT q.id
                      FROM question q
                               JOIN interview i ON i.id = q.interview_id
                      WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 3. question
-- ---------------------------------------------------------------------------
DELETE FROM question
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 4. interview_like
-- ---------------------------------------------------------------------------
DELETE FROM interview_like
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 5. interview (RESUME_BASED). interview_type은 VARCHAR(50)이고
--    idx_interview_interview_type(V33)이 있어 등가 조회가 인덱스를 탄다.
--
--    WHERE에 `OR generated_question_id IS NOT NULL`을 붙이지 않는다 -- 그것은 M2가 정의한
--    삭제 범위(interview_type='RESUME_BASED'와 그 후손)를 넘어 다른 타입의 면접을 조용히 지운다.
--    RESUME_BASED가 아닌데 generated_question을 물고 있는 행이 있다면 6단계가 ERROR 1451로
--    죽는 것이 옳다(§3-9-D 사전 점검 쿼리 (8)이 그것을 미리 알려준다).
-- ---------------------------------------------------------------------------
DELETE FROM interview
WHERE interview_type = 'RESUME_BASED';

-- ---------------------------------------------------------------------------
-- 6. generated_question 전량.
--    WHERE가 없는 것은 의도다 -- 이 테이블의 모든 행은 구 resume_question_generation 플로우의
--    산물이고 그 부모 테이블은 V54에서 DROP된다. 남길 행이 정의상 0이다.
--
--    TRUNCATE를 쓰지 않는다: interview.generated_question_id의 inbound FK 때문에
--    ERROR 1701로 죽고(실측), TRUNCATE는 DDL이라 암묵 커밋도 일으킨다.
--
--    fk_interview_generated_question은 ON DELETE 절이 없어 RESTRICT(delete_rule = NO ACTION,
--    실측)다. 5단계 이후에도 generated_question을 참조하는 interview 행이 남아 있으면
--    이 문장이 ERROR 1451로 죽는다 -- 이것이 "RESUME_BASED 이외의 면접은 generated_question을
--    참조하지 않는다"에 대한 DB 레벨 자동 검증이며, §3-9-D 사전 점검과 이중 방어를 이룬다.
--
--    이 문장이 V54의 MODIFY analysis_id NOT NULL 을 가능하게 만든다.
-- ---------------------------------------------------------------------------
DELETE FROM generated_question;
```

**`member.score` 표류 — 인간 판정 (§10 X-2). 위 SQL은 A안(무보정)으로 확정 배포 가능하다.**

`InterviewProceedService`가 `interview.evaluate(feedback, totalScore)` 직후 같은 값으로 `member.addScore(totalScore)`를 호출한다(`member != null` 가드 포함, `addScore` 호출처는 이 1곳뿐 — 실측). 즉 `member.score`는 `interview.total_score`의 비정규화 누계이고, RESUME_BASED 면접을 삭제하면 그 총점만큼 영구히 부풀어 남는다. `member`의 다른 비정규화 카운터는 없다(필드는 `id`/`nickname`/`score`/`profileCompleted`뿐 — 실측).

**B안 채택 시 전문.** 0단계를 **5단계보다 앞**에 넣어야 한다 — 삭제 후에는 영향 회원 집합을 알 수 없어 `UPDATE`를 `WHERE` 없이 돌릴 수밖에 없고, 그러면 `member` 전 행에 X 락이 걸려 퍼지 커밋까지 로그인 후속 쓰기가 막힌다.

```sql
-- ---------------------------------------------------------------------------
-- 0-B. (B안 채택 시에만) member.score 재계산 대상을 미리 확정한다. 5단계보다 앞이어야 한다.
--
--      !! 주의 !! CREATE TEMPORARY TABLE은 DDL이므로 이 블록을 넣으면 이 파일은
--      더 이상 "순수 DML"이 아니다. 파일 선두 주석의 해당 문장을 함께 정정하라.
--      이 스크립트의 안전 근거는 트랜잭션이 아니라 멱등성이므로 손실되는 보증은 없다.
-- ---------------------------------------------------------------------------
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_score_members (member_id BIGINT NOT NULL PRIMARY KEY)
    ENGINE = InnoDB;
DELETE FROM tmp_score_members;
INSERT INTO tmp_score_members (member_id)
SELECT DISTINCT i.member_id
  FROM interview i
 WHERE i.interview_type = 'RESUME_BASED'
   AND i.member_id IS NOT NULL;

-- ... 1~6단계 삭제 ...

-- ---------------------------------------------------------------------------
-- 7-B. member.score 재계산. 영향받은 회원만 갱신한다.
--      member.score는 interview.total_score의 비정규화 누계이며 유일 갱신 경로가
--      InterviewProceedService다.
-- ---------------------------------------------------------------------------
UPDATE member m
    JOIN tmp_score_members t ON t.member_id = m.id
SET m.score = COALESCE((SELECT SUM(i.total_score)
                        FROM interview i
                        WHERE i.member_id = m.id
                          AND i.total_score IS NOT NULL), 0);

DROP TEMPORARY TABLE tmp_score_members;
```

B안의 전제는 "`interview.total_score`를 우회한 점수 조정이 없었다"이다. 운영에서 아래가 **0행**이어야 안전하다. 0행이 아니면 A 또는 C로 간다.

```sql
SELECT m.id, m.score, COALESCE(SUM(i.total_score), 0) AS derived
FROM member m
         LEFT JOIN interview i ON i.member_id = m.id AND i.total_score IS NOT NULL
GROUP BY m.id, m.score
HAVING m.score <> derived;
```

**`V54__repoint_generated_question_and_drop_legacy_resume_tables.sql` (M3 + M1 teardown) 전문:**

```sql
-- ============================================================================
-- M3: generated_question을 단일 부모(resume_analysis) 구조로 전환.
-- M1(teardown side): 구 테이블 2개 DROP.
--
-- 전제 (반드시 확인): V51이 이미 아래를 만들어 두었다.
--     analysis_id BIGINT NULL / idx_generated_question_analysis_id
--     fk_generated_question_analysis / chk_generated_question_parent
--     그리고 generation_id를 NULL 허용으로 완화했다.
--   => 이 파일은 컬럼·인덱스·FK를 다시 만들지 않는다. 다시 만들면 각각
--      ERROR 1060(Duplicate column) / 1061(Duplicate key) / 1826(Duplicate foreign key)로 죽는다(실측).
--   => 남은 일은 (a) 구 부모 제거 (b) XOR CHECK 제거 (c) analysis_id NOT NULL 승격뿐이다.
--
-- 전제 2: V53이 generated_question을 0행으로 만들었다.
--   => MODIFY ... NOT NULL 이 이 전제에 대한 비침습적 assert 역할을 한다. 잔존 행이 있으면
--      ERROR 1138 (22004) Invalid use of NULL value 로 즉시 죽는다(실측).
--
-- !! 비가역 !! resume_evaluation / resume_question_generation DROP에 역마이그레이션이 없다.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. MDL 대기 상한. lock_wait_timeout 기본값 31536000초(1년)를 짧게 잡는다.
--    ALTER가 메타데이터 락을 못 잡으면 조용히 매달리는 대신 ERROR 1205로 빠르게 실패해야 한다
--    -- 매달리면 MySQL의 MDL 큐가 FIFO이므로 generated_question에 대한 후속 SELECT/INSERT
--    전량이 그 뒤에 쌓여 면접 진행 API가 통째로 멈춘다.
--    실패 시 flyway_schema_history에 success=0이 남으므로 flyway repair 후 재시도한다.
-- ---------------------------------------------------------------------------
SET SESSION lock_wait_timeout = 15;
SET SESSION innodb_lock_wait_timeout = 10;

-- ---------------------------------------------------------------------------
-- 1. XOR CHECK를 가장 먼저 지운다. 이 문장 없이 4번을 시도하면 죽는다(실측 8.4.5):
--      ERROR 3959 (HY000): Check constraint 'chk_generated_question_parent' uses column
--                          'generation_id', hence column cannot be dropped or renamed.
--    M3의 "XOR CHECK 불필요"가 최종 스키마에서 실현되는 지점이다.
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question DROP CHECK chk_generated_question_parent;

-- ---------------------------------------------------------------------------
-- 2. 구 부모 FK 분리. FK -> 인덱스 -> 컬럼 순서를 지킨다.
--    InnoDB는 FK가 참조하는 인덱스·컬럼의 선삭제를 거부한다(errno 150).
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question DROP FOREIGN KEY fk_gq_generation;

-- ---------------------------------------------------------------------------
-- 3. generation_id 위의 인덱스. 단일 컬럼 인덱스이므로 DROP COLUMN이 자동으로 함께 없애지만,
--    암묵 동작에 의존하지 않고 명시적으로 지운다.
--    (information_schema.statistics 실측: PRIMARY / idx_..._generation_id / idx_..._analysis_id)
-- ---------------------------------------------------------------------------
DROP INDEX idx_generated_question_generation_id ON generated_question;

-- ---------------------------------------------------------------------------
-- 4. 구 부모 컬럼 제거.
--    ALTER를 한 문장으로 합치지 않는다 -- 8.4.5에서는 합쳐도 동작하지만(실측) 실패 지점을
--    문장 단위로 특정할 수 있어야 부분 적용 복구가 쉽다.
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question DROP COLUMN generation_id;

-- ---------------------------------------------------------------------------
-- 5. analysis_id를 NOT NULL로 승격. V53이 0행으로 만들었으므로 깨끗하게 통과한다(실측).
--    ADD COLUMN ... NOT NULL 을 쓰지 않는 이유는 여기서 컬럼을 새로 만들지 않기 때문이며,
--    설령 새로 만든다 해도 비어 있지 않은 테이블에서는 경고도 에러도 없이 모든 행을 0으로
--    채워 FK 추가가 ERROR 1452로 늦게 죽는다(실측, sql_mode에 STRICT_TRANS_TABLES 포함).
--
--    ON DELETE CASCADE는 V51에서도 걸지 않았다 -- interview.generated_question_id가 이 행을
--    참조하므로 삭제는 항상 명시적·검증적이어야 한다(§7-6).
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question MODIFY COLUMN analysis_id BIGINT NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. 구 테이블 DROP.
--    resume_question_generation: inbound FK는 fk_gq_generation 1개뿐이었고 2단계에서 제거됐다.
--    resume_evaluation: inbound FK 0건(information_schema 실측). 바로 DROP된다.
--    IF EXISTS를 쓰지 않는다 -- 테이블이 없다면 그것 자체가 조사해야 할 이상 상태다.
-- ---------------------------------------------------------------------------
DROP TABLE resume_question_generation;

DROP TABLE resume_evaluation;
```

**엔티티 동반 변경 (V54와 같은 커밋).** `docs` 프로파일은 `ddl-auto: create-drop`으로 엔티티에서 스키마를 만들므로 엔티티가 남으면 H2에 `generation_id`가 생겨 마이그레이션과 갈라진다. `test`는 `ddl-auto: none` + 스키마 검증 없음이라 **기동은 성공하고 쿼리 시점에 터진다.**

`interview/domain/GeneratedQuestion.java` (실측 줄번호 기준):

```java
 @Table(name = "generated_question", indexes = {
-        @Index(name = "idx_generated_question_generation_id", columnList = "generation_id"),   삭제
         @Index(name = "idx_generated_question_analysis_id", columnList = "analysis_id")
 })
@@ 삭제
-    @ManyToOne(fetch = FetchType.LAZY)
-    @JoinColumn(name = "generation_id")
-    private ResumeQuestionGeneration generation;
@@ 교체
-    @ManyToOne(fetch = FetchType.LAZY)
-    @JoinColumn(name = "analysis_id")
+    @ManyToOne(fetch = FetchType.LAZY, optional = false)
+    @JoinColumn(name = "analysis_id", nullable = false)
     private ResumeAnalysis analysis;
@@ 삭제 (파라미터 타입이 삭제 대상이라 컴파일 불가. 유일 호출자 ResumeBasedInterviewService도 삭제됨)
-    public GeneratedQuestion(ResumeQuestionGeneration generation, String content, String reason, Integer questionOrder) {
-        this.generation = generation;
-        ...
-    }
존치: private 4인자 생성자(유일 생성 경로)
존치: 정적 팩토리 forAnalysis(...) — 존재 이유가 부모 구분이 아니라 영속화 직전
      방어적 절단(abbreviate)이므로 부모가 하나가 되어도 유효하다. 개명·병합하지 않는다
```

`@Index(generation_id)`를 남기면 `docs`의 create-drop이 없는 컬럼에 인덱스를 만들려다 실패한다.

**삭제 후 `new GeneratedQuestion(` 를 전수 grep한다** — 두 4인자 생성자는 첫 인자 타입으로만 구분됐으므로, `null`을 첫 인자로 넘기던 호출부가 있었다면 컴파일 에러가 아니라 **다른 생성자로 조용히 바인딩**된다.

`GeneratedQuestionRepository.java`의 `findByGenerationIdOrderByQuestionOrder` 삭제(§3-7).

### 3-5. 엔티티

```java
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "resume_analysis",
        indexes = {
                @Index(name = "idx_resume_analysis_member_id_created_at", columnList = "member_id, created_at"),
                @Index(name = "idx_resume_analysis_state_created_at", columnList = "state, created_at"),
                @Index(name = "idx_resume_analysis_state_question_started_at",
                        columnList = "state, question_started_at")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_resume_analysis_guest_token",
                columnNames = "guest_token")})
public class ResumeAnalysis extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id")   // nullable: 게스트
    private Member member;

    @Column(name = "guest_token", length = 36)      private String guestToken;
    @Column(name = "guest_ip", length = 45)         private String guestIp;
    @Column(name = "guest_lock_value", length = 36) private String guestLockValue;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_resume_id")
    private MemberResume memberResume;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_portfolio_id")
    private MemberPortfolio memberPortfolio;

    @Column(name = "job_position", nullable = false, length = 500) private String jobPosition;
    @Column(name = "job_description", columnDefinition = "TEXT")   private String jobDescription;
    @Column(name = "job_career", nullable = false, length = 100)   private String jobCareer;
    @Column(name = "jd_provided", nullable = false)                private boolean jdProvided;

    @Enumerated(EnumType.STRING) @Column(name = "state", nullable = false, length = 30)
    private ResumeAnalysisState state;
    @Enumerated(EnumType.STRING) @Column(name = "failure_reason", length = 30)
    private ResumeAnalysisFailureReason failureReason;

    @Column(name = "problem_solving_score") private Integer problemSolvingScore;
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "problem_solving_reason", columnDefinition = "JSON") private List<String> problemSolvingReason;
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "problem_solving_improvements", columnDefinition = "JSON")
    private List<String> problemSolvingImprovements;
    // project_experience_* / technical_skills_* / soft_skills_* / jd_fit_* 동일 패턴 (총 15필드)

    @Column(name = "total_score")                                  private Integer totalScore;
    @Column(name = "total_feedback", columnDefinition = "TEXT")     private String totalFeedback;
    @Column(name = "billing_required", nullable = false)            private boolean billingRequired;
    @Column(name = "charged_token_count", nullable = false)         private Integer chargedTokenCount;
    @Column(name = "token_charge_failed", nullable = false)         private boolean tokenChargeFailed;
    @Column(name = "question_retry_count", nullable = false)        private Integer questionRetryCount;
    @Column(name = "evaluation_completed_at")                       private LocalDateTime evaluationCompletedAt;
    @Column(name = "question_started_at")                           private LocalDateTime questionStartedAt;
    @Column(name = "completed_at")                                  private LocalDateTime completedAt;

    // --- 정적 팩토리 (생성자 private, 인자 5개 이하) ---
    public static ResumeAnalysis forMember(Member member, MemberResume memberResume,
                                           MemberPortfolio memberPortfolio,
                                           ResumeAnalysisJobInput jobInput, boolean billingRequired);
    public static ResumeAnalysis forGuest(String guestToken, ClientIp clientIp, String guestLockValue,
                                          ResumeAnalysisJobInput jobInput);
    // forGuest는 memberResume/memberPortfolio를 받지 않는다(member_resume.member_id NOT NULL 제약)

    // --- 상태 전이 (D9). 전부 PESSIMISTIC_WRITE 로 재조회한 인스턴스에서만 호출한다 ---
    public void completeEvaluation(ResumeAnalysisEvaluation evaluation);  // PENDING → EVALUATION_COMPLETED
    public void failEvaluation(ResumeAnalysisFailureReason reason);       // PENDING → EVALUATION_FAILED
    public void completeQuestions();                                     // EVALUATION_COMPLETED → COMPLETED
    public void failQuestions(ResumeAnalysisFailureReason reason);        // EVALUATION_COMPLETED → QUESTION_FAILED
    public void restoreForQuestionRetry();  // QUESTION_FAILED → EVALUATION_COMPLETED, retryCount++, startedAt=now
    private void validateCurrentState(ResumeAnalysisState expected);      // 위반 시 IllegalStateException

    // --- 조회 술어 ---
    public boolean isGuest();                            // member == null
    public boolean isOwner(Long memberId);               // member == null이면 false (NPE 가드)
    public boolean isSameGuestToken(String guestToken);  // isGuest() && Objects.equals(...)
    public boolean isQuestionRetryable(boolean sourceTextExists);
}
```

`complete()` 인자 폭발 회피 3단계: ① 지표 15값 + 총점·총평을 `ResumeAnalysisEvaluation` 값객체로 묶어 `completeEvaluation(...)`을 **1인자**로, ② 완료를 단계별 2메서드로 쪼개 D9와 1:1, ③ 직무 3필드를 `ResumeAnalysisJobInput`으로 묶어 팩토리 인자를 5개 이하로. 결과: 구 17 위치인자 → 최대 5.

`isOwner`가 `member.getId()`를 무가드 역참조하지 않는 것이 필수다(`ResumeQuestionGeneration.isOwner`는 게스트 행에서 NPE — **복사 금지**).

```java
public enum ResumeAnalysisState {
    PENDING, EVALUATION_COMPLETED, COMPLETED, EVALUATION_FAILED, QUESTION_FAILED,
    ;
    public boolean isEvaluationRevealed() {
        return this == EVALUATION_COMPLETED || this == COMPLETED || this == QUESTION_FAILED;
    }
    public boolean isQuestionReady() { return this == COMPLETED; }
    public boolean isTerminal() {
        return this == COMPLETED || this == EVALUATION_FAILED || this == QUESTION_FAILED;
    }
}
```

```java
public enum ResumeAnalysisDimension {
    PROBLEM_SOLVING("problem_solving"),
    PROJECT_EXPERIENCE("project_experience"),
    TECHNICAL_SKILLS("technical_skills"),
    SOFT_SKILLS("soft_skills"),
    JD_FIT("jd_fit"),
    ;
    private final String toolKey;
    public String toolKey() { return toolKey; }   // 툴 스키마 필드 접두사 · JSON 키의 단일 소스
}
```

```java
public enum ResumeAnalysisWeights {

    JD_PROVIDED(new EnumMap<>(Map.of(
            PROBLEM_SOLVING, 0.25, PROJECT_EXPERIENCE, 0.25, TECHNICAL_SKILLS, 0.25,
            SOFT_SKILLS, 0.10, JD_FIT, 0.15))),
    JD_ABSENT(new EnumMap<>(Map.of(
            PROBLEM_SOLVING, 0.30, PROJECT_EXPERIENCE, 0.30, TECHNICAL_SKILLS, 0.30,
            SOFT_SKILLS, 0.10))),
    ;

    private final Map<ResumeAnalysisDimension, Double> weights;

    public static ResumeAnalysisWeights of(boolean jdProvided) {
        return jdProvided ? JD_PROVIDED : JD_ABSENT;
    }

    public Double weightOf(ResumeAnalysisDimension dimension) {
        return weights.get(dimension);           // 산출 대상이 아니면 null
    }

    public List<ResumeAnalysisDimension> dimensions() {
        return Arrays.stream(ResumeAnalysisDimension.values())
                .filter(weights::containsKey)
                .toList();                        // 선언 순서 유지
    }

    public int calculateTotalScore(ResumeAnalysisEvaluation evaluation) {
        Map<ResumeAnalysisDimension, Integer> scores = evaluation.scores();
        if (!scores.keySet().equals(weights.keySet())) {
            throw new ExternalApiException("이력서 분석 차원이 가중치 세트와 일치하지 않습니다. scores=" + scores.keySet());
        }
        double weightedSum = 0.0;
        for (Map.Entry<ResumeAnalysisDimension, Double> entry : weights.entrySet()) {
            Integer score = scores.get(entry.getKey());
            if (score == null) {
                throw new ExternalApiException("차원 점수가 비어 있습니다. dimension=" + entry.getKey());
            }
            weightedSum += entry.getValue() * score;
        }
        return (int) Math.round(weightedSum);
    }
}
```

구 `withCalculatedTotalScore()`와의 차이: **null을 0으로 취급하지 않는다.** 구 `scoreOf(CategoryScore)`는 null → 0이라 JD적합성 null이 0점으로 가중합에 섞여 D4를 파괴한다. 신규는 키 집합 불일치·null을 `ExternalApiException`으로 즉시 실패시킨다(상위 `catch(Exception)`이 GPT 폴백을 유발하고, 스키마·가중치가 어긋난 배포에서는 양 provider가 같은 이유로 실패해 종단 실패가 된다 — 구 플로우와 동일한 성질). 반올림은 `Math.round`로 동일. 예) JD 있음 80/70/60/55/65 → 70.75 → **71**, JD 없음 80/70/60/55 → 68.5 → **69**.

```java
public record ResumeAnalysisEvaluation(
        DimensionScore problemSolving, DimensionScore projectExperience, DimensionScore technicalSkills,
        DimensionScore softSkills, DimensionScore jdFit /* nullable */,
        Integer totalScore, String totalFeedback) {

    public Map<ResumeAnalysisDimension, Integer> scores();       // jdFit == null이면 4개 엔트리
    public ResumeAnalysisEvaluation withTotalScore(int totalScore);
}

public record DimensionScore(int score, List<String> reason, List<String> improvements) {
    public DimensionScore {
        // score 0~100, reason/improvements non-empty 검증
    }
}

public record ResumeAnalysisJobInput(String jobPosition, String jobDescription, String jobCareer) {
    public boolean hasJobDescription() { return jobDescription != null && !jobDescription.isBlank(); }
}
```

`@Embeddable`을 쓰지 않는다(레포에 `@Embeddable`/`@Embedded` 사용처 0건 — 새 매핑 패턴 도입 대신 순수 값 객체로 인자만 묶는다).

### 3-6. 상태 전이 규약

| from | to | 트리거 | 부수 효과 |
|---|---|---|---|
| `PENDING` | `EVALUATION_COMPLETED` | 평가 콜 파싱 성공 | 15컬럼 + `total_score`/`total_feedback` + `evaluation_completed_at` + `question_started_at = now()` |
| `PENDING` | `EVALUATION_FAILED` | Bedrock+GPT 실패, 잘림, 저장 실패, executor 거절, sweep | `failure_reason`. **게스트면 `guest_lock_value`로 락 해제** |
| `EVALUATION_COMPLETED` | `COMPLETED` | 질문 콜 성공 | `generated_question` 5~7행 INSERT + `completed_at` |
| `EVALUATION_COMPLETED` | `QUESTION_FAILED` | 질문 콜 실패, 저장 실패, sweep | `failure_reason` |
| `QUESTION_FAILED` | `EVALUATION_COMPLETED` | 사용자 재시도 | `question_retry_count++`, `question_started_at = now()`, `failure_reason = null` |

**불변식(위반 시 claim 소실·중복 완료가 발생한다):** 모든 상태 전이는 다음 둘 중 하나로만 한다.
- (a) `readAnalysisForUpdate(analysisId)`(`PESSIMISTIC_WRITE`)로 **락 획득 후 최신 상태를 다시 읽고** 엔티티 가드 메서드를 호출한다. 이 경로 덕분에 "질문 콜 진행 중 claim"이 안전하다(락 안에서 `member`를 최신으로 다시 읽으므로 `member_id = NULL`을 되돌려 쓰지 않는다).
- (b) `WHERE id = ? AND state = ?` 조건부 벌크 UPDATE + 영향 행수 판정. 0행이면 다른 주체가 이미 전이시킨 것이므로 **자기 결과를 폐기한다.**

락 없이 엔티티를 로드해 세터로 바꾸는 코드는 금지한다. 레포에 `@DynamicUpdate` 사용처가 0건이므로 Hibernate는 전 컬럼 UPDATE를 발행하고, 그 경로가 하나만 섞여도 동시 claim이 조용히 소실된다. 이중 방어로 신규 엔티티에 `@DynamicUpdate`를 붙인다.

### 3-7. 리포지토리

```java
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {

    Optional<ResumeAnalysis> findByGuestToken(String guestToken);

    Page<ResumeAnalysisSummaryProjection> findSummariesByMemberId(Long memberId, Pageable pageable);

    boolean existsByMemberIdAndStateInAndCreatedAtAfter(
            Long memberId, Collection<ResumeAnalysisState> states, LocalDateTime since);

    boolean existsByMemberIdAndGuestTokenIsNotNull(Long memberId);      // claim 한도

    // 구 질문생성 이력(resume_question_generation)은 M1으로 테이블째 사라졌으므로 판정에 쓸 수 없다.
    // 따라서 무료 1회는 신규 resume_analysis 과금 대상 이력만으로 판정하는 유일 소스다(§7-3).
    // guest_token IS NULL 조건 때문에 claim된 게스트 행은 회원 무료 1회를 태우지 않는다.
    @Query("""
            SELECT COUNT(a) > 0 FROM ResumeAnalysis a
             WHERE a.member.id = :memberId
               AND a.guestToken IS NULL
               AND (a.failureReason IS NULL
                    OR a.failureReason NOT IN (
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.CAPACITY,
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.STALE_SWEEP,
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.PERSISTENCE,
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.GUEST_LIMIT))
            """)
    boolean existsChargeableByMemberId(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ResumeAnalysis a WHERE a.id = :id")
    Optional<ResumeAnalysis> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ResumeAnalysis a SET a.member = :member
             WHERE a.guestToken = :guestToken AND a.member IS NULL
            """)
    int claimByGuestToken(@Param("member") Member member, @Param("guestToken") String guestToken);

    @Modifying
    @Query("""
            UPDATE ResumeAnalysis a SET a.chargedTokenCount = :cost
             WHERE a.id = :id AND a.chargedTokenCount = 0
            """)
    int markTokenCharged(@Param("id") Long id, @Param("cost") int cost);

    @Modifying
    @Query("UPDATE ResumeAnalysis a SET a.chargedTokenCount = 0, a.tokenChargeFailed = true WHERE a.id = :id")
    int markTokenChargeFailed(@Param("id") Long id);

    List<ResumeAnalysis> findByStateAndCreatedAtBefore(
            ResumeAnalysisState state, LocalDateTime threshold, Pageable pageable);

    List<ResumeAnalysis> findByStateAndQuestionStartedAtBefore(
            ResumeAnalysisState state, LocalDateTime threshold, Pageable pageable);

    @Query("""
            SELECT a.id FROM ResumeAnalysis a
             WHERE a.member IS NULL AND a.guestToken IS NOT NULL AND a.createdAt < :threshold
               AND NOT EXISTS (
                   SELECT 1 FROM GeneratedQuestion gq
                    WHERE gq.analysis = a
                      AND EXISTS (SELECT 1 FROM Interview i WHERE i.generatedQuestion = gq))
             ORDER BY a.id
             LIMIT :limit
            """)
    List<Long> findUnclaimedGuestAnalysisIds(@Param("threshold") LocalDateTime threshold,
                                             @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM ResumeAnalysis a WHERE a.id IN :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
```

`LIMIT :limit`을 JPQL에 쓰는 것은 `TosspaymentsPaymentRepository.findStalePaymentsByStates`에 이미 있는 선례다.

```java
public interface ResumeAnalysisSourceTextRepository extends JpaRepository<ResumeAnalysisSourceText, Long> {
    Optional<ResumeAnalysisSourceText> findByAnalysisId(Long analysisId);
    boolean existsByAnalysisId(Long analysisId);
    @Modifying
    @Query("DELETE FROM ResumeAnalysisSourceText s WHERE s.analysis.id IN :analysisIds")
    int deleteByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);
}
```

**`GeneratedQuestionRepository` 추가 메서드 4개.** Task 3에서 이미 작성돼 스테이징돼 있다. 아래는 확인용 전사이며 재작성 대상이 아니다. 별칭은 `questionCount`여야 한다 — `count`는 HQL 함수명과 충돌한다(`QuestionCountProjection` 클래스 Javadoc이 정본). `@Transactional` + `clearAutomatically`/`flushAutomatically`도 그대로 유지한다.

```java
    List<GeneratedQuestion> findByAnalysisIdOrderByQuestionOrder(Long analysisId);

    Optional<GeneratedQuestion> findByIdAndAnalysisId(Long id, Long analysisId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GeneratedQuestion q WHERE q.analysis.id IN :analysisIds")
    int deleteByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);

    @Query("SELECT q.analysis.id AS analysisId, COUNT(q) AS questionCount FROM GeneratedQuestion q "
            + "WHERE q.analysis.id IN :analysisIds GROUP BY q.analysis.id")
    List<QuestionCountProjection> countByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);
```

**삭제 1개 (필수):** `List<GeneratedQuestion> findByGenerationIdOrderByQuestionOrder(Long generationId);`. 남기면 컴파일은 통과하지만 `generation` 프로퍼티 소멸로 파생 쿼리 해석이 `PropertyReferenceException`을 던진다. **단 `spring.main.lazy-initialization: true`(`src/test/resources/application.yml:5-6`) 때문에 "컨텍스트 기동 실패"가 아니라 "이 리포지토리를 주입하는 테스트가 개별 실패"한다** — 빈 컨텍스트 스모크로는 잡히지 않는다.

`GeneratedQuestion.forAnalysis`는 컬럼 한도 방어를 포함한다(§5-3).

### 3-8. 로컬 test DB 상태 (P1에서는 재기동 불필요)

로컬 `kokomen-test` 실측:

```
flyway_schema_history 최신 = version 51, script V51__create_resume_analysis.sql,
checksum -2144793090, success = 1
base table 수 = 31  (V50 시점 29 + resume_analysis + resume_analysis_source_text)
generated_question = analysis_id(NULL 허용) + fk_generated_question_analysis
                     + idx_generated_question_analysis_id + chk_generated_question_parent
                     + generation_id(NULL 허용, V51이 완화)
MySQL = 8.4.5   (CHECK 강제, sql_mode에 STRICT_TRANS_TABLES 포함)
```

유령 V51은 이미 없다 — Task 1이 실제로 적용한 이력이 남아 있을 뿐이다. **V51을 `git show d1eae65:src/main/resources/db/migration/V51__create_resume_analysis.sql` 로 바이트 동일 복원하면 checksum이 `-2144793090`으로 일치해 `validate-on-migrate: true` 하에서 통과하고, V52~V54가 순차 적용된다. 컨테이너 재기동은 필요 없다.**

**재기동이 필요한 유일한 경우는 이미 적용된 V51~V54 중 하나를 편집했을 때다.** 그때 절차:

```bash
cd /Users/osang0731/IdeaProjects/kokomen-backend
docker compose -f test.yml down
docker compose -f test.yml up -d
until [ "$(docker inspect -f '{{.State.Health.Status}}' test-mysql)" = healthy ]; do sleep 2; done
./gradlew test --tests "com.samhap.kokomen.member.repository.MemberRepositoryTest"
```

`test-mysql`은 `test.yml`에 볼륨이 없어 `down`으로 데이터가 사라진다(`test-redis`는 named volume `kokomen-test-redis-data` 보유).

**착수 전 판정 (V51 복원 직후, 전부 만족):**

```bash
docker exec test-mysql mysql -uroot -proot -N -e "
  SELECT version, script, checksum, success FROM \`kokomen-test\`.flyway_schema_history
   ORDER BY installed_rank DESC LIMIT 1;   -- 51 / V51__create_resume_analysis.sql / -2144793090 / 1
  SELECT COUNT(*) FROM \`kokomen-test\`.flyway_schema_history WHERE success = 0;   -- 0
"
git diff d1eae65 -- src/main/resources/db/migration/V51__create_resume_analysis.sql   # 빈 출력
```

**Flyway 설정의 프로파일 비대칭 (실측).** `src/test/resources/application.yml`이 `common-test`만 include하므로 `test` 프로파일은 `application-common.yml`을 로드하지 않는다.

| 설정 | `test` 실효값 | `prod` 실효값 |
|---|---|---|
| `validate-on-migrate` | `true` (기본) | `true` (명시) |
| `out-of-order` | `false` (기본) | `false` (명시) |
| `clean-disabled` | `true` (기본) | `true` (명시) |
| `baseline-on-migrate` | **`false`** (기본) | `true` (명시) |

`out-of-order: false`가 양쪽 공통이므로 **V52~V54의 번호가 실행 순서와 일치해야 한다** — 높은 버전을 먼저 적용한 뒤 낮은 버전을 추가하면 거부된다.

**부수 확인 (실측):** test MySQL은 **8.4.5**이므로 `CHECK` 제약이 강제된다(8.0.16+). `build.gradle`에 **`awaitility` 의존성이 없다** — §6-3이 hop을 public으로 노출하는 근거가 확인됐다.

**운영 적용 시에는 이 절이 적용되지 않는다** — 운영 `flyway_schema_history` 최고 version은 50이고 V51은 `develop`에 병합된 적이 없어 로컬 checksum 일치 전제가 성립하지 않는다. 운영 절차는 §3-9를 따른다.

### 3-9. 운영 적용 리스크와 선행 확인 항목

#### A. 전제 — 운영 `flyway_schema_history` 최고 version은 **50**이다

```
git ls-tree -r --name-only develop        -- src/main/resources/db/migration/ | grep V5   -> V50 만
git ls-tree -r --name-only origin/develop -- src/main/resources/db/migration/ | grep V5   -> V50 만
git merge-base --is-ancestor d1eae65 HEAD ; echo $?                                       -> 1 (NO)
```

V51은 `develop`·`origin/develop`·`HEAD` 어디에도 없다(soft reset + 미머지). §3-8의 로컬 checksum 일치 전제는 **로컬 test DB에만 참인 값이다.** 운영은 V51~V54가 **한 번에 4개** 적용된다(또는 `SPRING_FLYWAY_TARGET`으로 2회 분할).

#### B. 롤백 불가 구간

| 버전 | 롤백 불가 내용 | 역마이그레이션 |
|---|---|---|
| V51 | — | **가역** (`DROP TABLE resume_analysis_source_text; DROP TABLE resume_analysis;` + `generated_question` ALTER 원복) |
| V52 | recruit 계열 9개 테이블 전체 | **불가.** 논리 백업 복원만. 재크롤링도 `RecruitmentScheduler` 삭제로 불가 |
| V53 | RESUME_BASED 면접·질문·답변·좋아요·메모, `generated_question` 전량 | **불가** |
| V53-7B (§10 X-2 B안) | `member.score` 원본 값 | **불가** |
| V54-4 | `generated_question.generation_id` 값 | 컬럼은 복원 가능하나 **값은 불가**(V53에서 행이 사라짐) |
| V54-6 | `resume_evaluation`, `resume_question_generation` 전체 | **불가** |

MySQL DDL은 트랜잭션이 아니다. V52/V54가 중간에 실패하면 되돌릴 수 없는 반쯤 뜯긴 상태가 되고 `flyway_schema_history`에 `success = 0`이 남아 다음 기동이 거부된다. 복구 경로는 `flyway repair` → 남은 문장 수동 실행 뿐이다.

#### C. 0단계 — 백업 (다른 모든 항목보다 먼저)

```bash
mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF \
  -h "$PROD_HOST" -u "$PROD_USER" -p "$PROD_DB" \
  > "backup-pre-V51-$(date +%Y%m%d%H%M).sql"
# 복원 리허설까지 끝내야 백업으로 인정한다. 파일 존재는 백업이 아니다.
```

#### D. 1단계 — 스키마 전제 검증 (전부 통과해야 착수)

```sql
-- (1) Flyway가 정확히 V50까지, 실패 이력 없이 적용됐는가
SELECT MAX(CAST(version AS UNSIGNED)) AS max_version FROM flyway_schema_history;   -- 기대: 50
SELECT COUNT(*) AS failed FROM flyway_schema_history WHERE success = 0;            -- 기대: 0

-- (2) 이름 충돌 없음. FK/CHECK 제약 이름은 MySQL에서 스키마 전역이다.
SELECT COUNT(*) FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('resume_analysis', 'resume_analysis_source_text');           -- 기대: 0
SELECT COUNT(*) FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE()
   AND constraint_name IN ('fk_resume_analysis_member', 'fk_resume_analysis_member_resume',
                           'fk_resume_analysis_member_portfolio', 'fk_rast_analysis',
                           'uk_rast_analysis_id', 'uk_resume_analysis_guest_token',
                           'chk_resume_analysis_owner', 'chk_resume_analysis_scores',
                           'fk_generated_question_analysis',
                           'chk_generated_question_parent');                       -- 기대: 0
SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'generated_question'
   AND column_name = 'analysis_id';                                               -- 기대: 0
SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'generated_question'
   AND index_name = 'idx_generated_question_analysis_id';                         -- 기대: 0

-- (3) V51이 부착할 대상이 실재하는가 (V51은 generation_id를 MODIFY한다)
SELECT is_nullable FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'generated_question'
   AND column_name = 'generation_id';                                             -- 기대: NO (1행)
SELECT COUNT(*) FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_gq_generation';    -- 기대: 1
SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'generated_question'
   AND index_name = 'idx_generated_question_generation_id';                       -- 기대: >= 1

-- (4) M2의 연쇄 트리가 운영 스키마에서도 완전한가.
--     출력이 문서화된 집합과 정확히 일치해야 한다. 한 행이라도 더 나오면 V53에 삭제 문장을 추가한다.
--     기대: answer_like, answer_memo(->answer) / answer(->question) /
--           question, interview_like(->interview) / interview(->generated_question)
SELECT table_name, column_name, referenced_table_name, constraint_name
  FROM information_schema.key_column_usage
 WHERE table_schema = DATABASE()
   AND referenced_table_name IN ('interview', 'question', 'answer', 'generated_question')
 ORDER BY referenced_table_name, table_name;

-- (5) 구 이력서 테이블의 inbound FK
--     기대: resume_question_generation <- fk_gq_generation 단 1행. resume_evaluation은 0행.
SELECT table_name, constraint_name, referenced_table_name
  FROM information_schema.key_column_usage
 WHERE table_schema = DATABASE()
   AND referenced_table_name IN ('resume_evaluation', 'resume_question_generation');

-- (6) recruit inbound FK 전량. §3-2의 5개와 정확히 일치해야 한다.
--     4개만 나오면 ocr_waiting_list가 운영에 없다는 뜻이므로 V52 1단계에서 그 줄을 뺀다.
SELECT table_name, constraint_name FROM information_schema.key_column_usage
 WHERE table_schema = DATABASE()
   AND referenced_table_name IN ('recruit', 'affiliate', 'company', 'crawling_request')
 ORDER BY referenced_table_name, table_name;

-- (7) CHECK 제약 강제 여부. 8.0.16 미만이면 chk_* 4개가 파싱만 되고 조용히 무시된다.
SELECT VERSION() AS mysql_version;                                                -- 기대: >= 8.0.16

-- (8) RESUME_BASED 이외의 면접이 generated_question을 참조하지 않는가.
--     0이 아니면 V53-6이 ERROR 1451로 죽는다. 죽는 것이 옳으나 미리 알아야 한다.
SELECT COUNT(*) FROM interview
 WHERE generated_question_id IS NOT NULL AND interview_type <> 'RESUME_BASED';     -- 기대: 0

-- (9) interview_type의 실제 분포. 오타 값('resume_based' 등)이 있으면 삭제가 누락된다.
SELECT interview_type, COUNT(*) FROM interview GROUP BY interview_type;

-- (10) 장기 실행 트랜잭션. 0행이 아니면 V54의 ALTER TABLE이 MDL 대기에 들어가고
--      generated_question에 대한 후속 쿼리 전량이 그 뒤에 FIFO 큐잉된다(면접 진행 API 전면 정지).
SELECT trx_id, trx_started, TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS age_sec,
       trx_mysql_thread_id, trx_query
  FROM information_schema.innodb_trx
 WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 5;                             -- 기대: 0행
```

#### E. 2단계 — 삭제 대상 행 수 감사 (V53 적용 전에 반드시 기록)

```sql
SELECT 'interview.RESUME_BASED' AS target, COUNT(*) AS rows_to_delete
  FROM interview WHERE interview_type = 'RESUME_BASED'
UNION ALL SELECT 'interview_like', COUNT(*) FROM interview_like
  WHERE interview_id IN (SELECT id FROM interview WHERE interview_type = 'RESUME_BASED')
UNION ALL SELECT 'question', COUNT(*) FROM question
  WHERE interview_id IN (SELECT id FROM interview WHERE interview_type = 'RESUME_BASED')
UNION ALL SELECT 'answer', COUNT(*) FROM answer
  WHERE question_id IN (SELECT q.id FROM question q JOIN interview i ON i.id = q.interview_id
                        WHERE i.interview_type = 'RESUME_BASED')
UNION ALL SELECT 'answer_like', COUNT(*) FROM answer_like
  WHERE answer_id IN (SELECT a.id FROM answer a JOIN question q ON q.id = a.question_id
                      JOIN interview i ON i.id = q.interview_id WHERE i.interview_type = 'RESUME_BASED')
UNION ALL SELECT 'answer_memo', COUNT(*) FROM answer_memo
  WHERE answer_id IN (SELECT a.id FROM answer a JOIN question q ON q.id = a.question_id
                      JOIN interview i ON i.id = q.interview_id WHERE i.interview_type = 'RESUME_BASED')
UNION ALL SELECT 'generated_question (ALL)', COUNT(*) FROM generated_question
UNION ALL SELECT 'resume_evaluation (DROP)', COUNT(*) FROM resume_evaluation
UNION ALL SELECT 'resume_question_generation (DROP)', COUNT(*) FROM resume_question_generation
UNION ALL SELECT 'members_regaining_free_use', COUNT(DISTINCT member_id) FROM resume_question_generation
UNION ALL SELECT 'members_score_affected', COUNT(DISTINCT member_id) FROM interview
  WHERE interview_type = 'RESUME_BASED' AND member_id IS NOT NULL
UNION ALL SELECT 'recruit (DROP)', COUNT(*) FROM recruit
UNION ALL SELECT 'affiliate (DROP)', COUNT(*) FROM affiliate
UNION ALL SELECT 'company (DROP)', COUNT(*) FROM company
UNION ALL SELECT 'ocr_waiting_list (DROP)', COUNT(*) FROM ocr_waiting_list
UNION ALL SELECT 'crawling_request (DROP)', COUNT(*) FROM crawling_request;
```

**이 출력을 PR 설명에 붙여 "별로 없다"는 전제를 수치로 확정한다.** M1·M2의 근거가 그 전제뿐이므로 수치가 예상과 다르면 M1·M2를 인간 파트너에게 되돌린다. `members_regaining_free_use`는 §10 X-3 판정의 유일 근거, `members_score_affected`는 §10 X-2 판정의 유일 근거다.

**판단선:** `answer_like`가 10만 행을 넘으면 §3-10 변형으로 바꾼다.

#### F. 3단계 — 적용

```bash
# 게이트: 구 인스턴스 0대 (롤링 금지). §3-9-G 참조.
SPRING_FLYWAY_TARGET=53 <배포 커맨드>     # V51 -> V52 -> V53 까지
# -> E단계 감사 쿼리 재실행, 삭제 결과가 기대치와 일치하는지 확인
unset SPRING_FLYWAY_TARGET
<배포 커맨드>                              # V54
```

단일 배포로 갈 경우 V51~V54가 한 번에 적용되고 중간 정지가 불가능하다. 실패 시 복구는 `flyway repair` → 남은 문장 수동 실행 뿐이다.

#### G. 락·MDL 통제 (1차 수단은 SQL이 아니라 배포 방식)

| 위험 | 실측 | 통제 |
|---|---|---|
| V53의 서브쿼리 DELETE가 소스 테이블(`interview`/`question`/`answer`)에 **공유 넥스트키 락**을 건다 | `performance_schema.data_locks`: 소스 테이블에 `S` 3건, 대상 테이블에 `X,GAP` 2건 | ① **적용 시점 구 인스턴스 0대**(유지보수 창) — 유일하게 확실한 수단 ② `SET SESSION innodb_lock_wait_timeout = 10`(즉시 유효) ③ `READ COMMITTED`(**Flyway 트랜잭션 내에서는 현재 트랜잭션에 무효 — 확인 필요**) ④ JDBC URL `sessionVariables=transaction_isolation='READ-COMMITTED'` |
| `DELETE FROM generated_question`(WHERE 없음)이 전 행 X 락 + 갭 락 → 신규 질문 INSERT까지 차단 | — | 동일 |
| V54의 `ALTER TABLE`이 배타 MDL을 요구하고 `lock_wait_timeout` 기본값이 **31536000초(1년)** → 매달리면 MDL 큐 FIFO로 후속 쿼리 전량 정지, 신 인스턴스는 Flyway가 반환하지 않아 startup probe에 죽고 `success=0`만 남는다 | — | `SET SESSION lock_wait_timeout = 15` (V54 0단계, 즉시 유효) + D단계 (10) 사전 확인 |

**배포 절차 게이트: 마이그레이션 적용 시점에 구 인스턴스가 0대여야 한다.** 롤링 배포로 V53/V54를 적용하지 않는다.

#### H. Redis 잔류물 — 무해 확인됨

구 비회원 평가 결과는 `resume:evaluation:nonmember:{uuid}`에 저장되고 TTL이 `Duration.ofMinutes(5)`다(실측). 코드 삭제 후 5분 내 전량 자연 소멸하므로 정리 작업이 필요 없다. 구 플로우에 게스트 IP 락 같은 장기 TTL 키는 없다.

### 3-10. 부록 — 대량 데이터용 V53 변형 (§3-9-E 감사 결과 행 수가 클 때만)

현 서브쿼리는 `EXPLAIN DELETE` 실측에서 **대상 테이블이 드라이빙 테이블(`type = ALL`, 풀스캔)** 이 되고 서브쿼리가 semijoin으로 eq_ref 전개된다. `answer_like`가 10만 행을 넘으면 아래로 바꾼다. **판단선을 넘지 않으면 바꾸지 않는다.**

```sql
-- 임시 테이블로 대상 id를 먼저 확정한다. 계획이 결정적이 되고 감사 근거도 함께 남는다.
-- CREATE TEMPORARY TABLE은 DDL이라 암묵 커밋을 일으키지만, 이 파일의 안전성 근거는 트랜잭션이
-- 아니라 DELETE의 멱등성이므로 손실되는 보증이 없다. 세션 종료 시 자동 소멸한다.
-- MySQL은 한 문장에서 같은 TEMPORARY 테이블을 두 번 참조할 수 없다. 아래는 전부 1회 참조다.
--
-- 주의: INSERT ... SELECT 도 REPEATABLE READ에서는 소스에 공유 넥스트키 락을 잡는다.
-- 이 변형만으로는 락 문제가 해결되지 않는다. 0단계의 READ COMMITTED(또는 유지보수 창)가 함께 필요하다.
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_purge_interview (id BIGINT NOT NULL PRIMARY KEY) ENGINE = InnoDB;
DELETE FROM tmp_purge_interview;
INSERT INTO tmp_purge_interview (id)
SELECT id FROM interview WHERE interview_type = 'RESUME_BASED';

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_purge_question (id BIGINT NOT NULL PRIMARY KEY) ENGINE = InnoDB;
DELETE FROM tmp_purge_question;
INSERT INTO tmp_purge_question (id)
SELECT q.id FROM question q JOIN tmp_purge_interview t ON t.id = q.interview_id;

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_purge_answer (id BIGINT NOT NULL PRIMARY KEY) ENGINE = InnoDB;
DELETE FROM tmp_purge_answer;
INSERT INTO tmp_purge_answer (id)
SELECT a.id FROM answer a JOIN tmp_purge_question t ON t.id = a.question_id;

-- 자식부터. JOIN DELETE로 임시 테이블을 드라이빙 테이블로 만든다.
DELETE al FROM answer_like al    JOIN tmp_purge_answer t    ON t.id = al.answer_id;
DELETE am FROM answer_memo am    JOIN tmp_purge_answer t    ON t.id = am.answer_id;
DELETE a  FROM answer a          JOIN tmp_purge_answer t    ON t.id = a.id;
DELETE q  FROM question q        JOIN tmp_purge_question t  ON t.id = q.id;
DELETE il FROM interview_like il JOIN tmp_purge_interview t ON t.id = il.interview_id;
DELETE i  FROM interview i       JOIN tmp_purge_interview t ON t.id = i.id;
DELETE FROM generated_question;

DROP TEMPORARY TABLE tmp_purge_answer;
DROP TEMPORARY TABLE tmp_purge_question;
DROP TEMPORARY TABLE tmp_purge_interview;
```

`IF NOT EXISTS` + 선행 `DELETE FROM tmp_*` 조합으로 멱등성을 유지한다 — **확인 필요:** Flyway가 실패한 마이그레이션의 커넥션을 재사용하는지 미확인이므로 임시 테이블이 남아 있는 경우를 대비한다.

---

## 4. 5지표 정의와 프롬프트

### 4-1. 신규 5지표와 구지표 매핑

| 신규 | 유래 | 순증/순감 |
|---|---|---|
| `problem_solving` | 구 `problem_solving` | 데이터 기반 원인 분석, 대안 검토, 예기치 못한 변수 대처, Lesson Learned, **대응 적절성** 추가 |
| `project_experience` | 구 `project_experience` | 생애주기 참여 단계, **사후 관리(유지보수·모니터링·고도화)** 추가 |
| `technical_skills` | 구 `technical_skills` | **교차 검증 링크(GitHub/블로그/논문)**, 자격증·수상·교육 추가. "최신 트렌드 반영도" 제거(이력서 관찰 신뢰도 낮음) |
| `soft_skills` | 신규. 구 `documentation`의 STAR·문서 구조 관찰항목만 흡수 | "오탈자·형식 완성도" 폐기(PDF 텍스트 추출 후 형식 판단 불가) |
| `jd_fit` | 신규. 구 `career_growth`의 이직 횟수·근속·공백기만 흡수 | JD 제공 시에만 산출 |

폐기 관찰항목(어느 차원에서도 채점되지 않음). 구 프롬프트가 삭제되므로 아래 표가 원문의 유일한 기록이고 §8-4의 회귀 가드가 이 문자열을 단정한다. **구 원문 그대로** 적는다(실측 `ResumePromptFragments.java`):

| 구 차원 | 구 원문 (정확) | 원본 위치 |
|---|---|---|
| `career_growth` | `경력 발전 경로의 논리성` | 구 클래스 74행 |
| `career_growth` | `지속적인 학습 및 성장 증거` | 구 클래스 75행 |
| `career_growth` | 신입 잠재성 관련 항목 | 구 클래스 76행 이하 |
| `documentation` | `오탈자 및 형식 완성도` | 구 클래스 81행 |

JD 미제공 시 `jd_fit`의 관찰항목 전부도 폐기 대상이다(D4).

**주의:** `"지속적 학습"`은 구 원문 `"지속적인 학습 및 성장 증거"`의 부분 문자열이 **아니므로**, §8-4의 회귀 단정은 반드시 `"지속적인 학습"`으로 적어야 한다(그렇지 않으면 그 불릿을 되살려도 가드가 통과한다).

### 4-2. 프롬프트 클래스 배치

```
src/main/java/com/samhap/kokomen/resume/tool/
├── ResumeAnalysisPromptFragments.java        (상수 전용, public final — 유일본)
├── ResumeAnalysisSystemMessages.java         (jdProvided 조립)
├── ResumeAnalysisToolNames.java              (상수 2개)
├── ResumeAnalysisEvaluationResultRenderer.java
├── ResumeAnalysisUserMessages.java
└── PdfTextExtractor.java                     (존치, §1-3-4)

삭제: ResumePromptFragments.java, ResumeSystemMessages.java, ResumeToolNames.java
```

**참조 정책은 폐기된다.** 신규 클래스가 모든 프롬프트 조각의 유일본이며, 조각을 고치면 평가·질문 두 프롬프트가 함께 바뀐다(의도된 동작).

**선결 과제 — 상수 5개 이전 (구 클래스 삭제보다 먼저).** 실측 참조 지점:

| 파일 | 줄 | 참조 | 성격 |
|---|---|---|---|
| `ResumeAnalysisSystemMessages` | 22 | `ResumePromptFragments.SECURITY_RULES` | **코드** |
| " | 23 | `SENIOR_INTERVIEWER_LENS` | **코드** |
| " | 57 | `PERSONA_RECRUITER` | **코드** |
| " | 83 | `PERSONA_INTERVIEWER` | **코드** |
| " | 85 | `QUESTION_PROBE_LENS` | **코드** |
| " | 11 | `{@link ResumeSystemMessages}` | Javadoc |
| `ResumeAnalysisPromptFragments` | 91, 110, 222 | `{@code ResumePromptFragments...}` | Javadoc **only — 코드 참조 0건** |
| `ResumeAnalysisToolNames` | 5 | `구 {@code ResumeToolNames}와 …` | Javadoc |
| `ResumeAnalysisSystemMessageConsistencyTest` | 46, 51, 123, 124, 140, 142, 152, 168, 171 | 구 심볼 | **테스트 코드** (§8-4) |
| `ResumeAnalysisFlatSchemaTest` | 174, 177 | `ResumeToolNames.*` | **테스트 코드** (§8-3) |

실제 이전 작업은 `ResumeAnalysisSystemMessages` 5줄 + 테스트 참조 정리다.

**Step 1.** 상수 5개를 **바이트 동일**로 이전. 원본 위치(`ResumePromptFragments.java`): `PERSONA_INTERVIEWER :5` / `PERSONA_RECRUITER :7` / `QUESTION_PROBE_LENS :9–19`(텍스트블록 `:11–18`) / `SECURITY_RULES :183–188` / `SENIOR_INTERVIEWER_LENS :190–199`. 배치는 클래스 선두(`CRITERIA_INTRO` 앞), 페르소나 → 보안 → 렌즈 순.

**Step 2.** 클래스 Javadoc 교체.

```java
/**
 * 이력서 분석(5지표) 프롬프트 조각의 정본이자 유일본. 평가·질문 두 시스템 메시지가 모두 이 클래스에서
 * 조립되며, GPT와 Bedrock이 같은 문자열을 쓴다(ResumeAnalysisWiringTest가 강제).
 * 조각을 고치면 두 프로바이더의 프롬프트가 함께 바뀐다.
 */
```

"복사본" Javadoc 3개는 가리킬 원본이 없어져 오독을 부르므로 재작성한다.

```java
-    /** 구 {@code ResumePromptFragments.EVALUATION_CRITERIA} 내부 {@code <improvement_rules>} 문구 무수정 복사. */
+    /** improvements는 이 평가의 최우선 산출물이다. 문형·금지 유형을 여기서 고정한다. */
     public static final String IMPROVEMENT_RULES = """

-    /** 구 {@code ResumePromptFragments.EVALUATION_CRITERIA} 내부 {@code <improvement_examples>} 문구 무수정 복사. */
+    /** IMPROVEMENT_RULES의 권장 문형을 나쁨/좋음 쌍으로 예시한다. IMPROVEMENT_RULES 바로 뒤에 주입돼야 한다
+     *  — IMPROVEMENT_RULES 본문이 이 블록을 전방 참조한다. */
     public static final String IMPROVEMENT_EXAMPLES = """

-    /** 구 {@code ResumePromptFragments.QUESTION_GENERATION_GUIDE} 복사 후 8번 항목만 추가한 신규판. */
+    /** 8번 항목이 <evaluation_result> 활용 규칙이며 <evaluation_grounding_rule>과 짝을 이룬다. */
     public static final String QUESTION_GENERATION_GUIDE = """
```

**Step 3.** `ResumeAnalysisSystemMessages` 5줄 + Javadoc.

```java
 /**
- * 이력서 분석(신규 5지표) 시스템 메시지의 GPT·Bedrock 공용 단일 소스.
- * 구 {@link ResumeSystemMessages}는 동결이므로 {@code evaluation(boolean)} 오버로드를 그쪽에 추가하지 않는다.
+ * 이력서 분석(5지표) 시스템 메시지의 GPT·Bedrock 공용 단일 소스.
   * {@code questionGeneration()}은 의도적으로 무인자다: 평가 결과는 user 메시지에만 주입하며,
   * system을 요청별로 바꾸면 Bedrock 캐시 프리픽스가 요청마다 갈려 캐시가 전면 무효화된다(D8).
   */
@@ :22-23
-        fragments.add(ResumePromptFragments.SECURITY_RULES);
-        fragments.add(ResumePromptFragments.SENIOR_INTERVIEWER_LENS);
+        fragments.add(ResumeAnalysisPromptFragments.SECURITY_RULES);
+        fragments.add(ResumeAnalysisPromptFragments.SENIOR_INTERVIEWER_LENS);
@@ :57
-                ResumePromptFragments.PERSONA_RECRUITER,
+                ResumeAnalysisPromptFragments.PERSONA_RECRUITER,
@@ :83-85
-                ResumePromptFragments.PERSONA_INTERVIEWER,
+                ResumeAnalysisPromptFragments.PERSONA_INTERVIEWER,
                 ResumeAnalysisPromptFragments.QUESTION_GENERATION_GUIDE,
-                ResumePromptFragments.QUESTION_PROBE_LENS,
+                ResumeAnalysisPromptFragments.QUESTION_PROBE_LENS,
```

**Step 4 — 골든 대조 (필수 게이트 G4).** 이전 **전** `evaluation(true)` / `evaluation(false)` / `questionGeneration()` 3개를 파일로 덤프하고, 이전 **후** `diff`로 **0바이트 차이**를 확인한다. `ResumeAnalysisWiringTest`는 GPT/Bedrock 문자열 동일성만 보므로 내용이 변해도 통과한다 — 이 대조가 유일한 방어선이다.

**해소되는 finding.** Task 4 parked Important(~3.3KB 바이트 동일 복사, 소스로의 기계적 연결 없음)가 **자동 해소**된다 — 원본이 존재하지 않아 결함 조건이 소멸하고 복사본이 유일본이 된다. 리뷰어 완화책 2건은 무효화: (a) "소스의 정확한 줄 범위 명기"는 가리킬 소스가 없어 Javadoc 삭제가 옳고, (b) "구 클래스 삭제 예정 명시"는 실행됐다.

`ResumeAnalysisToolNames` Javadoc 재작성 — 근거 (1)(구/신 장애 로그 분리)은 소멸, (2)만 유효.

```java
/**
 * 이력서 분석 도구/함수 이름의 단일 소스. GPT(function)와 Bedrock(tool)이 동일 이름을 쓴다.
 * 같은 이름으로 jdProvided에 따라 두 가지 스키마를 보내므로, 파싱 실패 로그의 toolName만으로는
 * 어느 스키마였는지 구분되지 않는다(호출 로그의 jdProvided를 함께 본다).
 */
```

`{@link}` 깨짐이 빌드를 깨지는 않는다 — `build.gradle`에 `javadoc` 태스크도 doclint 설정도 없다(실측). 그래도 정리한다.

**추가 정리 판단 2건:** `INDEPENDENCE_PRINCIPLE`은 유일본이 되므로 손대지 않는다. `EVALUATION_INSTRUCTION` ↔ `IMPROVEMENT_RULES` ↔ `IMPROVEMENT_EXAMPLES` 3분할은 유지하고 순서 의존성을 Step 2 Javadoc에 명기하는 것으로 충분하다.

### 4-3. 조립 코드

```java
public final class ResumeAnalysisSystemMessages {

    private ResumeAnalysisSystemMessages() {
    }

    public static String evaluation(boolean jdProvided) {
        List<String> fragments = new ArrayList<>();
        fragments.add(ResumeAnalysisPromptFragments.SECURITY_RULES);
        fragments.add(ResumeAnalysisPromptFragments.SENIOR_INTERVIEWER_LENS);
        fragments.add(evaluationCriteria(jdProvided));
        fragments.add(jdProvided
                ? ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITH_JD
                : ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITHOUT_JD);
        fragments.add(ResumeAnalysisPromptFragments.EVALUATION_INSTRUCTION);
        fragments.add(ResumeAnalysisPromptFragments.IMPROVEMENT_RULES);
        fragments.add(ResumeAnalysisPromptFragments.IMPROVEMENT_EXAMPLES);
        fragments.add(ResumeAnalysisPromptFragments.SOFT_SKILLS_NEUTRAL_BASELINE);
        fragments.add(jdProvided
                ? ResumeAnalysisPromptFragments.JD_POLICY_PROVIDED
                : ResumeAnalysisPromptFragments.JD_POLICY_ABSENT);
        fragments.add(ResumeAnalysisPromptFragments.INDEPENDENCE_PRINCIPLE);
        fragments.add(scoreAnchors(jdProvided));

        List<String> dimensionKeys = ResumeAnalysisSchema.dimensionKeys(jdProvided);
        return """
                <role>
                %s
                </role>

                <task>
                10년차 시니어 면접관의 시선으로, 지원 직무와 (제공된 경우) 채용 공고를 기준 삼아 이력서와 포트폴리오를 검증하듯 종합 분석하여 차원별 객관적 평가와 점수를 산출하고, 지원자가 이력서에서 곧바로 실행할 수 있는 구체적 보완점을 도출하라.
                </task>

                %s

                <output>
                제공된 도구를 호출하여 다음 필드를 모두 제출하라.
                - %d개 차원(%s) 각각에 대해 {차원}_reasoning(점수 산정 전 사고 과정), {차원}_score(0-100, score_anchors 기준), {차원}_reason(평가 이유 배열, 2-6개), {차원}_improvements(보완 사항 배열, 2-6개)를 제출한다(예: problem_solving_score, problem_solving_reason).
                - total_feedback : 강점·개선·학습 방향을 포함한 종합 총평(한 단락). improvements 중 지원자가 가장 먼저 고쳐야 할 1~2개를 우선순위로 지목한다.
                - 도구 입력 스키마에 없는 필드는 절대 만들어 내지 않는다. (종합 점수는 서버에서 가중평균으로 재계산하므로 별도 출력하지 않는다.)
                </output>
                """.formatted(
                ResumeAnalysisPromptFragments.PERSONA_RECRUITER,
                joinFragments(fragments),
                dimensionKeys.size(),
                String.join(", ", dimensionKeys));
    }

    public static String questionGeneration() {
        return """
                <role>
                %s
                </role>

                <task>
                제공된 이력서, 포트폴리오, 직무 경력 정보와 <evaluation_result>(같은 문서에 대해 방금 수행된 평가 결과)를 함께 분석하여, 기술 면접에서 물어볼 핵심 질문들을 생성하라.
                </task>

                %s

                %s

                %s

                <output>
                제공된 도구를 호출하여 questions 배열을 제출하라. 각 항목은 question(질문 내용)과 reason(질문 선정 이유)을 포함해야 한다.
                </output>
                """.formatted(
                ResumeAnalysisPromptFragments.PERSONA_INTERVIEWER,
                ResumeAnalysisPromptFragments.QUESTION_GENERATION_GUIDE,
                ResumeAnalysisPromptFragments.QUESTION_PROBE_LENS,
                ResumeAnalysisPromptFragments.EVALUATION_GROUNDING_RULE);
    }

    private static String evaluationCriteria(boolean jdProvided) {
        return """
                <evaluation_criteria>
                %s
                %s%s</evaluation_criteria>
                """.formatted(
                ResumeAnalysisPromptFragments.CRITERIA_INTRO,
                ResumeAnalysisPromptFragments.DIMENSIONS_BASE,
                jdProvided ? ResumeAnalysisPromptFragments.DIMENSION_JD_FIT : "");
    }

    private static String scoreAnchors(boolean jdProvided) {
        return """
                <score_anchors>
                %s
                %s%s</score_anchors>
                """.formatted(
                ResumeAnalysisPromptFragments.ANCHORS_INTRO,
                ResumeAnalysisPromptFragments.ANCHORS_BASE,
                jdProvided ? ResumeAnalysisPromptFragments.ANCHOR_JD_FIT : "");
    }

    private static String joinFragments(List<String> fragments) {
        return fragments.stream()
                .filter(fragment -> fragment != null && !fragment.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
```

`<evaluation_result>`는 **user 메시지에만** 넣는다. system을 요청별로 바꾸면 `appendCachePoint`의 캐시 프리픽스가 요청마다 갈려 캐시가 전면 무효화된다.

### 4-4. `<evaluation_criteria>` 전문

`CRITERIA_INTRO`:

```
각 차원은 0-100점으로 평가한다. 아래 세부 관찰항목은 채점 체크리스트이며, 이력서/포트폴리오에서 실제로 관찰되는 항목만 근거로 사용한다. 체크리스트의 모든 항목이 채워져야 만점인 것은 아니고, <job_career>(연차) 기준에서 기대되는 항목이 갖춰졌는지로 판단한다. 종합 점수는 서버에서 가중평균으로 계산하므로 출력하지 않는다.
```

`DIMENSIONS_BASE` (JD 유무와 무관하게 항상 포함):

```
1. 문제 해결 (problem_solving)
  - 과거 직무·프로젝트에서 발생한 구체적인 문제 상황(Pain Point)이 어느 프로젝트의 어떤 상황인지 특정되어 기재됐는가. "이슈를 해결했다" 수준의 뭉뚱그린 서술은 특정된 것으로 보지 않는다.
  - 문제의 원인·배경을 직관이나 짐작이 아니라 데이터·로그·지표·측정값으로 분석한 흔적이 있는가.
  - 기존 방식이나 매뉴얼을 그대로 따르지 않고 새로운 대안·아이디어를 제시했는가, 그리고 배제한 대안과 그 이유가 드러나는가.
  - 해결 과정 중 발생한 예기치 못한 변수나 장애물, 그에 대한 대처 과정이 서술됐는가.
  - 최종 결과와 그것을 확인한 검증 방법(측정 지표, 전후 비교)이 함께 있는가, 그리고 그 경험에서 얻은 인사이트·교훈(Lesson Learned)이 서술됐는가.
  - 선택한 해결 방식이 분석된 원인에 맞는 적절한 대응이었는가. 원인과 무관한 조치, 근본 원인을 덮는 우회, 문제 규모에 비해 과도한 대응은 그 사실을 근거로 지적한다.
2. 프로젝트 경험 (project_experience)
  - 프로젝트의 목표, 기간, 규모(인원·트래픽·데이터량 등), 해결하려던 문제 정의가 명확히 기재됐는가.
  - 전체 프로젝트 중 지원자가 '직접' 담당한 역할과 범위가 팀 전체의 성과와 구분되어 있는가.
  - 기획, 개발·실행, 테스트, 배포 등 프로젝트 생애주기 중 어느 단계에 어떻게 참여했는지가 구체적인가.
  - 매출 증가, 비용 절감, 리드타임 단축, 응답시간 개선 등 성과가 정량 지표로 파악 가능한가. 수치가 있다면 무엇을 기준으로 측정한 값인지 드러나는가.
  - 프로젝트 종료 이후 유지보수, 모니터링, 고도화 등 사후 관리 경험이 포함됐는가.
3. 기술 역량 (technical_skills)
  - 해당 직무에 필요한 핵심 하드스킬(프로그래밍 언어, 프레임워크, 툴, 인프라)의 명칭이 명확히 열거됐는가.
  - 각 기술 스택의 활용 수준·숙련도를 유추할 근거(사용 기간, 적용한 프로젝트 수, 담당 범위)가 있는가.
  - 단순 툴 사용을 넘어 기술적 난제(아키텍처 설계, 성능 최적화, 트래픽·데이터 규모 대응, 장애 대응)를 해결한 기록이 있는가.
  - 포트폴리오, GitHub, GitLab, 개인 기술 블로그, 논문 등 기술력을 교차 검증할 수 있는 링크·산출물이 포함됐는가.
  - 직무 관련 자격증, 수상 경력, 전문 교육 등 기술 역량을 보강하는 부수 항목이 있는가. 있으면 가점 근거로 쓰되, 없다는 사실은 감점 사유로 쓰지 않는다.
4. 소프트스킬 (soft_skills)
  - 이력서 전체 문맥이 STAR(Situation, Task, Action, Result) 구조에 맞춰 논리적이고 간결하게 작성됐는가. 이 항목은 문서만으로 항상 관찰 가능하므로 반드시 채점 근거에 포함한다.
  - 협업 프로젝트에서 본인이 담당한 역할, 협업 대상(직군·부서·고객사), 의사소통 방식이 문서에 명시됐는가. 이 항목도 문서만으로 항상 관찰 가능하다.
  - 기술 블로그, 발표·세미나, 사내 문서·문서화 작업, 오픈소스 기여, README 등 커뮤니케이션 산출물이 근거로 제시됐는가.
  - 지시받은 업무 범위를 넘어 스스로 문제를 발굴하고 먼저 실행에 옮긴 주도적 경험이 문서에 기재됐는가.
  - 다음 항목들은 이력서에 근거가 기재되어 있을 때에만 채점한다: 타 부서·고객사·동료와의 의견 조율 및 갈등 해결 사례, 파트 리딩·멘토링 등 공식·비공식 리더십 발휘 사례, 조직 개편·피벗·촉박한 마감 등 급격한 환경 변화에 유연하게 대처한 사례. 기재가 없으면 '미기재'로 처리하며, 그 부재를 감점 사유로 삼지 않는다(<soft_skills_neutral_baseline> 참조).
```

`DIMENSION_JD_FIT` (JD 제공 시에만 append):

```
5. JD 적합성 (jd_fit)
  - 공고(JD)에서 요구하는 필수 총 경력 연차 요건(예: 3년 이상)을 만족하는가. 요건에 미달하더라도, 이력서 내 기술 역량과 문제 해결의 깊이가 채용을 고려할 수준인지 별도로 판단하고 그 근거를 남긴다. 연차 미달을 자동으로 최하 밴드로 처리하지 않는다.
  - JD의 '주요 업무'에 기재된 키워드와 지원자의 과거 업무 키워드가 매칭되는가. 각 항목을 [매칭 / 부분 매칭 / 미매칭]으로 판단한다.
  - JD의 '우대 사항'(특정 자격증, 외국어, 특정 툴 숙련도 등)에 부합하는 키워드가 이력서에 존재하는가.
  - 채용 기업의 산업군(핀테크, 커머스, 제조 등)이나 비즈니스 모델(B2B, B2C)과 유사한 도메인 경험이 있는가.
  - 과거 이직 횟수, 근속 기간, 공백기 등을 고려할 때 커리어의 일관성과 안정성이 확보되었는가. 공백·이직에 대한 합리적 설명이 이력서에 기재되어 있다면 그 설명을 근거로 인정한다. 설명이 없는 경우 '확인 불가'로 기록하고 추측으로 사유를 만들지 않는다.
```

`SCORING_WEIGHTS_WITH_JD`:

```
<scoring_weights>
종합 점수는 서버에서 아래 가중치로 계산하므로 출력하지 않는다. 아래 값은 각 차원의 상대적 중요도를 이해하기 위한 참고용이며, 가중치가 높다고 그 차원을 후하게 주라는 뜻이 아니다.
- problem_solving 0.25
- project_experience 0.25
- technical_skills 0.25
- soft_skills 0.10
- jd_fit 0.15
</scoring_weights>
```

`SCORING_WEIGHTS_WITHOUT_JD`:

```
<scoring_weights>
종합 점수는 서버에서 아래 가중치로 계산하므로 출력하지 않는다. 아래 값은 각 차원의 상대적 중요도를 이해하기 위한 참고용이며, 가중치가 높다고 그 차원을 후하게 주라는 뜻이 아니다.
- problem_solving 0.30
- project_experience 0.30
- technical_skills 0.30
- soft_skills 0.10
이번 평가에서 jd_fit 차원은 산출하지 않으며 가중치도 존재하지 않는다.
</scoring_weights>
```

가중치 수치는 프롬프트 문자열과 `ResumeAnalysisWeights` 두 곳에 존재하므로, **프롬프트 문자열이 enum의 각 항목을 `"- {toolKey} {weight}"` 형태로 포함하는지 단정하는 테스트**로 동기화를 강제한다(§8-2).

`EVALUATION_INSTRUCTION`:

```
<evaluation_instruction>
- 점수는 score_anchors 기준으로 엄격하게 평가하며, 각 차원의 reasoning에 점수 산정 근거를 먼저 정리한 뒤 score를 산출한다. 근거가 확인되지 않는 주장은 사실로 인정하지 않으며, "잘 했을 것"이라는 선의의 추정으로 점수를 올리지 않는다.
- 강점(reason)은 이력서/포트폴리오에 실제로 기재된 문장·수치·프로젝트명·기술명을 지목·인용하여 근거와 함께 작성하고, 그 강점이 지원 직무·연차 기준에서 왜 유의미한지를 밝힌다. 근거 없는 칭찬("전반적으로 우수함", "~해 보인다" 식 추측)은 작성하지 않는다.
- 지원자가 실제로 수행한 역할과 책임에 초점을 맞춰 평가하며, 팀 성과와 개인 기여가 구분되지 않는 서술은 개인 기여가 불분명한 것으로 보고 그 사실을 improvements에서 지적한다.
- reason과 improvements는 각각 2-6개 항목의 배열이며, 각 항목은 서로 다른 내용을 담은 정보 밀도 높은 1-2문장이다(여러 내용을 한 항목에 뭉쳐 넣지 않는다).
</evaluation_instruction>
```

`IMPROVEMENT_RULES` / `IMPROVEMENT_EXAMPLES`는 구 `ResumePromptFragments.EVALUATION_CRITERIA`(구 클래스, 삭제됨) 내부의 `<improvement_rules>`/`<improvement_examples>` 문구를 **문구 무수정 복사**한 것이며, 구 클래스 삭제 후 이 신규 클래스가 유일본이다(§4-2).

### 4-5. `<score_anchors>` 전문

`ANCHORS_INTRO`:

```
차원별 기준 anchor. 점수 산정 시 가장 가까운 anchor에 맞춘다.
아래 anchor 서술은 절대 난이도가 아니라 <job_career> 연차의 기대치에 상대적으로 해석한다. 신입에게는 해당 연차에서 기대되는 최상위 수준의 근거가 갖춰지면 90-100으로 본다. 시스템 설계·대규모 트래픽처럼 연차상 기대되지 않는 항목의 부재를 상위 밴드 미달의 근거로 쓰지 않는다.
soft_skills는 다른 차원과 채점 기준점이 다르다. 관찰 근거가 없을 때 0점에서 시작하지 않고 중립 기준점 밴드(50-59)에서 시작한다(<soft_skills_neutral_baseline> 참조).
```

`ANCHORS_BASE`:

```
<anchor category="problem_solving">
90-100: 문제 상황 특정 + 데이터 기반 원인 분석 + 대안 검토와 배제 이유 + 검증 수치 + 교훈이 한 사례 안에서 모두 확인되고, 예기치 못한 변수에 대한 대처까지 서술됨
70-89: 원인 분석과 해결 과정이 구체적이고 결과가 검증 가능하나, 대안 검토 또는 교훈 서술 중 일부가 빠짐
50-69: 문제와 결과는 있으나 원인 분석이 직관 수준이거나 해결 과정이 표면적
30-49: 결과만 기술되어 문제 상황·원인·검증을 구분할 수 없음, 또는 사례가 일반적 수준에 머무름
0-29: 문제 해결 관련 서술이 없음, 또는 원인과 무관한 조치만 기재되어 대응의 적절성을 인정할 수 없음
</anchor>

<anchor category="project_experience">
90-100: 목표·기간·규모·문제 정의가 모두 명시 + 본인이 직접 담당한 범위가 팀 성과와 명확히 구분 + 생애주기 참여 단계 구체 + 측정 기준이 드러나는 정량 성과 다수 + 사후 관리 경험
70-89: 본인 역할과 참여 단계가 명확하고 일부 정량 성과가 있으나, 사후 관리 또는 규모·기간 정보 일부가 누락
50-69: 프로젝트는 나열됐으나 본인 역할 범위 또는 성과 지표의 구체성이 부족
30-49: 팀 성과와 개인 기여가 뒤섞여 본인 기여를 특정할 수 없는 단순 참여 수준
0-29: 프로젝트 정보 부재 또는 목표·역할 자체가 불분명
</anchor>

<anchor category="technical_skills">
90-100: 핵심 스택이 명확히 열거되고 숙련도 근거(사용 기간·적용 범위)까지 확인 + 기술적 난제 해결 기록 + 교차 검증 가능한 산출물·링크 존재
70-89: 주력 스택의 실전 적용이 구체적이고 난제 해결 사례가 1건 이상 있으나, 교차 검증 산출물이나 숙련도 근거가 일부만 존재
50-69: 주력 스택은 명확하나 숙련도를 유추할 근거가 얇고 적용이 표면적
30-49: 기술명 나열만 있고 적용 맥락·수준을 유추할 근거가 없음
0-29: 기술 정보 부족 또는 지원 직무와 무관한 기술 위주
</anchor>

<anchor category="soft_skills">
90-100: STAR 구조가 문서 전반에 일관되고 협업 서술에서 본인 역할·협업 대상이 항상 명시됨 + 커뮤니케이션 산출물과 주도적 실행 사례가 구체적 근거로 확인됨
70-89: STAR 구조와 역할 구분이 대체로 지켜지고, 커뮤니케이션 산출물·주도성·조율/리딩·환경 변화 대처 중 최소 1개가 문서 근거로 확인됨
60-69: 문서 구조와 역할 서술은 읽어낼 수 있으나 협업·주도성 관련 근거가 산발적이거나 한두 문장에 그침
50-59: 중립 기준점. 문서가 기술·프로젝트 나열 중심이어서 관찰할 근거가 사실상 없어 판단을 보류하는 구간. 근거의 부재만으로는 이 밴드 아래로 내리지 않는다
30-49: 부정적 근거가 실제로 관찰됨 — 협업 서술이 팀 성과와 개인 기여를 구분하지 않아 본인 역할이 드러나지 않거나, 서술 간 내용이 상호 모순되거나, 결과 없는 나열만 이어져 STAR 구조가 성립하지 않음
0-29: 항목 간 연결이 없어 문서를 해독하기 어렵거나, 서술의 신뢰성을 훼손하는 모순이 반복됨
</anchor>
```

`ANCHOR_JD_FIT` (JD 제공 시에만 append):

```
<anchor category="jd_fit">
90-100: 필수 연차와 주요 업무 키워드가 대부분 충족 + 우대 사항 일부 충족 + 동일하거나 유사한 도메인 경험 + 근속·이직 흐름에서 커리어 일관성 확인
70-89: 필수 요건과 주요 업무 키워드가 다수 충족되나, 우대 사항 또는 도메인 유사성 중 하나가 약함
50-69: 필수 요건은 충족하나 주요 업무 키워드 매칭이 절반 수준이고 도메인 경험이 상이함
30-49: 필수 연차 또는 핵심 업무 요건에 미달하지만, 기술 역량과 문제 해결의 깊이에서 채용을 고려할 만한 보완 근거가 확인됨
0-29: 필수 요건에 미달하고 보완 근거도 확인되지 않음, 또는 지원자의 커리어 방향 자체가 공고와 상이함
</anchor>
```

### 4-6. D7 — 소프트스킬 중립 기준점

기존 두 규칙과의 관계:

| 라벨 | 위치 | 내용 |
|---|---|---|
| A | `ResumePromptFragments.java:85` | "근거가 확인되지 않는 주장은 사실로 인정하지 않으며, \"잘 했을 것\"이라는 선의의 추정으로 점수를 올리지 않는다." |
| B | `ResumePromptFragments.java:195` (`SENIOR_INTERVIEWER_LENS` 3번) | "측정하거나 검증할 수 없는 주장은 … '미검증'으로 취급하여, 강점 근거나 가점 사유로 쓰지 않는다." |
| C (신규) | `SOFT_SKILLS_NEUTRAL_BASELINE` | 근거 부재 시 0이 아니라 중립 기준점 밴드(50-59)에서 시작 |

A·B는 **상향 이동(가점)**을, C는 **근거 부재 시의 기준점 위치**를 규정하므로 논리적으로 겹치지 않는다. 세 규칙의 합: *침묵 → 중립, 관찰된 긍정 근거 → 상향, 관찰된 부정 근거 → 하향.* C가 A·B를 침범할 유일한 경로("중립 점수를 강점으로 서술")는 C 본문의 `soft_skills_reason` 기재 금지 + `soft_skills_reasoning` 명시 의무로 차단한다.

`SOFT_SKILLS_NEUTRAL_BASELINE`:

```
<soft_skills_neutral_baseline>
soft_skills는 다른 차원과 채점 기준점이 다르다. 개발자 이력서·포트폴리오 PDF에는 조직 내 상호작용이 기록되지 않는 것이 정상이므로, 근거의 '부재'와 근거의 '부정'을 반드시 구분한다.
- 관찰 근거가 없는 항목(갈등 조율, 멘토링·파트 리딩, 조직 개편·피벗 대응 등)은 '미기재'로 처리하고, 그 부재를 감점 사유로 쓰지 않는다. 이 경우 점수는 0에서 시작하지 않고 중립 기준점 밴드(50-59)에서 시작한다.
- 중립 기준점에서 점수를 올릴 수 있는 것은 문서에서 실제로 관찰된 근거뿐이다. 근거가 없는데 "협업을 잘했을 것"이라고 추정해 올리지 않는다. 추정 가점 금지는 이 차원에도 예외 없이 적용된다.
- 중립 기준점에서 점수를 내릴 수 있는 것은 문서에서 실제로 관찰된 부정적 근거뿐이다. 팀 성과와 개인 기여를 구분하지 않아 본인 역할이 드러나지 않는 협업 서술, 서로 모순되는 서술, 결과 없는 나열만 이어져 STAR 구조가 성립하지 않는 문서 구조가 그 예다. 이 근거를 사용할 때는 문서 구조 관점으로만 사용하고 성과 귀속 관점은 project_experience에 남긴다.
- 중립 기준점을 적용했다면 soft_skills_reasoning에 "관찰 근거 없음 → 중립 기준점 적용"이라고 명시한다. 관찰되지 않은 항목을 soft_skills_reason(강점 근거)에 쓰는 것은 금지한다. 중립 점수는 '강점이 확인됨'을 뜻하지 않는다.
- 채점 대상 관찰항목은 문서에서 확인 가능한 것으로 한정한다: (1) STAR 구조 준수 여부, (2) 협업 프로젝트에서 본인 역할·협업 대상 명시 여부, (3) 기술 블로그·발표·문서화·오픈소스 등 커뮤니케이션 산출물, (4) 스스로 문제를 발굴해 실행한 주도성의 기재, (5) 기재되어 있을 때에만 채점하는 조율·갈등 해결·리딩·멘토링·급격한 환경 변화 대처 사례.
- soft_skills_improvements는 "협업 경험을 쌓아라", "리더십을 발휘해 보라"처럼 이력서 밖의 일을 요구하지 않는다. "이미 이력서에 있는 OO 협업 프로젝트에 본인이 담당한 역할과 협업 대상 직군을 한 줄로 덧붙여라"처럼 지금 문서를 고치는 행동으로 쓴다.
</soft_skills_neutral_baseline>
```

`INDEPENDENCE_PRINCIPLE`:

```
<independence_principle>
각 차원은 독립적으로 평가한다. 한 차원의 점수가 다른 차원의 점수에 영향을 주지 않도록, 차원별로 고유한 근거만을 사용하라.
- technical_skills의 강점은 problem_solving 평가에 끌어다 쓰지 않는다. 같은 프로젝트를 근거로 삼더라도 technical_skills는 기술 선택·숙련도·난제 해결만, problem_solving은 문제 정의·원인 분석·검증 흐름만, project_experience는 역할 범위·정량 성과·생애주기만 본다.
- jd_fit의 공고 대조 결과를 다른 차원의 점수 근거로 쓰지 않는다. 반대로 다른 차원의 강점을 jd_fit 점수 근거로 재사용하지 않는다.
- 두 차원에 걸칠 수 있는 근거는 관점을 나눠 쓴다. 팀 성과와 개인 기여가 뒤섞인 서술은 project_experience에서는 성과 귀속(본인 기여를 특정할 수 있는가) 관점으로만, soft_skills에서는 문서 구조(협업 대상과 본인 역할이 명시되어 있는가) 관점으로만 사용한다.
- 한 차원에서 강했다고 다른 차원도 후하게 주지 않는다(halo effect 금지).
- 한 차원에서 약했다고 다른 차원도 박하게 주지 않는다(horn effect 금지).
- 각 차원의 reasoning에는 그 차원에 한정된 근거만 작성한다.
</independence_principle>
```

### 4-7. D4 — JD 미제공 시 `jd_fit` 미산출

구 `<job_alignment>` 처분:

| 구 문장 | 처분 | 신규 위치 |
|---|---|---|
| "채용 공고를 1순위 기준으로 삼는다" | **삭제** | JD를 `jd_fit`으로 격리하므로 다른 4차원을 지배하는 "1순위 기준" 개념 폐기 |
| "[충족/부분 충족/미충족] 판단 + improvements 형태" | 유지(범위를 `jd_fit`으로 한정) | `JD_POLICY_PROVIDED` 1·2 |
| "공고에 없는 역량이라는 이유만으로 … 눈감아 주지도 않는다" | 문구 그대로 유지 | `JD_POLICY_PROVIDED` 3 |
| "**JD 부재 자체를 감점 사유로 삼거나 \"공고를 확인하라\"는 식의 조언을 하지 않는다**" | **문구 그대로 유지** | `JD_POLICY_ABSENT` 2 |
| "존재하지 않는 공고 요구사항을 지어내지 않는다" + "업계 일반 기대치 기준" | 유지(대상을 "나머지 네 차원"으로 명시) | `JD_POLICY_ABSENT` 2·3 |
| "공고 대조 결과는 억지로 나누어 넣지 말라" | **강화**(전이 전면 금지) | `JD_POLICY_PROVIDED` 5 |
| "<job_career> 연차 기대치 기준" | 유지 | 양쪽 |

L117을 유지하는 이유: 신규 설계에서 JD 부재는 `jd_fit` 차원의 소멸로 처리되지만, 모델이 남은 4차원에서 "지원 직무 요구가 불명확하다"를 이유로 깎을 위험은 그대로 남는다. 이 문장의 보호 대상이 "`jd_fit` 점수"에서 "나머지 4차원 점수"로 바뀌었을 뿐이다.

`JD_POLICY_PROVIDED`:

```
<jd_policy>
user 메시지에 <job_requirements>(채용 공고)가 제공되었다. 공고와의 대조는 jd_fit 차원에서만 수행한다.
- 공고가 요구하는 핵심 역량·기술·경험을 식별한 뒤 이력서/포트폴리오의 근거와 대조하여 각 요구 항목을 [충족 / 부분 충족 / 미충족]으로 판단하고, 그 판단을 jd_fit_reasoning에 먼저 정리한 뒤 jd_fit_score를 산출한다.
- jd_fit_improvements는 미충족·부분 충족 항목을 메우는 방향으로 "공고가 요구하는 X 대비 이력서에는 Y 수준의 근거만 있으므로 …" 형태로 작성한다.
- 공고에 없는 역량이라는 이유만으로 지원자의 유효한 강점을 감점하지 않고, 공고가 요구하지만 이력서에 없는 항목을 눈감아 주지도 않는다.
- 필수 연차 요건에 미달하더라도 기술 역량과 문제 해결의 깊이가 채용을 고려할 수준이면 그 근거를 jd_fit_reasoning에 명시하고 점수에 반영한다.
- 공고 대조 결과를 problem_solving·project_experience·technical_skills·soft_skills의 점수 근거나 improvements로 전이하지 않는다. 그 네 차원은 <target_position>과 <job_career>만을 기준으로 평가한다.
- <job_career>에 적힌 연차 수준에 맞는 기대치를 기준으로 삼는다(신입에게 시니어 기준을, 시니어에게 신입 기준을 적용하지 않는다).
</jd_policy>
```

`JD_POLICY_ABSENT`:

```
<jd_policy>
user 메시지에 <job_requirements>(채용 공고)가 제공되지 않았다. 이번 평가에서 jd_fit 차원은 산출하지 않는다.
- 제공된 도구의 입력 스키마에는 jd_fit_reasoning·jd_fit_score·jd_fit_reason·jd_fit_improvements 필드가 존재하지 않는다. 이 필드들을 만들어 출력하려 시도하지 않는다.
- 존재하지 않는 공고 요구사항을 지어내거나 특정 회사의 요구사항을 상상하지 않는다. <target_position>(지원 직무)에 대한 업계 일반 기대치를 기준으로 평가하며, JD 부재 자체를 감점 사유로 삼거나 "공고를 확인하라"는 식의 조언을 하지 않는다.
- 나머지 네 차원(problem_solving, project_experience, technical_skills, soft_skills)은 <target_position>에 대한 업계 일반 기대치와 <job_career>(연차) 기대치만을 기준으로 평가한다.
- 공고가 없다는 사실을 어떤 차원의 improvements에도 쓰지 않는다("공고 키워드를 반영하라" 류 금지). 또한 jd_fit이 담당하는 관찰항목(필수 연차 요건 충족 여부, 주요 업무·우대 사항 키워드 매칭, 산업군·도메인 유사성, 이직 횟수·근속 기간·공백기)을 다른 네 차원의 점수 근거로 전이하지 않는다. 이번 평가에서 그 관찰항목들은 채점 대상이 아니다.
- <job_career>에 적힌 연차 수준에 맞는 기대치를 기준으로 삼는다.
</jd_policy>
```

### 4-8. 질문 콜 프롬프트와 `<evaluation_result>` 주입 (D8)

주입 결정:

| 항목 | 주입 | 근거 |
|---|---|---|
| `{dim}_score` | 전량 | 정수 5개 = 무시 가능한 비용. 낮은 차원에 질문을 더 배분하는 기준 |
| `{dim}_improvements` | 전량(최대 6개) | 이력서 본문에 **없는** 정보(평가자가 찾아낸 검증 공백) = 질문 표적 선정의 유일한 순증 정보 |
| `{dim}_reason` | 차원별 앞 2개 | 이력서 문장·수치를 인용한 항목이라 같은 user 메시지의 `<resume>` 본문과 중복. 대표 2개로 "검증할 주장" 지목 목적 달성 |
| `{dim}_reasoning` | **제외** | 내부 CoT. 순증 정보 없이 차원당 수백 자로 가장 비싸고, 실질 위험은 컨텍스트가 아니라 주의 분산(모델이 이력서 대신 평가문을 읽고 질문을 만드는 현상) |
| `total_feedback` | **제외** | improvements 상위 1~2개의 재언급 |
| `total_score`, `jd_provided` | 포함 | 난도 조정 신호 + `jd_fit` 블록 부재가 오류가 아님을 고지 |

토큰 예산: 주입 블록 최악 ≈ 5,100자(3,200~4,300 입력 토큰) + 이력서·포트폴리오 3,000~9,000자 → Sonnet 4 컨텍스트 여유. **잘라내기 로직 불필요.** `resume-question-max-tokens: 2048`은 출력 상한이며 주입량과 무관하다(출력 최악 ≈ 1,700자 ≈ 1,400 토큰).

user 메시지 템플릿(Bedrock·GPT 동일 5태그):

```java
public static List<Message> createQuestionGenerationMessages(
        String resumeText, String portfolioText, String jobPosition, String jobCareer,
        String evaluationResult) {
    String userText = """
            <resume>
            %s
            </resume>

            <portfolio>
            %s
            </portfolio>

            <target_position>
            %s
            </target_position>

            <job_career>
            %s
            </job_career>

            <evaluation_result>
            %s
            </evaluation_result>
            """.formatted(nullToEmpty(resumeText), nullToEmpty(portfolioText), nullToEmpty(jobPosition),
            nullToEmpty(jobCareer), nullToEmpty(evaluationResult));

    return List.of(Message.builder()
            .role("user")
            .content(List.of(ContentBlock.builder().text(userText).build()))
            .build());
}
```

`ResumeAnalysisEvaluationResultRenderer.render(ResumeAnalysisEvaluation, boolean jdProvided)` 출력 형식(`|` 구분자로 개행 토큰 절약, 차원 순서 = `ResumeAnalysisWeights.dimensions()`):

```
이 결과는 같은 이력서·포트폴리오를 대상으로 방금 수행된 평가다. 점수가 낮은 차원과 gaps(검증 공백)를 질문 표적 선정에 사용한다. strengths는 각 차원의 대표 근거 2개만 발췌한 것이다.
<dimension name="problem_solving" score="62">
strengths: {reason[0]} | {reason[1]}
gaps: {improvements[0]} | {improvements[1]} | {improvements[2]}
</dimension>
<dimension name="project_experience" score="78">
strengths: … | …
gaps: … | …
</dimension>
<dimension name="technical_skills" score="71">
…
</dimension>
<dimension name="soft_skills" score="55">
strengths: (없음)
gaps: … | …
</dimension>
<dimension name="jd_fit" score="64">
…
</dimension>
overall: total_score=68, jd_provided=true
```

렌더 규칙:
- `reason`이 비었으면 `strengths: (없음)`(빈 줄 금지 — 모델이 필드 누락으로 오독).
- JD 없음이면 `<dimension name="jd_fit">` 블록을 렌더하지 않고 `jd_provided=false`.
- `strengths`는 `reason.subList(0, Math.min(2, reason.size()))`. "대표 근거 2개만 발췌"를 첫 줄에 고지한다.
- 불릿 본문의 `|`는 `/`로, `<`는 `(`로 치환(태그 파싱 혼동 방지).

`EVALUATION_GROUNDING_RULE` — `QUESTION_GENERATION_GUIDE` 6번("문서에 없는 기술·경험을 전제한 질문 금지")과 gaps 주입의 충돌을 해소한다:

```
<evaluation_grounding_rule>
<evaluation_result>는 질문의 '표적'을 고르는 데에만 사용한다. 질문 문장 자체는 반드시 이력서/포트폴리오에 실제로 기재된 항목·문장·기술·프로젝트를 지목해 구성한다.
- gaps는 "이력서에 없는 것"을 지적한 문장이다. 그것을 그대로 질문으로 옮기면 문서에 없는 경험을 전제한 질문이 되므로 금지한다. 대신 그 gap이 지적한 원래 서술을 이력서에서 찾아 그 서술을 지목해 캐묻는다. 예: gap이 "응답 지연 개선의 측정 방법이 없다"이면 "왜 측정하지 않았나"가 아니라, 이력서의 해당 개선 항목을 지목해 "그 개선의 효과를 무엇으로 어떻게 확인했는지"를 묻는다.
- strengths로 제시된 주장은 액면 그대로 인정하지 말고, 본인이 직접 한 일인지·어떻게 검증했는지를 확인하는 질문의 소재로 삼는다.
- 점수가 낮은 차원에 질문을 더 배분한다. 단 <diversity_rule>의 4개 카테고리 최소 1개씩 조건은 그대로 지킨다.
- <evaluation_result>의 문장을 그대로 인용하거나 지원자에게 평가 결과를 통보하는 질문("평가에서 지적된 …", "점수가 낮은 …")은 만들지 않는다. 지원자는 이 평가 결과를 질문 형태로 받지 않는다.
- soft_skills의 점수가 중립 기준점(50-59)인 것은 협업 역량이 부족하다는 뜻이 아니라 문서에 근거가 없다는 뜻이다. 이를 근거로 협업 역량을 의심하는 질문을 만들지 않는다.
</evaluation_grounding_rule>
```

`QUESTION_GENERATION_GUIDE`(신규판)는 구 상수 본문을 복사한 뒤 8번 항목만 추가한다: `"8. <evaluation_result>가 제공된 경우 질문 배분의 우선순위 근거로 사용하며, <evaluation_grounding_rule>을 준수한다."`

---

## 5. 툴 스키마

### 5-1. 사양 클래스 (public)

```java
package com.samhap.kokomen.resume.external.dto;

/**
 * 이력서 분석 tool 스키마의 provider 공용 사양. JD 제공 여부에 따라 차원 목록이 두 가지로 갈리며,
 * 런타임 재정규화 대신 두 목록을 명시적으로 선언한다(D5·D6).
 * 지표 키는 ResumeAnalysisDimension.toolKey()가 단일 소스다.
 */
public final class ResumeAnalysisSchema {

    public static final int SCORE_MIN = 0;
    public static final int SCORE_MAX = 100;
    public static final int BULLET_MIN_ITEMS = 2;
    public static final int BULLET_MAX_ITEMS = 6;
    public static final int QUESTION_MIN_ITEMS = 5;
    public static final int QUESTION_MAX_ITEMS = 7;
    public static final int QUESTION_MAX_LENGTH = 300;      // generated_question.content VARCHAR(1000)
    public static final int QUESTION_REASON_MAX_LENGTH = 600; // generated_question.reason VARCHAR(1000)
    public static final int FIELDS_PER_DIMENSION = 4;

    private static final Map<ResumeAnalysisDimension, String> SCORE_DESCRIPTIONS = new EnumMap<>(Map.of(
            PROBLEM_SOLVING, "0-100 점수. score_anchors 기준.",
            PROJECT_EXPERIENCE, "0-100 점수. score_anchors 기준.",
            TECHNICAL_SKILLS, "0-100 점수. score_anchors 기준.",
            SOFT_SKILLS, "0-100 점수. score_anchors 기준. 관찰 근거가 없으면 감점하지 않고 중립 기준점 50-59에서 시작한다.",
            JD_FIT, "0-100 점수. score_anchors 기준. 채용 공고 대조 결과만을 근거로 산출한다."));

    private ResumeAnalysisSchema() {
    }

    public static List<ResumeAnalysisDimension> dimensions(boolean jdProvided) {
        return ResumeAnalysisWeights.of(jdProvided).dimensions();
    }

    public static List<String> dimensionKeys(boolean jdProvided) {
        return dimensions(jdProvided).stream().map(ResumeAnalysisDimension::toolKey).toList();
    }

    public static String scoreDescription(ResumeAnalysisDimension dimension) {
        return SCORE_DESCRIPTIONS.get(dimension);
    }

    public static int requiredFieldCount(boolean jdProvided) {
        return dimensions(jdProvided).size() * FIELDS_PER_DIMENSION + 1;
    }
}
```

차원 목록의 소스가 `ResumeAnalysisWeights`이므로 **가중치 세트와 스키마 필드 집합이 구조적으로 어긋날 수 없다.**

`ResumeAnalysisSchema`는 `public`을 유지한다. 원래 근거(구 `ResumeEvaluationSchema`의 package-private 함정 회피)는 구 클래스 삭제로 소멸했지만, `QUESTION_MIN_ITEMS`/`QUESTION_MAX_ITEMS`를 서비스·컨트롤러 검증에서 쓸 가능성이 있어 축소하지 않는다(축소 후 되돌리면 순환 diff).

### 5-2. Bedrock 평가 스키마 — 빌더 하나, 출력 두 가지 (D6)

```java
public static ToolConfiguration createEvaluationToolConfig(boolean jdProvided) {
    // 중첩 object는 Claude XML 누수를 유발하므로 차원별 4필드를 flat으로 펼친다.
    // required를 느슨하게 풀어 모델이 알아서 빼게 하지 않고, jdProvided에 따라 필드 자체를 넣지 않는다.
    Map<String, Document> properties = new LinkedHashMap<>();
    List<Document> required = new ArrayList<>();
    for (ResumeAnalysisDimension dimension : ResumeAnalysisSchema.dimensions(jdProvided)) {
        putDimensionFields(properties, required, dimension);
    }
    properties.put("total_feedback", Document.fromMap(Map.of(
            "type", Document.fromString("string"),
            "description", Document.fromString("종합 총평. 강점·개선·학습 방향 포함, 한 단락."))));
    required.add(Document.fromString("total_feedback"));

    Document schema = Document.fromMap(Map.of(
            "type", Document.fromString("object"),
            "properties", Document.fromMap(properties),
            "required", Document.fromList(required)));

    return buildToolConfig(ResumeAnalysisToolNames.EVALUATION,
            "이력서/포트폴리오 종합 평가를 제출한다.", schema);
}

private static void putDimensionFields(Map<String, Document> properties, List<Document> required,
                                       ResumeAnalysisDimension dimension) {
    String key = dimension.toolKey();
    properties.put(key + "_reasoning", Document.fromMap(Map.of(
            "type", Document.fromString("string"),
            "description", Document.fromString("이 차원 점수 산정 전 사고 과정. 이 차원에 한정된 근거만 작성."))));
    properties.put(key + "_score", Document.fromMap(Map.of(
            "type", Document.fromString("integer"),
            "minimum", Document.fromNumber(ResumeAnalysisSchema.SCORE_MIN),
            "maximum", Document.fromNumber(ResumeAnalysisSchema.SCORE_MAX),
            "description", Document.fromString(ResumeAnalysisSchema.scoreDescription(dimension)))));
    properties.put(key + "_reason", bulletArraySchema("평가 이유 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
    properties.put(key + "_improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
    required.add(Document.fromString(key + "_reasoning"));
    required.add(Document.fromString(key + "_score"));
    required.add(Document.fromString(key + "_reason"));
    required.add(Document.fromString(key + "_improvements"));
}
```

`bulletArraySchema`·`buildToolConfig`는 `ResumeAnalysisBedrockRequestFactory`의 private 헬퍼이며 **유일본**이다. 구 `ResumeBedrockRequestFactory`가 삭제되므로 "복사"라는 서술은 사실이 아니게 된다. GPT 폴백은 같은 사양을 `Map`으로 렌더한다.

| 경우 | 차원 | 차원 필드 | `total_feedback` | properties | required |
|---|---|---|---|---|---|
| `jdProvided = true` | 5 | 20 | 1 | **21** | **21** |
| `jdProvided = false` | 4 | 16 | 1 | **17** | **17** |

구 평가 스키마와의 비교는 성립하지 않는다 — 구 팩토리·스키마·스키마 테스트 전부 삭제. 21/17의 유일 검증자는 `ResumeAnalysisFlatSchemaTest`다.

**Javadoc 교체 (필수).**

```java
-/**
- * 신규 이력서 분석 Bedrock 요청 팩토리. 구 ResumeBedrockRequestFactory는 0바이트 수정 대상이므로
- * 그 클래스의 private 헬퍼(bulletArraySchema·buildToolConfig)를 재사용하지 않고 같은 형태로 복사했다
- * (가시성 확대는 D2 위반).
- */
+/**
+ * 이력서 분석 Bedrock 요청 팩토리. 평가 tool 스키마는 jdProvided에 따라 차원 4개/5개로 갈리며,
+ * 중첩 object 없이 flat으로만 구성한다(중첩은 Claude의 XML 누수를 유발한다).
+ * 스키마 경계값의 단일 소스는 {@link ResumeAnalysisSchema}이고, GPT 쪽 동일 사양은
+ * {@link ResumeAnalysisEvaluationGptRequest} / {@link ResumeAnalysisQuestionGptRequest}가 렌더한다.
+ */
```

| 파일 | 현재 | 개정 |
|---|---|---|
| `ResumeAnalysisEvaluationGptRequest` | `구 ResumeGptRequest는 0바이트 수정 대상이므로 별 클래스로 둔다.` | 문장 삭제 |
| `ResumeAnalysisEvaluationGptClient` | `…한 겹 벗긴다(구 플로우와 동일 처리).` | 괄호 절 삭제 |
| `ResumeAnalysisGptTimeouts` | `BaseGptClient를 고치면 동결된 구 플로우의 동작이 바뀌므로(D2)` | `BaseGptClient는 면접 진행 GPT 클라이언트(InterviewProceedGptClient)와 공유되므로 그쪽 타임아웃까지 바꾸지 않도록 신규 클라이언트 생성자에서만 적용한다.` |

**해소되는 finding.** 코드 복제 3건(`bulletArraySchema`/`buildToolConfig`, `nullToEmpty`, `SCORE_MIN/MAX`·`BULLET_MIN/MAX_ITEMS`)이 원본 삭제로 **유일본화되어 자동 해소**된다. **코드는 0바이트 수정이고 Javadoc만 고친다.** 패키지 충돌도 없다(구 `ResumeEvaluationSchema`는 package-private).

### 5-3. 질문 스키마 — `maxLength`로 컬럼 한도를 스키마에 반영

구 형상(`questions: array<object{question, reason}>`, minItems 5 / maxItems 7)을 유지하되 **문자열 상한을 명시**한다. `generated_question.content`/`reason`이 VARCHAR(1000)이고 신규 질문 콜은 `<evaluation_result>`의 gaps를 근거로 삼아 `reason`이 길어질 유인이 구조적으로 있다. 상한이 없으면 스키마·minItems를 모두 만족한 응답이 W8에서 `Data too long`으로 전체 롤백되고, 같은 데이터를 다시 넣는 재시도는 100% 재실패한다.

```java
properties.put("question", Document.fromMap(Map.of(
        "type", Document.fromString("string"),
        "maxLength", Document.fromNumber(ResumeAnalysisSchema.QUESTION_MAX_LENGTH),
        "description", Document.fromString("질문 내용. 300자 이내."))));
properties.put("reason", Document.fromMap(Map.of(
        "type", Document.fromString("string"),
        "maxLength", Document.fromNumber(ResumeAnalysisSchema.QUESTION_REASON_MAX_LENGTH),
        "description", Document.fromString("질문 선정 이유. 600자 이내."))));
```

스키마를 신뢰하지 않고 **영속화 직전 방어적 절단**을 둔다(스키마 위반으로 사용자 결과를 버리는 것보다 3자 생략이 낫다):

```java
public static GeneratedQuestion forAnalysis(ResumeAnalysis analysis, String content, String reason,
                                            Integer questionOrder) {
    return new GeneratedQuestion(analysis,
            StringUtils.abbreviate(content, CONTENT_MAX_LENGTH),   // 1000
            StringUtils.abbreviate(reason, REASON_MAX_LENGTH),     // 1000
            questionOrder);
}
```

`minItems`/`maxItems`는 매직넘버 대신 `ResumeAnalysisSchema.QUESTION_MIN_ITEMS`/`QUESTION_MAX_ITEMS`를 Bedrock·GPT 양쪽에서 참조해 중복을 제거한다.

### 5-4. 도구 이름

```java
public final class ResumeAnalysisToolNames {

    public static final String EVALUATION = "submit_resume_analysis_evaluation";
    public static final String QUESTION_GENERATION = "submit_resume_analysis_questions";

    private ResumeAnalysisToolNames() {
    }}
```

`ResumeToolNames`는 삭제되므로 이름 충돌 자체가 성립하지 않는다. 두 상수 값 유지 근거는 남는다: `jdProvided`에 따라 **같은 이름으로 두 가지 스키마**를 보내므로 파싱 실패 로그의 `toolName=`만으로 구분되지 않는다. 두 상수는 서로 달라야 하며(`EVALUATION` ≠ `QUESTION_GENERATION`), 그것이 §8-3의 잔존 단정이다.

### 5-5. 파싱 DTO

```java
public record ResumeAnalysisEvaluationFlatResponse(
        Integer problemSolvingScore, List<String> problemSolvingReason, List<String> problemSolvingImprovements,
        Integer projectExperienceScore, List<String> projectExperienceReason,
        List<String> projectExperienceImprovements,
        Integer technicalSkillsScore, List<String> technicalSkillsReason, List<String> technicalSkillsImprovements,
        Integer softSkillsScore, List<String> softSkillsReason, List<String> softSkillsImprovements,
        Integer jdFitScore, List<String> jdFitReason, List<String> jdFitImprovements,
        String totalFeedback
) {
    /**
     * jdProvided를 인자로 받는 이유: 응답 JSON만으로는 "jd_fit이 없음"과 "jd_fit이 누락됨"을
     * 구분할 수 없으므로 요청 측 사실을 전달해야 한다.
     */
    public ResumeAnalysisEvaluation toEvaluation(boolean jdProvided) {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                new DimensionScore(problemSolvingScore, problemSolvingReason, problemSolvingImprovements),
                new DimensionScore(projectExperienceScore, projectExperienceReason, projectExperienceImprovements),
                new DimensionScore(technicalSkillsScore, technicalSkillsReason, technicalSkillsImprovements),
                new DimensionScore(softSkillsScore, softSkillsReason, softSkillsImprovements),
                jdProvided ? new DimensionScore(jdFitScore, jdFitReason, jdFitImprovements) : null,
                null, totalFeedback);
        return evaluation.withTotalScore(
                ResumeAnalysisWeights.of(jdProvided).calculateTotalScore(evaluation));
    }
}
```

`{dim}_reasoning` 5필드는 **선언하지 않는다**(`FAIL_ON_UNKNOWN_PROPERTIES=false` 전제로 무시). `jdProvided = false`인데 모델이 `jd_fit_*`을 채워 보내면 그 값들은 DTO에 매핑되지만 `toEvaluation(false)`가 `jdFit`을 null로 버리므로 4지표 가중치가 적용된다. 반대로 `jdProvided = true`인데 `jd_fit_score`가 null이면 `DimensionScore` 생성자 검증에서 즉시 실패한다.

---

## 6. 비동기 파이프라인과 상태 전이

### 6-1. 동기 구간 (톰캣 요청 스레드)

기존 두 플로우와의 결정적 차이: **PDF 검증·텍스트 추출·(회원) 파일 영속화를 요청 스레드에서 끝낸 뒤에 행을 만든다.** `PENDING` 행이 존재하는 순간부터 남은 실패 원인은 LLM/DB뿐이고, 추출 실패는 폴링 왕복 없이 즉시 400으로 도달한다.

| # | 단계 | 트랜잭션 |
|---|---|---|
| S1 | 컨트롤러에서 `memberAuth.isAuthenticated()`로 회원/게스트 분기 | 없음 |
| S2 | (게스트) 시간당 시도 카운터 검사 | 없음 |
| S3 | `pdfValidator.validate(resume)` / `validate(portfolio)` (non-empty일 때만) | 없음 |
| S4 | (회원) 진행 중 중복 제출 검사 `existsByMemberIdAndStateInAndCreatedAtAfter(memberId, [PENDING, EVALUATION_COMPLETED], now-15분)` | readOnly |
| S5 | (회원, 과금 대상) `tokenFacadeService.validateEnoughTokens(memberId, 5)` — **확인만, 차감 없음** | REQUIRES_NEW(readOnly) |
| S6 | 텍스트 추출 (`EXTRACTION_SEMAPHORE` 하에서 순차, 병렬화 금지) | 없음 |
| S7 | (회원 + 파일 제출) `pdfUploadService.saveResume/savePortfolio` → S3 + `member_resume`/`member_portfolio` | 자체 `@Transactional` |
| S8 | (게스트) **365일 게스트 락 획득** `acquireLockWithValue` | 없음 |
| S9 | `resumeAnalysisService.saveAnalysis(...)` — `PENDING` 행 + `resume_analysis_source_text` INSERT | **`@Transactional(REQUIRES_NEW)`** → 반환 시점에 커밋 완료 |
| S10 | `resumeAnalysisExecutor.execute(() -> asyncService.run(command))` | 없음 |
| S11 | 202 반환 | 없음 |

**파사드 메서드에는 `@Transactional`을 붙이지 않는다.** S9만 `REQUIRES_NEW`로 커밋되므로 S10 시점에 행이 반드시 조회 가능하다(기존 질문 플로우는 파사드 트랜잭션 안에서 save한 뒤 커밋 전에 비동기를 제출해 워커의 `findById`가 실패할 수 있었다). 나중에 누가 파사드에 `@Transactional`을 붙여도 S9가 `REQUIRES_NEW`이므로 커밋 선행은 유지된다.

**게스트 락을 S8(추출 이후)에 잡는 이유** — D12의 4요건(`acquireLockWithValue`, 365일 TTL, 실패 시 `BadRequestException`, 예외 시 `releaseLockSafely`)은 전부 지키면서, 락 획득 → 커밋 사이 구간을 단일 INSERT(수 ms)로 줄인다. 락을 파사드 진입 직후(추출 전)에 잡으면 10~60초짜리 추출 구간에서 프로세스가 급사할 때 `catch`도 실행되지 않고 `guest_lock_value`도 아직 없어 **해당 IP가 365일간 영구 차단되고 추적 수단이 0**이 된다. 획득 시 `log.info("게스트 이력서 분석 락 획득 - ip: {}, lockValue: {}", ...)`를 남겨 잔여 위험(수 ms 창)에 대한 수동 `DEL` 런북을 성립시킨다.

```java
// ResumeAnalysisFacadeService — 회원 경로
@DistributedLock(prefix = "resume-analysis", key = "#memberId")
public ResumeAnalysisSubmitResponse submitMemberAnalysis(Long memberId, ResumeAnalysisSubmitRequest request) {
    pdfValidator.validate(request.resume());                                        // S3
    validateNoInProgressAnalysis(memberId);                                         // S4
    boolean billingRequired = !isFirstUse(memberId);                                // §7-3
    if (billingRequired) {
        tokenFacadeService.validateEnoughTokens(memberId, RESUME_ANALYSIS_TOKEN_COST);   // S5
    }
    ExtractedContents contents = extractContents(memberId, request);                // S6 (세마포어)
    MaterialRefs refs = persistMaterialsIfNeeded(memberId, request, contents);       // S7
    ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(                       // S9 (REQUIRES_NEW 커밋)
            memberId, GuestInfo.none(), refs, contents, request.toJobInput(), billingRequired);
    submitPipeline(saved, billingRequired ? memberId : null, contents, request);     // S10
    return ResumeAnalysisSubmitResponse.ofMember(saved.getId());
}

// 게스트 경로 — @DistributedLock 없음. setIfAbsent 1회성 락이 동시성 제어까지 겸한다.
// (DistributedLockAspect.resolveLockKey는 SpEL 결과가 null이면 BadRequestException을 던지므로
//  memberId == null인 게스트를 같은 메서드에 태울 수 없다. 선례: startResumeBasedInterview vs startGuestInterview)
public ResumeAnalysisSubmitResponse submitGuestAnalysis(ResumeAnalysisSubmitRequest request, ClientIp clientIp) {
    validateGuestAttemptQuota(clientIp);                                             // S2
    if (request.hasSavedMaterialId()) {
        throw new BadRequestException("비회원은 저장된 이력서를 사용할 수 없습니다.");
    }
    pdfValidator.validate(request.resume());                                         // S3
    ExtractedContents contents = extractContents(null, request);                      // S6

    String lockKey = GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX + clientIp.address();
    String lockValue = UUID.randomUUID().toString();
    if (!redisService.acquireLockWithValue(lockKey, lockValue, GUEST_RESUME_ANALYSIS_LOCK_TTL)) {   // S8
        throw new BadRequestException("비회원 이력서 분석은 1회만 가능합니다.");
    }
    log.info("게스트 이력서 분석 락 획득 - lockKey: {}, lockValue: {}", lockKey, lockValue);
    try {
        String guestToken = UUID.randomUUID().toString();
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(                    // S9
                null, new GuestInfo(guestToken, clientIp, lockValue), MaterialRefs.empty(),
                contents, request.toJobInput(), false);
        submitPipeline(saved, null, contents, request);                               // 무과금
        return ResumeAnalysisSubmitResponse.ofGuest(saved.getId(), guestToken);
    } catch (RuntimeException e) {
        redisService.releaseLockSafely(lockKey, lockValue);
        throw e;
    }
}

private void submitPipeline(ResumeAnalysis analysis, Long billingMemberId, ExtractedContents contents,
                            ResumeAnalysisSubmitRequest request) {
    ResumeAnalysisCommand command = ResumeAnalysisCommand.of(analysis, billingMemberId, contents, request);
    try {
        resumeAnalysisExecutor.execute(() -> resumeAnalysisAsyncService.run(command));
    } catch (TaskRejectedException e) {
        resumeAnalysisStateService.failEvaluation(analysis.getId(), ResumeAnalysisFailureReason.CAPACITY);
        throw new ServiceUnavailableException("이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.");
    }
}
```

`failEvaluation`은 게스트 락 해제를 포함하며(§7-5), 파사드가 무트랜잭션이므로 롤백되지 않는다.

```java
public record ResumeAnalysisCommand(
        Long analysisId, Long billingMemberId, boolean jdProvided,
        String resumeText, String portfolioText,
        String jobPosition, String jobDescription, String jobCareer) {

    public boolean isBillable() { return billingMemberId != null; }
}
```

`billingMemberId == null` ⇒ 무과금(게스트 또는 첫 사용 무료). 판정은 S5에서 1회만 하고 결과를 커맨드에 고정해 워커로 전달한다(DB를 다시 읽지 않으므로 그 사이 claim/제출이 끼어들어도 과금이 뒤집히지 않는다). `jdProvided`도 커맨드에 실어 워커가 문자열로 재계산하지 않게 한다.

### 6-2. 텍스트 추출 보호 (웹 티어 고갈 차단)

실측: `server.tomcat.threads.max = 30`, `spring.servlet.multipart.max-file-size = 10MB`, `PdfTextExtractor`는 ≤5MB를 `getBytes()`로 전체 힙 로드하고 `PDFTextStripper.setSortByPosition(true)`로 페이지당 정렬한다. 인증 없는 게스트 엔드포인트에서 동기 추출을 그대로 열면 10MB·수천 페이지 PDF 병렬 30건으로 **Tomcat 스레드 전부가 PDFBox에 묶여 면접·로그인·결제까지 응답 불가**가 된다(executor 큐 40은 도달하기 전에 웹 티어가 죽는다). 게스트 락은 실패 시 해제되므로 "일부러 실패하는 PDF"를 반복하면 유일한 제한도 무력화된다.

세 겹으로 막는다.

```java
// 1) 시도 제한(성공 제한과 분리) — 락은 1회 '성공' 제한, 카운터는 '시도' 제한
private void validateGuestAttemptQuota(ClientIp clientIp) {
    String attemptKey = GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX + clientIp.address();
    Long attempts = redisService.incrementKey(attemptKey);
    redisService.expireKey(attemptKey, Duration.ofHours(1));
    if (attempts > GUEST_MAX_ATTEMPTS_PER_HOUR) {
        throw new BadRequestException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }
}

// 2) 동시 추출 수를 Tomcat 스레드 수보다 훨씬 낮게 묶는다
private static final Semaphore EXTRACTION_SEMAPHORE = new Semaphore(6);
private static final Duration EXTRACTION_ACQUIRE_TIMEOUT = Duration.ofSeconds(2);

private ExtractedContents extractContents(Long memberId, ResumeAnalysisSubmitRequest request) {
    boolean acquired;
    try {
        acquired = EXTRACTION_SEMAPHORE.tryAcquire(
                EXTRACTION_ACQUIRE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ServiceUnavailableException("이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.");
    }
    if (!acquired) {
        throw new ServiceUnavailableException("이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.");
    }
    try {
        return doExtract(memberId, request);   // 순차. CompletableFuture 병렬화 금지
    } finally {
        EXTRACTION_SEMAPHORE.release();
    }
}
```

3) `PdfValidator`에 **페이지 수 상한**(`MAX_PAGE_COUNT`)을 추가한다 — 파일 크기만으로는 파싱 비용을 제한할 수 없다. 기존 `PdfValidator`에 상수·검증을 추가하면 구 평가 플로우의 동작이 바뀌므로(50MB→10MB 실효 상한과 달리 페이지 검증은 새 거부 조건이다), **신규 전용 `ResumeAnalysisPdfPolicy.validatePageCount(MultipartFile)`를 별도 클래스로 두고 신규 경로에서만 호출**한다. 또는 `PDFTextStripper.setEndPage(N)`로 상한 페이지까지만 추출한다.

`MultipartFile`을 워커 스레드로 넘기지 않는다(요청 종료 후 임시 파일 유효성 문제). `byte[]`를 워커 큐에 담지도 않는다(최대 10MB × 큐 길이만큼 힙 점유). 워커에는 **추출된 텍스트 String만** 넘긴다.

### 6-2-1. 하이퍼링크 URL 추출 (신규 경로 전용)

`PDFTextStripper`는 **링크 annotation을 추출하지 않는다.** 현재 `PdfTextExtractor`는 `stripper.getText(document).trim()`만 반환하므로, "GitHub" 같은 글자에 URL이 annotation으로만 걸린 이력서(Notion·Figma 산출물에서 흔하다)에서는 URL이 텍스트에 존재하지 않는다.

그런데 §4-4 `technical_skills` 관찰항목에 다음이 있다.

> 포트폴리오, GitHub, GitLab, 개인 기술 블로그, 논문 등 기술력을 교차 검증할 수 있는 링크·산출물이 포함됐는가.

즉 이 항목은 **구조적으로 채점 불가**다. 모델이 볼 수 없는 것을 근거로 요구하면 `INDEPENDENCE_PRINCIPLE`("근거 미확인 주장은 사실 불인정")에 따라 항상 미기재로 처리된다.

**해소 방식: `PdfTextExtractor`에 가산 전용 메서드를 추가한다.** 기존 `extractText(MultipartFile)` / `extractText(byte[])`와 공유 private `extractText(PDDocument)`는 **0바이트 수정한다.**

```java
// 기존 — 무변경 (신규 파사드의 저장-자료 텍스트 추출 경로인 ResumeContentService가 계속 사용)
public String extractText(MultipartFile file)
public String extractText(byte[] pdfData)
private String extractText(PDDocument document)          // stripper.getText().trim()

// 신규 — 신규 분석 경로(제출 시 업로드된 파일)만 호출
public String extractTextWithLinks(MultipartFile file)
public String extractTextWithLinks(byte[] pdfData)
private String extractTextWithLinks(PDDocument document) // body + <links> 블록
private String extractLinks(PDDocument document)         // PDAnnotationLink → PDActionURI
```

`<links>` 블록 형식(중복 제거, 삽입 순서 유지):

```
<links>
https://github.com/example
https://example.tistory.com
</links>
```

**공유 private `extractText(PDDocument)`를 수정해서는 안 된다.** 그 메서드는 `extractText(MultipartFile)`과 `extractText(byte[])` 양쪽에서 호출되고, 두 메서드는 존치되는 `ResumeContentService`(신규 파사드의 저장-자료 텍스트 추출 경로)가 계속 쓴다. 본문 뒤에 `<links>`를 덧붙이면 **신규 플로우의 LLM 입력이 하이퍼링크 유무로 두 갈래가 된다**(제출 시 업로드 파일은 링크 포함, 저장된 자료 재사용은 링크 미포함). 신규 경로(제출 시 업로드 파일)만 새 메서드를 호출한다.

`page.getAnnotations()`가 던지는 `IOException`은 페이지 단위로 삼켜 `log.warn` 후 본문만 사용한다(링크 부재는 채점 가능한 상태이고, 링크 파싱 실패로 분석 전체를 버릴 이유가 없다).

`BaseTest`는 현재 `extractText(MultipartFile)`만 스텁하고 있고 신규 경로는 `extractTextWithLinks`를 호출하므로, §8-9의 목 추가 목록에 이 메서드 스텁을 포함한다.

### 6-3. 비동기 구간 (`resumeAnalysisExecutor`, 단일 hop)

기존 평가 플로우의 3-hop 재제출(hop 간 예외 전파 단절 + hop2/3 rejection 시 영구 PENDING)을 **단일 태스크 + try/catch/finally 하나**로 대체한다. GPT 폴백도 재제출이 아니라 같은 스레드 내 순차 호출이다.

```java
public void run(ResumeAnalysisCommand command) {          // executor에 제출되는 단일 태스크
    Map<String, String> capturedMdc = MDC.getCopyOfContextMap();   // 명명 executor에는 MdcDecorator가 없다
    try {
        if (capturedMdc != null) {
            MDC.setContextMap(capturedMdc);
        }
        ResumeAnalysisEvaluation evaluation = runEvaluationHop(command);
        if (evaluation != null) {
            runQuestionHop(command, evaluation);
        }
    } finally {
        MDC.clear();
    }
}

public ResumeAnalysisEvaluation runEvaluationHop(ResumeAnalysisCommand command);   // 실패 시 null
public void runQuestionHop(ResumeAnalysisCommand command, ResumeAnalysisEvaluation evaluation);
public ResumeAnalysisCommand readCommand(Long analysisId);   // source_text + 부모 행에서 복원(재시도·테스트)
```

두 hop을 **public 메서드로 노출**하는 것은 테스트 요구사항이다 — 레포에 `awaitility` 의존성이 없고 executor를 동기로 교체하는 장치도 없어, hop을 직접 호출할 수 없으면 2콜 순차 종단 테스트가 `Thread.sleep` 없이는 불가능하다.

| # | 단계 | 트랜잭션/커밋 |
|---|---|---|
| W1 | **평가 콜** Bedrock `temperature=0.2`, `maxTokens=10000`, 툴 스키마는 `command.jdProvided()`로 생성 | 없음 |
| W2 | 실패 시 GPT 폴백 1회(같은 스레드) | 없음 |
| W3 | `findByIdForUpdate(analysisId)` → `state != PENDING`이면 **결과 폐기하고 종료** | W4와 동일 `@Transactional(REQUIRES_NEW)` |
| W4 | `completeEvaluation(evaluation)` — 15컬럼 + 총점·총평 + `evaluation_completed_at` + `question_started_at = now()` | **커밋 = D9 1차 공개 지점** |
| W5 | `chargeTokensIfNeeded(analysisId, billingMemberId)` — CAS 선점 후 `useTokens` | 각각 REQUIRES_NEW |
| W6 | **질문 콜** Bedrock `temperature=0.7`, `maxTokens=2048`, user 메시지에 `<evaluation_result>`(W1 결과 렌더) 주입 | 없음 |
| W7 | 실패 시 GPT 폴백 1회. W1이 Bedrock 예외였다면 Bedrock 건너뛰고 GPT 직행 | 없음 |
| W8 | `findByIdForUpdate` → `state == EVALUATION_COMPLETED` 확인 → `generated_question` 5~7행 INSERT + `completeQuestions()` | REQUIRES_NEW, **커밋 = D9 2차 공개** |

W1→W6이 같은 스레드에서 순차 실행되므로 D8의 "병렬/단일콜 아님"이 구조적으로 보장된다. 평가 결과는 메모리(`ResumeAnalysisEvaluation`)로 W6에 직접 전달하므로 W4 커밋 결과를 다시 읽지 않는다.

**`question_started_at`을 W4에서 반드시 세팅한다.** sweep이 `created_at`을 기준으로 질문 단계를 판정하면 (a) 평가에 8분 걸린 정상 요청이 질문 콜 도중 `QUESTION_FAILED`로 찍히고, (b) 2시간 뒤의 사용자 재시도가 sweep에 즉시 잡혀 **재시도가 구조적으로 항상 실패**한다(재시도 워커가 W8에서 상태 가드에 걸려 정상 생성한 질문 5개를 폐기하고, `question_retry_count`만 소모되어 2회 만에 영구 고착). 재시도 진입(`restoreForQuestionRetry`)에서도 같은 컬럼을 갱신한다.

### 6-4. Executor

| 후보 | 판정 | 근거 |
|---|---|---|
| `gptCallbackExecutor` | 배제 | 면접 proceed 콜백과 공유. 신규 태스크는 LLM 2콜로 스레드를 40~90초 점유해 면접 응답 지연에 직결된다 |
| `resumeEvaluationExecutor`(구 평가 플로우의 executor) | 배제 | 구 평가 플로우가 통째로 삭제되므로 이 빈 자체가 사라진다(`AsyncConfig`의 `@Bean("resumeEvaluationExecutor")` 삭제, §1-2). 설령 재사용을 검토했더라도 구 게스트 경로가 같은 풀에 `supplyAsync` 2건을 제출하고 `join()`으로 셀프 블로킹하는 패턴이라 큐가 차면 데드락에 근접했고, `awaitTerminationSeconds=30`은 2콜 순차 태스크를 배포 중 잘라먹었을 것이다 |
| **신규 `resumeAnalysisExecutor`** | 채택 | 격리 + rejection을 요청 스레드에서 명시 처리하기 위해 별도 빈이 필요 |

```java
@Bean
public ThreadPoolTaskExecutor resumeAnalysisExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(60);
    executor.setMaxPoolSize(60);
    executor.setQueueCapacity(40);
    executor.setThreadNamePrefix("Async-Resume-Analysis-");
    // 셧다운 시 큐를 버린다. 큐에 있던 행은 sweep이 종단 처리하며,
    // 억지로 실행하면 "중간에 죽는 태스크"만 늘어난다.
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    executor.getThreadPoolExecutor().prestartAllCoreThreads();
    return executor;
}
```

| 파라미터 | 값 | 근거 |
|---|---|---|
| core=max | 60 | 태스크는 전부 네트워크 대기(Bedrock socketTimeout 60s) → 스레드 수 = 목표 동시 진행 건수. 회원 1건 제한 + 게스트 IP 1회 제한이 있어 여유 |
| queue | 40 | 평균 점유 ≈ 60초 ⇒ 최대 대기 ≈ 40초. 그 이상 대기시키면 사용자는 이미 폴링을 포기하고 PENDING 행만 쌓인다. 큐 1000은 "실패를 지연된 PENDING으로 바꾸는 장치"일 뿐이다 |
| rejection | 기본 `AbortPolicy` | `TaskRejectedException`을 요청 스레드에서 받아 즉시 종단 + 503으로 바꾼다. `CallerRunsPolicy`는 톰캣 스레드를 60초 잡으므로 금지 |
| awaitTermination | 60s | `spring.lifecycle.timeout-per-shutdown-phase: 60s`와 정렬. 컨테이너 grace period는 §10 |

`@Async`는 쓰지 않는다(레포 선례 일치, `getAsyncExecutor()`가 호출마다 새 풀을 만드는 함정 회피).

### 6-5. GPT 폴백

두 콜 모두에 폴백 1회씩, 콜 단위 독립. 신규 GPT 클라이언트의 `RestClient`에는 **명시 타임아웃(connect 3s / read 90s)** 을 설정한다(설정하지 않으면 워커가 무한 대기해 sweep이 먼저 실패를 찍는다).

```java
private ResumeAnalysisEvaluation evaluateWithFallback(ResumeAnalysisCommand command) {
    try {
        return evaluationBedrockClient.evaluate(command);
    } catch (Exception e) {
        log.error("Bedrock 이력서 분석 평가 실패, GPT 폴백 - analysisId: {}, exception: {}",
                command.analysisId(), e.getClass().getName(), e);
        bedrockUnhealthy = true;                    // 태스크 로컬 플래그
        return evaluationGptClient.evaluate(command);
    }
}

private ResumeAnalysisQuestionResult generateQuestionsWithFallback(ResumeAnalysisQuestionCallCommand command) {
    if (bedrockUnhealthy) {
        return questionGptClient.generateQuestions(command);   // 60s socketTimeout 중복 회피
    }
    try {
        return questionBedrockClient.generateQuestions(command);
    } catch (Exception e) {
        log.error("Bedrock 이력서 분석 질문 생성 실패, GPT 폴백 - analysisId: {}, exception: {}",
                command.analysisId(), e.getClass().getName(), e);
        return questionGptClient.generateQuestions(command);
    }
}
```

| # | 평가 콜 | 질문 콜 | 최종 state | 토큰 |
|---|---|---|---|---|
| 1 | Bedrock 성공 | Bedrock 성공 | `COMPLETED` | 차감 |
| 2 | Bedrock 성공 | Bedrock 실패 → GPT 성공 | `COMPLETED` | 차감 |
| 3 | Bedrock 실패 → GPT 성공 | **Bedrock 건너뛰고 GPT** 성공 | `COMPLETED` | 차감 |
| 4 | Bedrock 실패 → GPT 성공 | GPT 실패 | `QUESTION_FAILED` | 차감 유지 |
| 5 | Bedrock 실패 → GPT 실패 | **미실행** | `EVALUATION_FAILED` | 미차감 |

catch 범위는 기존과 동일하게 `Exception`으로 넓게 잡되 **`e.getClass().getName()`을 반드시 로그에 남긴다.** 신규 5지표 스키마 오타 같은 프로그래밍 오류까지 GPT로 폴백해 같은 이유로 재실패하고 로그 2줄만 남는 문제에 대한 최소 관측 대책이다. `failure_reason`을 `EVALUATION_LLM` / `OUTPUT_TRUNCATED`로 구분 기록해 잘림과 스키마 오류를 사후 분리한다.

`stopReason=MAX_TOKENS` → `extractToolUse`가 `ExternalApiException("Bedrock 응답이 tool_use가 아닙니다.")`를 던지므로 `OUTPUT_TRUNCATED`로 분류하고 GPT 폴백을 시도한다(GPT는 `max_tokens`를 전송하지 않아 성공 가능).

---

## 7. 실패·과금·게스트 정책

### 7-1. 실패 정책 표

| 사례 | 감지 | 최종 state | `failure_reason` | 사용자 | 자동 재시도 | 수동 | 토큰 |
|---|---|---|---|---|---|---|---|
| 게스트 시간당 시도 초과 | S2 | 행 없음 | — | 400 | 없음 | 1시간 후 | 미차감 |
| PDF 검증 실패 | S3 | 행 없음 | — | 400 | 없음 | 재제출 | 미차감 |
| 진행 중 중복 제출 | S4 | 행 없음 | — | 400 | — | 15분 후 또는 완료 후 | 미차감 |
| 토큰 부족 | S5 | 행 없음 | — | 400 | — | 충전 후 | 미차감 |
| 추출 동시 한도 초과 | S6 | 행 없음 | — | 503 | 없음 | 재제출 | 미차감 |
| **텍스트 추출 실패** | S6 | 행 없음 | — | 400 `이력서 PDF에서 텍스트를 추출할 수 없습니다.` | 없음 | 재제출 | 미차감. **게스트 락은 아직 잡지 않았으므로 소진 0** |
| S3 업로드/`member_resume` 저장 실패 | S7 | 행 없음 | — | 500 | 없음 | 재제출 | 미차감 |
| 게스트 2회째 제출 | S8 | 행 없음 | — | 400 | — | 불가 | 미차감 |
| Executor rejection | S10 | `EVALUATION_FAILED` | `CAPACITY` | 503 + 폴링 일관 | 없음 | 재제출 | 미차감. **게스트 락 해제** |
| **평가 실패**(Bedrock+GPT) | W1/W2 | `EVALUATION_FAILED` | `EVALUATION_LLM` | 200 `state`, `evaluation` 키 없음 | GPT 폴백 1회가 전부 | 재제출(신규 행) | **미차감** |
| 출력 잘림 | W1 | `EVALUATION_FAILED` | `OUTPUT_TRUNCATED` | 동일 | GPT 폴백 1회 | 재제출 | 미차감 |
| 평가 저장 실패 | W3/W4 | `EVALUATION_FAILED` | `PERSISTENCE` | 동일 | 일시적 예외만 1회 재시도 | 재제출 | 미차감 |
| 토큰 차감 실패 | W5 | 진행 계속 | — (`token_charge_failed = true`) | 결과 정상 노출 | 백오프 2회 | — | 미차감 + 감사 흔적 |
| **질문만 실패** | W6/W7 | `QUESTION_FAILED` | `QUESTION_LLM` | 평가 전체 + `question_retryable=true` | 없음 | **전용 재시도(무과금, 최대 2회)** | 차감 유지 |
| 질문 저장 실패 | W8 | `QUESTION_FAILED` | `PERSISTENCE` | 동일 | 일시적 예외만 1회 | 재시도 | 차감 유지 |
| 워커 사망(PENDING 잔류) | sweep | `EVALUATION_FAILED` | `STALE_SWEEP` | 200 실패 | 없음 | 재제출 | 미차감. **게스트 락 해제** |
| 워커 사망(EVALUATION_COMPLETED 잔류) | sweep | `QUESTION_FAILED` | `STALE_SWEEP` | 평가 + 재시도 가능 | 없음 | 재시도 | 차감(sweep이 회수 과금) |

핵심 원칙 3개:
1. **과금 경계 = 평가 결과 커밋.** 그 이후의 실패는 환불하지 않고, 그 전의 실패는 애초에 차감하지 않는다. 구 플로우의 "환불 경로 없음" 결함이 재현될 수 없다.
2. **모든 실패는 종단 상태를 남긴다.** 요청 스레드 실패는 행을 만들지 않고 즉시 4xx/5xx, 워커 실패는 조건부 전이로 종단, 그 둘이 놓친 것은 sweep이 10분 내 종단 처리.
3. `PERSISTENCE` 재시도는 **일시적 예외에만** 적용한다(`CannotAcquireLockException`, `DeadlockLoserDataAccessException`). `DataIntegrityViolationException`은 즉시 종단 — 같은 데이터를 다시 넣는 재시도는 결정적으로 재실패한다.

### 7-2. 토큰 소비 — 후차감

| 시점 | 동작 |
|---|---|
| S5 | `validateEnoughTokens(memberId, 5)` — 부족하면 400, 행 생성 없음. 차감 안 함 |
| W5 | CAS 선점 후 `useTokens(billingMemberId, 5)` |

후차감 근거: (a) `TokenFacadeService.refundTokens`는 PG 결제 취소 전용이고 `TokenService.refundPaidTokenCount`는 이름과 달리 PAID 잔량을 차감한다 — 소비 취소용 메서드가 없어 선차감을 택하면 신규 메서드 + `tokenPurchaseService.usePaidTokens` 되돌리기까지 필요하다. (b) `startResumeBasedInterview`가 `validateEnoughTokens`만 하고 실제 차감은 이후 단계에서 하는 선례. (c) 실패의 대부분(추출·rejection·평가 LLM)이 W5 이전이다. (d) 남용은 S5 검증 + 진행 중 1건 제한이 막는다.

```java
private void chargeTokensIfNeeded(Long analysisId, Long billingMemberId) {
    if (billingMemberId == null) {
        return;                                              // 게스트(D10) 또는 첫 사용 무료
    }
    if (resumeAnalysisRepository.markTokenCharged(analysisId, RESUME_ANALYSIS_TOKEN_COST) != 1) {
        return;                                              // 이미 과금됨(0행) → 이중 차감 방지
    }
    for (int attempt = 1; attempt <= TOKEN_CHARGE_MAX_ATTEMPTS; attempt++) {   // 3회
        try {
            tokenFacadeService.useTokens(billingMemberId, RESUME_ANALYSIS_TOKEN_COST);
            return;
        } catch (RuntimeException e) {
            if (attempt == TOKEN_CHARGE_MAX_ATTEMPTS) {
                resumeAnalysisRepository.markTokenChargeFailed(analysisId);
                log.error("이력서 분석 토큰 차감 실패, 결과는 제공 - analysisId: {}, memberId: {}",
                        analysisId, billingMemberId, e);
                return;
            }
            sleepQuietly(TOKEN_CHARGE_BACKOFF);              // 200ms
        }
    }
}
```

`useTokens`는 `@DistributedLock(prefix = "token", key = "#memberId")`이고 `DistributedLockAspect`는 3초 내 미획득 시 `BadRequestException`을 던진다. 같은 회원이 면접을 진행하는 동안 분석을 돌리면 `lock:token:{memberId}`가 경쟁하므로, **한 번의 예외를 정상 흐름으로 흡수하면 "면접 중이면 분석이 무료"가 되고 Redis 지연 시간대에는 체계적 누락이 된다.** 백오프 재시도 후에만 포기하고, 포기했을 때 `token_charge_failed = true`로 사후 정산 가능한 흔적을 남긴다(`charged_token_count`는 0으로 되돌려 재과금 여지를 열어둔다).

이 메서드는 **평가 공개 이후의 모든 종단 전이 지점에서 반복 호출**된다(W5, `failQuestions`, `completeQuestions`, sweep의 `EVALUATION_COMPLETED → QUESTION_FAILED`). CAS로 멱등이므로 중복 과금이 없고, W4 커밋 직후 프로세스가 죽어 W5를 못 돌린 행도 sweep이 종단 처리할 때 회수 과금된다(배포 롤링이 잦을 때 평가+질문이 전부 무료로 끝나는 경로를 막는다). 회수 과금 대상 판정은 `billing_required = true && charged_token_count = 0 && member_id IS NOT NULL`이다 — `billing_required` 컬럼이 있어 `isFirstUse`를 다시 계산하지 않는다.

`useTokens`는 **워커 스레드에서** 호출되므로 요청 스레드가 놓은 `lock:resume-analysis:{memberId}`와 중첩되지 않는다(기존 질문 플로우의 2단 중첩이 사라진다).

### 7-3. 첫 사용 무료 판정 (D11)

**판정 대상: `resume_analysis`의 회원 제출 행 하나뿐이다.** 구 판정식 조건 ①이 근거로 삼은 `resume_question_generation`과 `ResumeQuestionGenerationRepository`가 모두 삭제되므로(M1) 판정에 쓸 이력이 신규 테이블에만 존재한다.

```java
    // 구 질문생성 이력(resume_question_generation)은 M1으로 테이블째 사라졌으므로 판정에 쓸 수 없다.
    // 따라서 무료 1회는 신규 resume_analysis 과금 대상 이력만으로 판정한다.
    // 결과: 구 플로우를 이미 유료로 써 본 기존 회원 전원에게 무료 1회가 재부여된다.
    // 이 과금 정책 변경은 착수 전 인간 판정 대상이다(§10 X-3).
    // existsChargeableByMemberId의 쿼리는 guest_token IS NULL 조건을 포함하므로
    // claim된 게스트 행은 회원 무료 1회를 태우지 않는다.
    private boolean isFirstUse(Long memberId) {
        return !resumeAnalysisRepository.existsChargeableByMemberId(memberId);
    }
```

**이것은 제품 정책 변경이다 — 인간 판정 필요(§10 X-3).** 규모 확정 쿼리는 V53 적용 **전에** 돌린다:

```sql
-- 무료 1회를 다시 받게 되는 회원 수. A안 채택의 유일한 근거다.
SELECT COUNT(DISTINCT member_id) AS members_regaining_free_use FROM resume_question_generation;
```

**개정된 함정 표**

| 함정 | 막는 방식 |
|---|---|
| ~~신규 테이블만으로 판정 ⇒ 기존 사용자 전원 무료 1회 재부여~~ | **막지 않는다. A안으로 수용하는 것이 현 권고이며 미판정이다** |
| 게스트 무료 사용 후 claim ⇒ 회원 무료 1회 소진 | `existsChargeableByMemberId`의 `guest_token IS NULL`. claim은 `member_id`만 채우고 `guest_token`을 남긴다 |
| 서버 귀책 실패가 무료 1회를 태운다 | `failure_reason NOT IN (CAPACITY, STALE_SWEEP, PERSISTENCE, GUEST_LIMIT)` 필터 |
| 무료 사용자의 무한 재시도 | `EVALUATION_LLM`/`OUTPUT_TRUNCATED`는 소진으로 계산. `charged_token_count > 0`으로 바꾸면 무한 재시도가 열리므로 금지 |
| 판정·차감 시점 불일치 | 판정은 S5 1회, `billing_required` 컬럼 + `billingMemberId`(null=무료)로 고정 |

`tokenCost`는 항상 5(D11)이며 유일 정의처가 신규 파사드 상수가 된다.

`GET /api/v1/resume-analyses/usage-status`는 같은 `isFirstUse`를 써서 `firstUseFree`를 내보낸다.

### 7-4. 부분 실패(평가 성공 + 질문 실패) 처리

**`QUESTION_FAILED`로 평가를 계속 노출하고, 질문만 재생성하는 전용 엔드포인트를 둔다. 자동 재시도는 하지 않는다.**

| 선택지 | 판정 |
|---|---|
| 전체를 FAILED로 되돌린다 | 배제. D9의 단계적 공개를 무의미하게 만들고 이미 과금된 평가 결과를 폐기 |
| 워커가 질문 콜을 자동 N회 재시도 | 배제. 폴백 1회 이후의 실패는 대개 즉시 재시도로 풀리지 않고(스키마·잘림·계정 한도) 태스크 점유 시간이 늘어 풀 사이징 근거가 무너진다 |
| 평가만 노출하고 재시도 수단 없음 | **배제.** 5토큰을 내고 질문 0개 → `generated_question` 행이 없어 면접을 시작할 수 없다 → 이 API의 존재 목적이 달성 불가 상태로 고착되고, 환불 경로도 없어 순수 손실이 된다. 게스트는 여기에 365일 락까지 얹힌다 |
| **`QUESTION_FAILED` + 명시 재시도** | **채택.** `resume_analysis_source_text`에 원문이 있고 평가 결과도 15컬럼에 있어 `<evaluation_result>`를 재구성할 수 있다 ⇒ 질문 콜 1개만 다시 돌린다 |

```java
@DistributedLock(prefix = "resume-analysis-retry", key = "#analysisId")
public ResumeAnalysisQuestionRetryResponse retryQuestionGeneration(
        Long analysisId, MemberAuth memberAuth, String guestToken) {
    ResumeAnalysis analysis = resumeAnalysisService.readById(analysisId);
    validateAccessible(analysis, memberAuth, guestToken);
    ResumeAnalysisCommand command = resumeAnalysisAsyncService.readCommand(analysisId);   // 원문 없으면 400
    resumeAnalysisStateService.restoreForQuestionRetry(analysisId);   // QUESTION_FAILED → EVALUATION_COMPLETED
    try {
        ResumeAnalysisEvaluation evaluation = resumeAnalysisService.readEvaluation(analysisId);
        resumeAnalysisExecutor.execute(() ->
                resumeAnalysisAsyncService.runQuestionHop(command.withoutBilling(), evaluation));
    } catch (TaskRejectedException e) {
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.CAPACITY);
        throw new ServiceUnavailableException("이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.");
    }
    return new ResumeAnalysisQuestionRetryResponse(analysisId, ResumeAnalysisState.EVALUATION_COMPLETED,
            analysis.getQuestionRetryCount() + 1);
}
```

| 규칙 | 값 |
|---|---|
| 허용 상태 | `QUESTION_FAILED`만. 그 외 `BadRequestException("질문 재생성이 필요한 상태가 아닙니다.")` |
| 최대 횟수 | `MAX_QUESTION_RETRY = 2` (`question_retry_count`). 초과 또는 원문 만료 시 `question_retryable=false` + 400 |
| 과금 | **무과금**(`command.withoutBilling()` ⇒ W5 미실행). 이미 차감된 5토큰은 유지 |
| 상태 흐름 | `QUESTION_FAILED` → (즉시) `EVALUATION_COMPLETED` → `COMPLETED` 또는 다시 `QUESTION_FAILED`. 폴링 클라이언트는 상태 변화만 보면 되고 새 스키마가 필요 없다 |
| 게스트 | 허용(`guest_token` 소유 증명). 1회 제한 락은 건드리지 않는다 |
| 재실행 범위 | 질문 콜 1개(+GPT 폴백 1회). 평가 콜은 절대 다시 호출하지 않는다 |
| 중복 방어의 실체 | `@DistributedLock`은 202를 반환하는 순간 풀리므로(`DistributedLockAspect`의 `finally { unlock }`) 비동기 작업을 보호하지 않는다. **중복 실행을 막는 단일 수단은 `restoreForQuestionRetry`의 `WHERE id = ? AND state = 'QUESTION_FAILED'` 조건부 전이 + 영향 행수 1 확인이다.** 0행이면 400 |

`restoreForQuestionRetry`가 `question_started_at = now()`를 갱신하므로 재시도가 sweep에 즉시 잡히지 않는다(§6-3).

### 7-5. 게스트 락 (D12)

| 항목 | 값 |
|---|---|
| 키 | `"guest:resume-analysis:started:" + clientIp.address()` |
| TTL | `Duration.ofDays(365)` |
| 획득 | `redisService.acquireLockWithValue(lockKey, lockValue, TTL)` (내부 `setIfAbsent`) |
| 락 값 | `UUID.randomUUID().toString()` — **`guest_token`과 다른 별개 UUID.** 락 값은 365일 Redis에 남고 NAT 공유 IP에서 조회 가능성이 있어 소유 증명 토큰과 분리한다. `resume_analysis.guest_lock_value`에 영속화한다 |
| 실패 | `throw new BadRequestException("비회원 이력서 분석은 1회만 가능합니다.");` |
| 획득 위치 | §6-1 S8 (추출 완료 후, INSERT 직전) |
| 해제 ① | 파사드 `catch (RuntimeException e) { releaseLockSafely(lockKey, lockValue); throw e; }` — INSERT/제출 실패 보상 |
| 해제 ② | **워커·sweep이 `EVALUATION_FAILED`로 종단할 때** `releaseLockSafely(GUEST_..._PREFIX + analysis.getGuestIp(), analysis.getGuestLockValue())` |
| 해제하지 않는 경우 | `QUESTION_FAILED`(평가는 제공했으므로 1회 소진), `COMPLETED`, claim 이후, 30일 후 행 정리 시(1회 제한은 영구 유지) |

```java
// ResumeAnalysisStateService
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void failEvaluation(Long analysisId, ResumeAnalysisFailureReason reason) {
    ResumeAnalysis analysis = resumeAnalysisRepository.findByIdForUpdate(analysisId).orElseThrow();
    if (analysis.getState() != ResumeAnalysisState.PENDING) {
        return;                                  // 이미 다른 주체가 전이시켰다 → 폐기
    }
    analysis.failEvaluation(reason);
    releaseGuestLockIfNeeded(analysis);
}

private void releaseGuestLockIfNeeded(ResumeAnalysis analysis) {
    if (!analysis.isGuest() || analysis.getGuestLockValue() == null) {
        return;
    }
    redisService.releaseLockSafely(
            GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX + analysis.getGuestIp(), analysis.getGuestLockValue());
}
```

`guest_lock_value` 컬럼이 필수인 이유: `releaseLockSafely`는 Lua CAS(`if get(KEYS[1]) == ARGV[1] then del`)이므로 값을 정확히 알아야만 해제된다. 값 없이 `releaseLock(key)`(무조건 삭제)을 쓰면 같은 NAT의 다른 게스트가 방금 잡은 락을 A의 워커가 지워버려 세 번째 사용자가 무료로 통과하는 오해제가 발생한다. **게스트 락에 `releaseLock`(무조건 삭제)을 쓰는 것은 설계 금지 사항이다.**

### 7-6. 잔류 행 회수 스케줄러

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class ResumeAnalysisRecoveryScheduler {

    private static final String SWEEP_LOCK_KEY = "lock:resume-analysis:sweep:scheduler";
    private static final Duration SWEEP_LOCK_TTL = Duration.ofMinutes(4);   // 실행 간격(5분)보다 짧게
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    private static final int MAX_SWEEP_COUNT = 200;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sweepStaleAnalyses() {
        if (!redisService.acquireLock(SWEEP_LOCK_KEY, SWEEP_LOCK_TTL)) {
            log.debug("이력서 분석 잔류 정리 스킵 - 다른 인스턴스가 실행 중");
            return;
        }
        try {
            LocalDateTime threshold = LocalDateTime.now().minus(STALE_THRESHOLD);
            int pending = stateService.sweepStalePending(threshold, MAX_SWEEP_COUNT);
            int questionStage = stateService.sweepStaleQuestionStage(threshold, MAX_SWEEP_COUNT);
            if (pending >= MAX_SWEEP_COUNT || questionStage >= MAX_SWEEP_COUNT) {
                log.warn("이력서 분석 잔류 정리 상한 도달 - pending: {}, questionStage: {}", pending, questionStage);
            }
        } catch (Exception e) {
            log.error("이력서 분석 잔류 행 정리 실패", e);
            redisService.releaseLock(SWEEP_LOCK_KEY);
        }
    }
}
```

- `sweepStalePending`: `state='PENDING' AND created_at < threshold` → 행별로 `failEvaluation(STALE_SWEEP)`(게스트 락 해제 포함).
- `sweepStaleQuestionStage`: `state='EVALUATION_COMPLETED' AND question_started_at < threshold` → 행별로 `failQuestions(STALE_SWEEP)` + `chargeTokensIfNeeded`(회수 과금).
- **벌크 UPDATE로 끝내지 않고 행별 처리**한다 — 게스트 락 해제와 회수 과금이 행 데이터를 필요로 한다. 각 전이는 `findByIdForUpdate` + 상태 가드이므로 살아있던 워커와 경합해도 한쪽만 성공한다(패배한 쪽은 결과를 폐기한다).
- **재구동하지 않는다**(LLM 중복 비용 + 이중 실행 위험). 재실행은 사용자 명시 재시도로만.
- 락 실패를 `log.debug` + 스킵 카운터 메트릭으로 남긴다(Redis 장애 시 회복 장치가 조용히 죽는 것을 관측 가능하게 한다).
- 임계값 10분 근거: Bedrock 60s socketTimeout × SDK 기본 재시도 + GPT 폴백(read 90s 명시 설정)까지 합쳐도 10분 이내.

**진행 중 1건 제한에 15분 시간 창을 두는 이유**: 고착 행 하나가 회원을 영구 제출 차단하는 것을 막는다(sweep이 Redis 장애로 침묵하는 동안에도 15분 뒤에는 재제출이 가능해진다). 창 크기는 `STALE_THRESHOLD`(10분)보다 크게 잡아 정상 진행 건과 겹치지 않게 한다.

### 7-7. 게스트 행 정리 스케줄러

```java
@Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
public void deleteUnclaimedGuestAnalyses() {
    if (!redisService.acquireLock("lock:resume-analysis:cleanup:scheduler", Duration.ofHours(1))) {
        return;
    }
    try {
        LocalDateTime threshold = LocalDateTime.now().minusDays(GUEST_RETENTION_DAYS);        // 30
        List<Long> ids = resumeAnalysisRepository.findUnclaimedGuestAnalysisIds(threshold, MAX_CLEANUP_COUNT);
        if (!ids.isEmpty()) {
            generatedQuestionRepository.deleteByAnalysisIdIn(ids);   // 자식 먼저 (CASCADE 없음)
            resumeAnalysisRepository.deleteByIds(ids);               // source_text는 ON DELETE CASCADE
        }
        purgeExpiredSourceTexts();                                   // 종단 상태 + 30일 경과
        log.info("미claim 게스트 분석 정리 - analyses: {}", ids.size());
        if (ids.size() >= MAX_CLEANUP_COUNT) {
            log.warn("게스트 분석 정리 상한 도달 - 남은 백로그가 있다");
        }
    } catch (Exception e) {
        log.error("게스트 분석 정리 실패", e);
        redisService.releaseLock("lock:resume-analysis:cleanup:scheduler");
    }
}
```

| 항목 | 값 | 근거 |
|---|---|---|
| 주기 | `0 30 4 * * *` Asia/Seoul | 00:00 `rechargeDailyFreeToken`, 05:00 `syncInterviewViewCounts`와 겹치지 않는다 |
| 대상 | `member_id IS NULL AND guest_token IS NOT NULL AND created_at < now-30d` | claim된 행은 영구 보존. 회원 행 오삭제를 두 조건으로 막는다 |
| 상한 | `MAX_CLEANUP_COUNT = 500` | `PaymentRecoveryScheduler.MAX_RECOVERY_COUNT` 패턴. LIMIT 없는 DELETE는 백로그가 크면 수십만 행을 잠그고 1시간 락 TTL을 넘겨 두 번째 인스턴스가 병렬 실행에 들어간다 |
| `interview` 참조 가드 | `NOT EXISTS(GeneratedQuestion ← Interview)` | 게스트는 면접을 시작할 수 없으므로 원칙적으로 참조가 없지만, 어떤 이유로든 한 행이 걸리면 첫 DELETE가 FK 위반으로 **배치 전체를 롤백**시켜 매일 같은 행에서 실패하고 개인정보 보존 정책이 무기한 무력화된다. 대상 선정 단계에서 걸러 한 행 때문에 배치가 멈추지 않게 한다 |
| 원문 만료 | `SOURCE_TEXT_RETENTION_DAYS = 30` | 종단 상태 + 30일 경과 행의 `resume_analysis_source_text`를 별도로 지운다(LONGTEXT 무한 증가 방지). 만료 후 `question_retryable`은 `false`가 된다 |
| 삭제 순서 | `generated_question` → `resume_analysis` (`source_text`는 CASCADE) | `generated_question.analysis_id` FK에 CASCADE를 걸지 않은 것은 공유 테이블의 암묵적 연쇄 삭제를 금지하기 위한 의도된 안전장치다 |

이 스케줄러는 **미claim 게스트 행만** 정리한다. 고착 행의 종단 처리는 §7-6의 책임이다.

### 7-8. 파일 영속화 정책

| 항목 | 회원(파일 제출) | 회원(`resume_id` 재사용) | 게스트 |
|---|---|---|---|
| `PdfValidator` + 페이지 상한 | 적용 | — | 적용 |
| 텍스트 추출 | 요청 스레드, `extractText(MultipartFile)` | `member_resume.content` 있으면 재사용, 없으면 S3 다운로드 후 `extractText(byte[])` + `content` 역기록 | 요청 스레드 |
| 추출 실패 | 400, 행 없음 | 400 | 400 (락 미획득 상태) |
| S3 업로드 | `pdfUploadService.saveResume/savePortfolio` | 없음 | **없음** |
| `member_resume` 행 | 생성 + FK 채움 | 기존 행 참조 | **생성 불가**(`member_id NOT NULL`) → FK NULL |
| 텍스트 보관 | `source_text` **및** `member_resume.content` | 양쪽 | `source_text`만 |

게스트 원본 PDF를 S3에 올리지 않는다 — 올리면 claim 시 S3 객체 이동 + `member_resume` 행 생성이 필요해져 D10의 "claim은 UPDATE 한 줄"을 위반한다. claim으로 승계되는 것은 `resume_analysis` 행(+ `source_text`)뿐이며, claim 후에도 그 이력서는 `GET /api/v1/resumes?type=`의 저장된 목록에 나타나지 않는다(의도된 동작, API 문서에 명시). `resume_analysis.member_resume_id`는 게스트 유래 행에서 영구 NULL이다.

회원 경로에서 `member_resume.content`와 `source_text`가 중복되지만, (a) 질문 재시도가 재추출·S3 재다운로드 없이 동작해야 하고, (b) 게스트는 보관처가 여기밖에 없으며, (c) 사용자가 `member_resume`를 교체·삭제해도 분석 결과의 재현성이 유지된다.

---

## 8. 테스트 계획

### 8-1. 삭제·수정되는 기존 테스트와 새 게이트

**D1·D2는 폐기됐다. "기존 테스트는 100% 무수정으로 통과해야 한다"는 원칙은 더 이상 적용되지 않는다.** 구체적인 테스트 파일별 삭제·수정 목록과 검증 게이트(G1~G5) 명령은 구현 계획 문서(§6)에서 관리한다. 이 절에는 그 계획과 무관하게 **여전히 유효한 제약**(근거만 바뀐 것)만 남긴다.

| 대상 | 제약 | 개정된 근거 |
|---|---|---|
| `MemberResume`/`MemberPortfolio` | 엔티티 필드 추가 금지 | 두 픽스처가 5인자 `@AllArgsConstructor`에 의존하고 **존치**된다(`ResumeAnalysisRepositoryTest`가 사용) |
| `H2AutoIncrementCleaner` / `InterviewDocsTest`·`InterviewDocsV2Test` | `@Table(name)` = 클래스명 스네이크, 신규 테이블에 `id` 필수 | 불변. 위반 시 `docs` `@BeforeEach` 즉사 |
| `src/test/resources/application.yml` | Bedrock/GPT 프로퍼티 **추가·삭제 모두 금지** | `@Validated` + 전 필드 `@NotNull`. 기존 4개 키는 전부 신규 클라이언트가 사용 |
| `MySQLDatabaseCleaner` | 수정 불필요 | `INFORMATION_SCHEMA` 매 실행 동적 조회 |
| `src/docs/asciidoc/index.adoc` | **세 구간 삭제 필수** | 원판의 "L132–206·L664–741 무수정"이 정반대로 뒤집힌다(§8-6) |

### 8-2. 신규 도메인 단위 테스트 (Spring 미기동)

**`ResumeAnalysisWeightsTest`**

| 메서드 | 단정 |
|---|---|
| `JD가_제공되면_5지표_가중치의_합은_1이다` | `isCloseTo(1.0, offset(1e-9))` |
| `JD가_없으면_4지표_가중치의_합은_1이다` | 동일 |
| `JD_제공_가중치로_종합점수를_계산한다` | 90/80/70/60/50 → 73.5 → **74** |
| `JD_미제공_가중치로_종합점수를_계산한다` | 90/80/70/60 → 78 |
| `JD_미제공에서_JD적합성은_0점으로_취급되지_않는다` | 위 입력에 JD_ABSENT → **78**(66이 아님). 구 `scoreOf(null)→0` 버그 회귀 방지 |
| `가중합의_소수점은_반올림된다` | 73.5 → 74 |
| `모든_지표가_100이면_두_세트_모두_100이다` / `모두_0이면_0이다` | |
| `JD가_제공됐는데_JD적합성_점수가_없으면_예외가_발생한다` | `ExternalApiException` |
| `JD가_없는데_JD적합성_점수가_오면_예외가_발생한다` | 키 집합 불일치 |
| `가중치_세트의_차원_목록은_선언_순서를_유지한다` | `dimensions()` |
| `프롬프트의_가중치_문자열은_코드의_가중치와_일치한다` | `SCORING_WEIGHTS_WITH_JD`가 `"- problem_solving 0.25"` 등 5줄을, `WITHOUT_JD`가 4줄을 contains |

**`ResumeAnalysisTest`**

| 메서드 |
|---|
| `생성_직후_상태는_PENDING이다` |
| `평가_결과를_기록하면_EVALUATION_COMPLETED가_된다` (15컬럼 + `evaluationCompletedAt` + `questionStartedAt`) |
| `평가_기록_후_질문을_기록하면_COMPLETED가_된다` |
| `PENDING에서_질문을_먼저_기록하면_예외가_발생한다` |
| `EVALUATION_COMPLETED에서_평가를_다시_기록하면_예외가_발생한다` |
| `평가_실패는_EVALUATION_FAILED이고_질문_실패는_QUESTION_FAILED다` |
| `질문_실패_상태에서도_평가_결과는_보존된다` |
| `질문_실패에서_재시도로_복원하면_EVALUATION_COMPLETED가_되고_재시도_횟수가_늘어난다` |
| `재시도_복원은_question_started_at을_갱신한다` |
| `COMPLETED에서는_재시도로_복원할_수_없다` |
| `JD가_없으면_JD적합성_3개_필드가_모두_null로_남는다` |
| `COMPLETED가_아니면_면접을_시작할_수_없다` |
| `게스트_분석은_member가_null이고_guest_token과_guest_lock_value를_가진다` |
| `회원_분석은_guest_token이_null이다` |
| `isOwner는_게스트_행에서_예외없이_false를_반환한다` (`ResumeQuestionGeneration.isOwner` NPE 회귀 방지) |
| `다른_guest_token으로는_소유자로_인정되지_않는다` |
| `재시도_횟수가_상한이면_question_retryable은_false다` |
| `원문이_없으면_question_retryable은_false다` |

**`GeneratedQuestionTest`(기존 파일, M3로 6개 → 5개)**

| 처분 | 대상 |
|---|---|
| **삭제** | `기존_생성_흐름의_질문은_generation만_채우고_analysis는_null이다()` — `ResumeQuestionGeneration`과 4인자 public 생성자 소멸 |
| **단정 1줄 삭제 + 개명** | `분석용_질문은_analysis만_채우고_generation은_null이다` → `분석용_질문은_analysis와_질문_내용을_채운다`. `assertThat(question.getGeneration()).isNull(),` 삭제 |
| **import 삭제** | `MemberFixtureBuilder` — 삭제된 테스트가 단독 사용이면 함께 삭제. private 헬퍼가 별도로 쓰는지 확인 후 결정(§10 항목) |
| 존치 | 나머지 4개 무수정 |

`ResumeAnalysisRepositoryTest`의 `() -> assertThat(questions).allSatisfy(q -> assertThat(q.getGeneration()).isNull()),` 1줄도 함께 삭제한다. 나머지 무수정.

**M3로 XOR 검증 테스트가 불필요해진다.** Task 3 deferred finding("XOR 제약을 DB 레벨에서 검증하는 테스트 없음")은 **최종 스키마에 제약이 없어 결함 조건이 소멸**한다. `analysis_id NOT NULL` + `@JoinColumn(nullable=false)`이 자동 강제하고 위반 경로가 타입 시스템에 없다.

### 8-3. 스키마 테스트 — `ResumeAnalysisFlatSchemaTest`

`assertNoNestedObject` 헬퍼와 `DocumentJsonConverter.toJavaObject` 패턴은 구 `ResumeEvaluationFlatSchemaTest`에서 유래했으나 그 파일이 삭제되므로 **이 파일이 유일본이다.**

| 메서드 | 단정 |
|---|---|
| `JD가_제공되면_Bedrock_평가_스키마의_required는_21개다` | |
| `JD가_없으면_Bedrock_평가_스키마의_required는_17개다` | |
| `JD가_없으면_properties에_jd_fit로_시작하는_키가_하나도_없다` | D6: 느슨한 required가 아니라 필드 자체 부재 |
| `JD가_있으면_properties에_jd_fit_4개_필드가_존재한다` | |
| `평가_스키마는_JD_유무와_무관하게_중첩_object가_없다` | `assertNoNestedObject` ×2 |
| `GPT_평가_스키마도_jdProvided에_따라_required_개수가_같다` | 21 / 17 |
| `평가_스키마의_required_집합은_Bedrock과_GPT가_완전히_동일하다` | `Set` 비교, true/false 각각. 두 곳 중복 정의의 드리프트를 잡는 유일한 단정 |
| `점수_필드는_integer이고_최소0_최대100이다` | |
| `근거_배열은_최소2개_최대6개다` | |
| `질문_스키마는_최소5개_최대7개의_배열이다` | |
| `질문_스키마의_minItems와_maxItems는_Bedrock과_GPT가_같다` | |
| `질문과_이유_필드에는_maxLength가_설정되어_있다` | 300 / 600. 컬럼 한도 truncation 방어 |
| `도구_이름은_평가와_질문이_서로_다르다` (개명, 원명 `신규_도구_이름은_기존_도구_이름과_겹치지_않는다`) | 리터럴 `isEqualTo` 2건이 와이어 계약(Bedrock `toolChoice.tool.name` / GPT `tool_choice.function.name`)을 고정 |
| `구지표_이름은_신규_스키마에_존재하지_않는다` | `career_growth`, `documentation` 부재. 의미가 "구/신 격리(D2)"에서 **"폐기된 5지표 체계로의 회귀 방지"**로 바뀌었을 뿐 실효는 유지 |

```java
    @Test
    void 도구_이름은_평가와_질문이_서로_다르다() {
        assertThat(ResumeAnalysisToolNames.EVALUATION)
                .isEqualTo("submit_resume_analysis_evaluation");
        assertThat(ResumeAnalysisToolNames.QUESTION_GENERATION)
                .isEqualTo("submit_resume_analysis_questions")
                .isNotEqualTo(ResumeAnalysisToolNames.EVALUATION);
    }
```

`import ResumeToolNames`는 삭제한다. **테스트 총수 18개 유지(삭제 0).**

**flat 응답 매핑 테스트**

| 메서드 | 단정 |
|---|---|
| `JD포함_flat_응답은_5지표로_매핑되고_종합점수는_JD포함_가중치로_계산된다` | 21필드 JSON → 74 |
| `JD미포함_flat_응답은_4지표로_매핑되고_JD적합성은_null이다` | 17필드 → 78, `jdFit()` null |
| `reasoning_필드는_무시된다` | `FAIL_ON_UNKNOWN_PROPERTIES=false` 전제 고정 |
| `질문_flat_응답은_질문과_이유_쌍으로_매핑된다` | |

### 8-4. 프롬프트 일관성 테스트 — `ResumeAnalysisSystemMessageConsistencyTest`

**총 21개 → 18개.** 구 클래스(`ResumeSystemMessages`, `ResumeToolNames`, `ResumePromptFragments`)가 삭제되므로 그 심볼을 직접 참조하던 테스트 3개(`기존_평가_시스템_메시지는_신규지표를_포함하지_않는다`, `기존_질문_시스템_메시지는_평가결과_규칙을_포함하지_않는다`, `신규_도구_이름은_기존_도구_이름과_겹치지_않는다` — 후자는 §8-3과 완전 중복이라 §8-3 쪽만 남긴다)를 **전체 삭제**한다. 나머지는 상수 참조만 `ResumeAnalysisPromptFragments.*`로 교체하며 의미·결과는 동일하다. 렌더러 테스트 5개는 구 심볼을 참조하지 않으므로 무수정이다.

클래스 Javadoc:

```java
/**
 * 이력서 분석(5지표) 프롬프트의 일관성을 검증한다.
 * 폐기된 구 지표명·구 관찰항목이 신규 프롬프트에 재유입되지 않는지도 함께 단정한다.
 */
```

| 메서드 | 단정 |
|---|---|
| `평가_시스템_메시지는_신규5지표_이름을_모두_포함한다` | 5개 `toolKey()` |
| `평가_시스템_메시지는_구지표_이름을_포함하지_않는다` | `career_growth`, `documentation` 부재 |
| `JD가_있으면_평가_프롬프트에_JD적합성_지시가_들어간다` | `evaluation(true)` |
| `JD가_없으면_JD적합성_지시가_없고_4지표_가중치가_명시된다` | `evaluation(false)`, `"0.30"` 포함, `"jd_fit 0.15"` 부재 |
| `JD_부재를_감점_사유로_삼지_말라는_규칙이_유지된다` | `"JD 부재 자체를 감점 사유로 삼거나"` 포함 |
| `소프트스킬_기준은_근거_부재를_감점하지_않고_중립_기준점으로_채점한다고_명시한다` | `"중립 기준점"`, `"부재를 감점 사유로 쓰지 않는다"` |
| **`소프트스킬은_근거가_있을_때만_채점하는_항목을_명시한다`** | `contains("STAR", "본인이 담당한 역할", "기술 블로그", "멘토링", "조직 개편", "갈등 해결", "기재되어 있을 때에만 채점")`. **D7은 그 항목들의 삭제가 아니라 조건부 채점을 요구했으므로 "멘토링·조직 개편·갈등 해결 부재" 단정은 D7 위반이다** |
| `폐기된_구_관찰항목은_신규_프롬프트에_없다` | `doesNotContain("오탈자", "경력 발전 경로", "지속적인 학습")`. **구 원문이 `"지속적인 학습 및 성장 증거"`이므로 `"지속적 학습"`은 부분 문자열이 아니다 — 구 프롬프트 삭제 후 이 단정이 폐기 문구 재유입을 막는 유일한 방어선이 되므로 정확한 문자열이 필수다** |
| `평가_시스템_메시지는_GPT와_Bedrock이_단일_소스에서_나온다` | jdProvided true/false 각각 문자열 동일 |
| `질문_시스템_메시지는_GPT와_Bedrock이_단일_소스에서_나온다` | |
| `질문_user_메시지에는_평가결과가_evaluation_result_태그로_주입된다` | |
| `질문_시스템_메시지는_평가결과와_무관하게_항상_동일하다` | **D8 + 캐시 프리픽스 보호** |
| `평가_user_메시지에는_evaluation_result_태그가_없다` | |
| `독립성_원칙과_보안규칙은_신규_평가_프롬프트에도_포함된다` | |
| `신규_페르소나_인칭도_너로_통일됐다` | `startsWith("너는")` |
| `평가결과_렌더러는_JD가_없으면_jd_fit_블록을_생략한다` | `jd_provided=false` |
| `평가결과_렌더러는_근거가_없으면_없음으로_표기한다` | `"strengths: (없음)"` |
| `평가결과_렌더러는_구분자와_괄호를_치환한다` | `|`→`/`, `<`→`(` |

### 8-5. 서비스 통합 테스트 (`BaseTest`)

**`ResumeAnalysisFacadeServiceTest`** — 제출·게스트·검증

| 메서드 |
|---|
| `회원이_이력서와_JD로_분석을_제출하면_PENDING_행과_원문이_저장되고_비동기가_시작된다` |
| `회원이_JD_없이_제출하면_job_description이_null이고_jd_provided가_false로_저장된다` |
| `토큰이_부족하면_분석_행이_저장되지_않는다` |
| `진행_중_분석이_있으면_제출할_수_없다` |
| `15분이_지난_고착_분석은_제출을_막지_않는다` |
| `PDF가_아니면_제출_단계에서_예외가_발생한다` (구 질문 플로우의 `PdfValidator` 미적용 교정) |
| `저장된_이력서_ID로_제출하면_기존_content를_재사용한다` |
| `게스트가_제출하면_member_id는_null이고_guest_token과_guest_lock_value가_저장된다` |
| `게스트는_저장된_이력서_ID를_사용할_수_없다` |
| `같은_IP의_게스트가_두_번_제출하면_예외가_발생한다` (상수 참조로 락 선점 후 400) |
| `게스트_제출_중_INSERT_실패시_IP_락이_해제된다` |
| `추출이_실패하면_게스트_락을_잡지_않는다` (`redisTemplate.hasKey(락키)` false) |
| `게스트_시간당_시도_한도를_초과하면_예외가_발생한다` |
| `회원_제출은_IP_락을_사용하지_않는다` |
| `billing_required는_첫_사용_무료_판정_결과로_저장된다` |
| `신규_분석_이력이_없는_회원은_첫_사용이_무료다` (**M1 이후 유일 판정 소스가 `resume_analysis`뿐임을 고정** — 구 `resume_question_generation` 기반 조건은 M1로 삭제됨) |
| `신규_분석_이력이_있는_회원은_두_번째부터_과금_대상이다` |
| `claim된_게스트_분석이_있어도_회원_첫_사용은_무료다` (`guest_token IS NULL` 조건 고정) |
| `서버_귀책_실패_이력은_무료_1회를_소진시키지_않는다` (`CAPACITY`/`STALE_SWEEP`) |
| `LLM_실패_이력은_무료_1회를_소진시킨다` (`EVALUATION_LLM`) |

**`ResumeAnalysisAsyncServiceTest`** — hop 직접 호출(executor 우회)

| 메서드 |
|---|
| `평가_hop이_끝나면_EVALUATION_COMPLETED가_되고_15개_컬럼이_저장된다` |
| `평가_hop은_question_started_at을_세팅한다` |
| `질문_hop이_끝나면_COMPLETED가_되고_질문_5개가_순서대로_저장된다` (`question_order` 0..4, `analysis_id` 채움, `generation_id` NULL) |
| `평가_hop이_실패하면_EVALUATION_FAILED이고_질문_hop은_호출되지_않는다` (`verify(questionClient, never())`) |
| `질문_hop만_실패하면_QUESTION_FAILED이고_평가_결과는_남는다` |
| `Bedrock_평가가_실패하면_GPT_폴백으로_평가가_완료된다` |
| `Bedrock_질문생성이_실패하면_GPT_폴백으로_질문이_완료된다` |
| `Bedrock_평가가_실패하면_질문_콜은_Bedrock을_건너뛴다` (`verify(questionBedrockClient, never())`) |
| `Bedrock과_GPT_모두_실패하면_해당_단계의_실패_상태가_기록된다` |
| `JD를_주면_5지표가_저장되고_종합점수는_JD포함_가중치로_계산된다` |
| `JD를_주지_않으면_jd_fit_컬럼_3개가_null이고_4지표_가중치가_적용된다` |
| `질문_hop에는_평가_결과가_evaluation_result로_주입된다` (`ArgumentCaptor`) |
| `이미_COMPLETED된_분석에_평가_hop을_재실행하면_결과가_폐기된다` |
| `평가_hop이_성공하면_토큰_5개가_차감된다` |
| `평가_hop이_실패하면_토큰이_차감되지_않는다` |
| `게스트_분석은_hop_성공에도_토큰이_차감되지_않는다` |
| `이미_과금된_분석에_hop을_재실행해도_이중_차감되지_않는다` (CAS) |
| `토큰_차감이_계속_실패하면_결과는_제공되고_실패_플래그가_남는다` |
| `게스트_평가_실패시_IP_락이_해제된다` |
| `게스트_질문_실패시_IP_락은_유지된다` |
| `질문_내용이_컬럼_한도를_넘으면_절단되어_저장된다` |

**`ResumeAnalysisWiringTest`** (Spring 미기동, `BedrockRuntimeClient`만 목)

| 메서드 |
|---|
| `평가_콜은_temperature_0점2와_maxTokens_10000으로_호출된다` |
| `질문_콜은_temperature_0점7과_maxTokens_2048으로_호출된다` |
| `평가_콜과_질문_콜이_이_순서로_정확히_한_번씩_호출된다` (`InOrder`) |
| `두_콜_모두_system_블록_마지막에_캐시포인트가_붙는다` |
| `평가_콜의_toolChoice는_평가_도구로_강제된다` / `질문_콜의_toolChoice는_질문_도구로_강제된다` |
| `JD가_없으면_평가_콜의_toolConfig에_jd_fit_필드가_없다` |
| `질문_콜의_user_메시지에만_evaluation_result가_들어간다` |

**`ResumeAnalysisQuestionRetryTest`**

| 메서드 |
|---|
| `QUESTION_FAILED에서_재시도하면_EVALUATION_COMPLETED로_복원되고_질문_콜만_다시_실행된다` |
| `재시도는_평가_콜을_호출하지_않는다` |
| `재시도는_토큰을_차감하지_않는다` |
| `재시도_상한을_초과하면_400을_반환한다` |
| `COMPLETED_상태에서_재시도하면_400을_반환한다` |
| `게스트도_guest_token으로_재시도할_수_있다` |
| `원문이_삭제된_분석은_재시도할_수_없다` |
| `동시_재시도_두_건_중_하나만_통과한다` (조건부 전이 CAS) |

**`ResumeAnalysisClaimServiceTest`**

| 메서드 |
|---|
| `게스트_분석을_로그인_회원이_claim하면_member_id가_채워지고_guest_token은_남는다` |
| `이미_회원_소유인_분석은_claim해도_member_id가_바뀌지_않는다` |
| `본인이_이미_claim한_분석을_다시_claim하면_200이다` |
| `다른_회원이_claim한_분석은_403이다` |
| `존재하지_않는_guest_token으로_claim하면_404다` |
| `이미_비회원_분석을_연결한_회원은_추가_claim이_400이다` |
| `claim_후_옛_guest_token으로는_조회할_수_없다` (403 — 토큰 효력 종료) |
| `claim된_분석으로_회원은_면접을_시작할_수_있다` |
| `질문_hop_진행_중_claim해도_member_id가_소실되지_않는다` (PESSIMISTIC_WRITE 경합) |

**`ResumeAnalysisInterviewStartTest`**

| 메서드 |
|---|
| `COMPLETED_분석의_질문으로_텍스트모드_면접을_시작한다` (`interview.generated_question_id` 채움, `interview_type=RESUME_BASED`) |
| `음성모드_면접_시작은_토큰_2배를_요구한다` |
| `EVALUATION_COMPLETED_상태에서는_면접을_시작할_수_없다` |
| `미claim_게스트_분석으로는_면접을_시작할_수_없다` |
| `다른_회원의_분석으로는_면접을_시작할_수_없다` (403) |
| `분석에_속하지_않는_질문_ID로는_면접을_시작할_수_없다` |
| `구_질문생성_플로우의_질문_ID로는_시작할_수_없다` (`findByIdAndAnalysisId` 격리) |
| `이력서분석_기반_면접의_목록_조회에서_질문_내용이_정상_노출된다` (`getDisplayQuestion()` 무수정 검증) |

**`ResumeAnalysisRecoverySchedulerTest`**

| 메서드 |
|---|
| `잔류_PENDING은_EVALUATION_FAILED로_종단된다` |
| `잔류_질문단계는_QUESTION_FAILED로_종단된다` (`question_started_at` 기준) |
| `평가_직후_질문_콜_진행_중인_행은_종단되지_않는다` (`question_started_at`이 최신) |
| `재시도로_복원된_행은_즉시_종단되지_않는다` |
| `sweep이_찍은_뒤_도착한_워커_결과는_폐기된다` |
| `잔류_게스트_PENDING_종단시_IP_락이_해제된다` |
| `잔류_질문단계_종단시_미과금이면_회수_과금된다` |
| `종단_상한_건수를_초과하지_않는다` |

**`ResumeAnalysisCleanupSchedulerTest`**

| 메서드 |
|---|
| `보존기간이_지난_미claim_게스트_분석과_질문이_삭제된다` |
| `원문_사이드_테이블도_함께_삭제된다` |
| `claim된_분석은_삭제되지_않는다` |
| `기준시간_이내의_게스트_분석은_삭제되지_않는다` |
| `면접이_참조하는_질문을_가진_분석은_대상에서_제외된다` |
| `삭제_상한_건수를_초과하지_않는다` |
| `종단_상태의_만료된_원문은_별도로_삭제된다` |

### 8-6. 컨트롤러 테스트와 RestDocs

`ResumeAnalysisControllerTest`(제출·조회·목록·claim·재시도·usage-status) + `ResumeAnalysisInterviewControllerTest`(면접 시작). 문서화 identifier 18개:

| identifier | 테스트 메서드 | 문서화 블록 |
|---|---|---|
| `resume-analysis-submit-member-with-file` | `회원_파일_업로드로_이력서_분석_제출_성공` | requestHeaders, requestParts(5), responseFields(1) |
| `resume-analysis-submit-member-with-saved-resume` | `회원_저장된_이력서로_분석_제출_성공` | requestParts(4), responseFields(1) |
| `resume-analysis-submit-member-without-jd` | `채용공고_없이_이력서_분석_제출_성공` | requestParts(4), responseFields(1) |
| `resume-analysis-submit-guest` | `비회원_이력서_분석_제출_성공` | requestHeaders(X-Forwarded-For), requestParts(4), responseFields(2) |
| `resume-analysis-submit-guest-duplicate-ip` | `비회원이_같은_IP로_두_번_제출하면_400` | requestHeaders만 (`interview-startGuestInterview-duplicateIp` 선례와 동형) |
| `resume-analysis-get-pending` | `이력서_분석_조회_대기중` | pathParameters, responseFields(evaluation/questions 미포함) |
| `resume-analysis-get-evaluation-completed` | `이력서_분석_조회_평가완료_JD포함` | pathParameters, responseFields(evaluation 5차원 ×4 + total 2) |
| `resume-analysis-get-evaluation-completed-without-jd` | `이력서_분석_조회_평가완료_JD미제공` | **`evaluation.jd_fit*` 경로를 넣지 않는다** |
| `resume-analysis-get-completed` | `이력서_분석_조회_완료` | + `questions[]` 4필드 |
| `resume-analysis-get-evaluation-failed` | `이력서_분석_조회_평가실패` | 최소 집합 |
| `resume-analysis-get-question-failed` | `이력서_분석_조회_질문생성실패` | + `question_retryable` |
| `resume-analysis-get-guest` | `비회원_이력서_분석_조회_성공` | pathParameters, queryParameters(`guest_token`), requestHeaders 없음 |
| `resume-analysis-list` | `내_이력서_분석_목록_조회_성공` | requestHeaders, queryParameters(4, 전부 `.optional()`), responseFields(`data[]` 8필드 전부 열거) |
| `resume-analysis-claim` | `비회원_이력서_분석_회원_귀속_성공` | requestHeaders, requestFields(1), responseFields(2) |
| `resume-analysis-question-retry` | `질문_재생성_요청_성공` | requestHeaders, pathParameters, responseFields(3) |
| `resume-analysis-usage-status` | `이력서_분석_이용_상태_조회_성공` | requestHeaders, responseFields(2) |
| `resume-analysis-interview-start-text-mode` | `이력서_분석_기반_면접_시작_텍스트모드_성공` | requestHeaders, pathParameters, requestFields(3), responseFields(3) |
| `resume-analysis-interview-start-voice-mode` | `이력서_분석_기반_면접_시작_음성모드_성공` | 동일 + voice url |

문서화 없는 예외 테스트: `인증없이_목록을_조회하면_401`, `남의_분석을_조회하면_403`, `존재하지_않는_분석을_조회하면_404`, `숫자가_아닌_분석_ID는_404`, `guest_token없이_게스트_분석을_조회하면_403`, `잘못된_guest_token으로_조회하면_403`, `claim_후_옛_guest_token으로_조회하면_403`, `PDF가_아닌_파일을_제출하면_400`, `이력서_파일과_ID가_모두_없으면_400`, `job_position이_없으면_400`, `토큰이_부족하면_400`, `진행_중_분석이_있으면_400`, `게스트가_면접을_시작하려_하면_401`, `잘못된_state_파라미터는_400`.

강제 규칙:
- 응답에 있는 모든 필드를 `responseFields`에 나열해야 한다(누락 시 `SnippetException`). `non_null` 정책상 상태별로 페이로드가 달라 **한 identifier로 전 상태를 덮을 수 없다.**
- `jd_fit`은 `.optional()`로 뭉개지 말고 **identifier 2개로 분리**해 JD 유무 각각의 실제 페이로드를 문서화한다(D4를 문서에서 드러내는 유일한 방법).
- multipart 비파일 파트는 `.file("job_career", "신입".getBytes())` 형식(`.param(...)` 아님).
- `pathParameters`를 쓰는 요청은 `RestDocumentationRequestBuilders` + `{analysisId}` 플레이스홀더.
- 인증은 `MockHttpSession` + `setAttribute("MEMBER_ID", ...)` + `.header("Cookie", "JSESSIONID=" + session.getId())` + `.session(session)` 3중 세팅.
- **게스트 엔드포인트는 `DocsTest`로 문서화하지 않는다.** `DocsTest`는 `RedisCleaner`를 호출하지 않으면서 같은 test-redis(16379)를 쓰므로 365일 락이 영구 잔류한다. `BaseControllerTest`에서만 문서화한다.

**편집은 append가 아니라 삭제 + 교체다** (741줄 → 약 600줄). 미해결 `include::`는 asciidoctor 기본 `failureLevel`에서 빌드를 실패시키지 않고 "Unresolved directive"를 인라인하므로 **빠뜨리면 조용히 망가진 문서가 배포된다**(`bootJar`가 `asciidoctor`에 의존).

**삭제 3구간 — 큰 라인번호부터 (실측 앵커 확인)**

| 삭제 범위 | 내용 | 경계 |
|---|---|---|
| **674–741** | 구 이력서 평가 7절 (`=== 이력서 평가 비동기 제출 …` ~ EOF) | `== 이력서`, `=== 이력서 & 포트폴리오 반환`은 존치 |
| **617–663** | `== 채용 공고` 섹션 전체 7절 | `== 이력서` 존치 |
| **134–207** | 구 이력서 기반 면접 8절 | `== 인터뷰`와 그 뒤 공백은 존치, `=== 비회원 인터뷰 시작`도 존치 |

결과: `== 인터뷰`의 첫 항목이 `=== 비회원 인터뷰 시작`, `== 이력서`는 1절만 남고 **파일 마지막 줄이 `include::{snippetsDir}/resume-getCareerMaterials/curl-request.adoc[]`** 이 된다. 신규 `== 이력서 분석` 16절은 그 뒤(파일 끝)에 append하고, 면접 시작 2절은 **`== 인터뷰` 섹션 말미**에 넣는다(P4). **행 번호로 위치를 지정하지 말고 앵커로 확인한다.**

**삭제 identifier 22개 / 존치 1개**

| 그룹 | identifier | 소멸 경로 |
|---|---|---|
| 구 이력서 기반 면접 8 | `resume-based-interview-submit-question-generation-with-{file,resume-id,portfolio}`, `resume-based-interview-{check,get-generated-questions}`, `resume-based-interview-start-{text,voice}-mode`, `resume-question-generation-list` | `ResumeBasedInterviewControllerTest` 삭제 |
| 구 이력서 평가 7 | `resume-evaluation-async-submit`, `resume-evaluation-state-{pending,completed}`, `resume-evaluation-{history,detail}`, `resume-evaluation-saved-async-submit`, `resume-evaluation-saved-async-submit-without-portfolio` | `CareerMaterialsControllerTest` 테스트 7개 삭제 |
| recruit 7 | `recruit-filters`, `recruit-list`, `recruit-list-filter-{region,multiple,career}`, `recruit-list-pagination`, `recruit-list-empty` | `RecruitControllerTest` 삭제 |
| **존치 1** | `resume-getCareerMaterials` | `GET /api/v1/resumes` 존치 |

구 이력서/면접 엔드포인트 10개(recruit 2개 제외)에 identifier가 15개인 이유: 평가 API가 케이스별 다중 identifier를 갖고, 구 `usage-status`는 **애초에 문서화되지 않았다**. 신규는 처음부터 `resume-analysis-usage-status`로 문서화해 같은 누락을 반복하지 않는다.

아래는 신규 `== 이력서 분석` 섹션의 append 예시다:

```asciidoc
== 이력서 분석

=== 이력서 분석 제출 (JD 포함)

include::{snippetsDir}/resume-analysis-submit-member-with-file/http-request.adoc[]
include::{snippetsDir}/resume-analysis-submit-member-with-file/request-headers.adoc[]
include::{snippetsDir}/resume-analysis-submit-member-with-file/request-parts.adoc[]
include::{snippetsDir}/resume-analysis-submit-member-with-file/http-response.adoc[]
include::{snippetsDir}/resume-analysis-submit-member-with-file/response-body.adoc[]
include::{snippetsDir}/resume-analysis-submit-member-with-file/response-fields.adoc[]
include::{snippetsDir}/resume-analysis-submit-member-with-file/curl-request.adoc[]
```

(18개 identifier 반복. `request-parts` 자리를 항목별로 `path-parameters.adoc`/`query-parameters.adoc`/`request-fields.adoc`로 교체.) `asciidoctor`에 `failure-level`이 없어 include 오타는 빌드 실패 없이 문서 공백으로 남으므로, **`./gradlew asciidoctor` 후 `build/docs/asciidoc/index.html`에서 18개 섹션 렌더를 눈으로 확인**하는 절차를 PR 체크리스트에 넣는다.

### 8-7. 픽스처

**`ResumeAnalysisFixtureBuilder`** (`global/fixture/resume/`)

```java
public class ResumeAnalysisFixtureBuilder {

    public static ResumeAnalysisFixtureBuilder builder();

    // 소유자 — 둘 중 하나만. 둘 다 지정하면 build()에서 IllegalStateException
    public ResumeAnalysisFixtureBuilder member(Member member);
    public ResumeAnalysisFixtureBuilder guest(String guestToken, String guestIp);
    public ResumeAnalysisFixtureBuilder guest();                    // token=UUID, ip="11.22.33.99"

    public ResumeAnalysisFixtureBuilder resume(MemberResume resume);         // guest()면 무시
    public ResumeAnalysisFixtureBuilder portfolio(MemberPortfolio portfolio);
    public ResumeAnalysisFixtureBuilder jobPosition(String jobPosition);
    public ResumeAnalysisFixtureBuilder jobDescription(String jobDescription);   // null = JD 없음
    public ResumeAnalysisFixtureBuilder jobCareer(String jobCareer);
    public ResumeAnalysisFixtureBuilder billingRequired(boolean billingRequired);

    public ResumeAnalysisFixtureBuilder state(ResumeAnalysisState state);   // 무인자 플래그가 아니라 enum
    public ResumeAnalysisFixtureBuilder failureReason(ResumeAnalysisFailureReason reason);
    public ResumeAnalysisFixtureBuilder questionRetryCount(int questionRetryCount);

    public ResumeAnalysisFixtureBuilder problemSolving(DimensionScore dimension);
    public ResumeAnalysisFixtureBuilder projectExperience(DimensionScore dimension);
    public ResumeAnalysisFixtureBuilder technicalSkills(DimensionScore dimension);
    public ResumeAnalysisFixtureBuilder softSkills(DimensionScore dimension);
    public ResumeAnalysisFixtureBuilder jdFit(DimensionScore dimension);
    public ResumeAnalysisFixtureBuilder allDimensions(int score);

    public ResumeAnalysisFixtureBuilder totalScore(Integer totalScore);      // 미지정 시 실제 계산
    public ResumeAnalysisFixtureBuilder totalFeedback(String totalFeedback);

    public ResumeAnalysis build();
}

public final class DimensionScoreFixture {
    public static DimensionScore of(int score);                                    // 근거/보완 각 2개 기본
    public static DimensionScore of(int score, String reason, String improvement);
    public static DimensionScore of(int score, List<String> reason, List<String> improvements);
}
```

```java
public ResumeAnalysis build() {
    ResumeAnalysis analysis = (member != null)
            ? ResumeAnalysis.forMember(member, resume, portfolio, jobInput(), billingRequired)
            : ResumeAnalysis.forGuest(guestToken(), new ClientIp(guestIp()), guestLockValue(), jobInput());

    if (state == ResumeAnalysisState.PENDING) {
        return analysis;
    }
    if (state == ResumeAnalysisState.EVALUATION_FAILED) {
        analysis.failEvaluation(failureReasonOrDefault(EVALUATION_LLM));
        return analysis;
    }
    analysis.completeEvaluation(buildEvaluation());          // ← 인자 1개
    if (state == ResumeAnalysisState.QUESTION_FAILED) {
        analysis.failQuestions(failureReasonOrDefault(QUESTION_LLM));
    } else if (state == ResumeAnalysisState.COMPLETED) {
        analysis.completeQuestions();
    }
    return analysis;
}

private ResumeAnalysisEvaluation buildEvaluation() {
    DimensionScore jd = (jobDescription == null)
            ? null : (jdFit != null ? jdFit : DimensionScoreFixture.of(70));
    ResumeAnalysisWeights weights = (jd == null) ? JD_ABSENT : JD_PROVIDED;
    ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
            orDefault(problemSolving, 90), orDefault(projectExperience, 80),
            orDefault(technicalSkills, 70), orDefault(softSkills, 60), jd, null, totalFeedback());
    return evaluation.withTotalScore(
            totalScore != null ? totalScore : weights.calculateTotalScore(evaluation));
}
```

설계 규칙:
- **기본값은 "JD 없음"** — D4의 까다로운 경로가 zero-config 기본이 되어 테스트가 실수로 JD 있음 경로만 덮는 것을 막는다.
- `jdFit`은 `jobDescription`에 **연동**되어 자동 결정된다 → "JD 없는데 jd_fit이 채워진" 불가능한 픽스처를 만들 수 없다.
- `totalScore` 미지정 시 실제 가중치로 계산 → 픽스처와 프로덕션의 드리프트가 생기지 않는다(기존 `totalScore=81` 하드코딩이 만든 문제의 반대).
- 상태 전이는 전부 엔티티 API를 통과 → 불가능 상태의 픽스처 생성 불가.
- `ResumeAnalysisSourceTextFixtureBuilder`, `GeneratedQuestionForAnalysisFixtureBuilder`(+ `static List<GeneratedQuestion> five(ResumeAnalysis)`)를 함께 추가한다. **기존 픽스처 4종은 무수정.**

### 8-8. 해피패스 LLM 응답 픽스처

레포에 이력서 평가/질문 LLM 응답 픽스처가 0개다. `BedrockResponseFixtureBuilder`는 면접용이며 평면 문자열만 다루고 `Document.fromList` 사용 예가 없다.

| 클래스 (`global/fixture/resume/`) | 산출물 | 용도 |
|---|---|---|
| `ResumeAnalysisEvaluationFixture` | `ResumeAnalysisEvaluation` | 클라이언트 레벨 목(L1) |
| `ResumeAnalysisQuestionResultFixture` | `ResumeAnalysisQuestionResult` | L1 |
| `ResumeAnalysisConverseResponseFixtureBuilder` | AWS `ConverseResponse` | SDK 레벨 목(L2) |
| `ResumeAnalysisGptResponseFixtureBuilder` | `String` (tool_calls arguments) | GPT 폴백 목 |

```java
public ConverseResponse buildEvaluation(boolean jdProvided) {
    Map<String, Document> input = new LinkedHashMap<>();
    putDimension(input, "problem_solving", 90);
    putDimension(input, "project_experience", 80);
    putDimension(input, "technical_skills", 70);
    putDimension(input, "soft_skills", 60);
    if (jdProvided) {
        putDimension(input, "jd_fit", 50);
    }
    input.put("total_feedback", Document.fromString("종합 총평"));
    return toolUseResponse(ResumeAnalysisToolNames.EVALUATION, input);
}

private void putDimension(Map<String, Document> input, String key, int score) {
    input.put(key + "_reasoning", Document.fromString("사고 과정"));
    input.put(key + "_score", Document.fromNumber(score));
    input.put(key + "_reason", Document.fromList(List.of(
            Document.fromString("근거1"), Document.fromString("근거2"))));
    input.put(key + "_improvements", Document.fromList(List.of(
            Document.fromString("보완1"), Document.fromString("보완2"))));
}

public ConverseResponse buildQuestions() {
    List<Document> questions = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> Document.fromMap(Map.of(
                    "question", Document.fromString("질문 " + i),
                    "reason", Document.fromString("이유 " + i))))
            .toList();
    return toolUseResponse(ResumeAnalysisToolNames.QUESTION_GENERATION,
            Map.of("questions", Document.fromList(questions)));
}

private ConverseResponse toolUseResponse(String toolName, Map<String, Document> input) {
    return ConverseResponse.builder()
            .stopReason(StopReason.TOOL_USE)          // extractToolUse가 이 값과 도구명으로 판별한다
            .output(ConverseOutput.builder()
                    .message(Message.builder()
                            .role(ConversationRole.ASSISTANT)
                            .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                    .toolUseId("tool-use-1")
                                    .name(toolName)
                                    .input(Document.fromMap(input))
                                    .build()))
                            .build())
                    .build())
            .build();
}
```

GPT 픽스처는 `String`이다(`ResumeEvaluationGptClient.requestResumeEvaluation`·`ResumeBasedQuestionGptClient.generateQuestions`가 모두 String 반환). `parseGptResponse`가 이중 인코딩을 벗기므로 `buildEvaluationArguments(boolean)`, `buildEvaluationDoubleEncoded(boolean)`, `buildQuestionsArguments()` 3종을 제공한다.

**스터빙 레벨 3종**

| 레벨 | 목 대상 | 스터빙 | 검증 범위 |
|---|---|---|---|
| L1 | 신규 클라이언트 4개(`BaseTest` `@MockitoBean`) | `given(evaluationBedrockClient.evaluate(any())).willReturn(...)`, `given(questionBedrockClient.generateQuestions(any())).willReturn(...)` — **서로 다른 메서드라 순차 반환 스터빙 불필요** | 상태머신, DB, 과금, 게스트, claim |
| L2 | `BedrockConverseClient`를 **실물로 생성**하고 `BedrockRuntimeClient`만 목 | `given(bedrockRuntimeClient.converse(any())).willReturn(fixture.buildEvaluation(true)).willReturn(fixture.buildQuestions())` — 연속 `willReturn`이 2콜 순서를 표현 | `extractToolUse`/`parseToolInput`/`appendCachePoint`가 실제 코드로 동작. `ArgumentCaptor<ConverseRequest>`로 modelId·system·messages·toolConfig·maxTokens·temperature 전부 검증 |
| L3 | L1에서 Bedrock 클라이언트만 예외 | `willThrow(new ExternalApiException(...))` + GPT 픽스처 | 폴백 1회, 파싱, 상태 기록 |

```java
BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
BedrockConverseProperties properties = new BedrockConverseProperties(
        "test-model-id", 2048, 4096, 1024, 2048, 10000, 0.2f, 0.7f, 0.5f);
BedrockConverseClient converseClient =
        new BedrockConverseClient(bedrockRuntimeClient, properties, new ObjectMapper());
```

**`BedrockConverseClient`를 `mock()`으로 만들면 안 된다** — `extractToolUse`/`parseToolInput`도 목이 되어 픽스처가 무의미해진다. 반드시 SDK 레벨을 목으로 잡는다.

### 8-9. `BaseTest` 목 교체 (13+2=15 → 15+2=17)

**삭제 5개** (구 클래스 소멸로 함께 사라짐)

| 필드 | 타입 |
|---|---|
| `resumeEvaluationBedrockClient` | `ResumeEvaluationBedrockClient` |
| `resumeEvaluationGptClient` | `ResumeEvaluationGptClient` |
| `resumeBasedQuestionGptClient` | `ResumeBasedQuestionGptClient` |
| `resumeBasedQuestionBedrockService` | `ResumeBasedQuestionBedrockService` |
| `questionGenerationAsyncService` | `QuestionGenerationAsyncService` |

import 5개도 함께 삭제한다.

**추가 5개**

| # | 필드 | 타입 |
|---|---|---|
| 1 | `resumeAnalysisEvaluationBedrockClient` | `resume.external.ResumeAnalysisEvaluationBedrockClient` |
| 2 | `resumeAnalysisEvaluationGptClient` | `resume.external.ResumeAnalysisEvaluationGptClient` |
| 3 | `resumeAnalysisQuestionBedrockClient` | `resume.external.ResumeAnalysisQuestionBedrockClient` |
| 4 | `resumeAnalysisQuestionGptClient` | `resume.external.ResumeAnalysisQuestionGptClient` |
| 5 | `resumeAnalysisAsyncService` | `resume.service.ResumeAnalysisAsyncService` (제출 API 테스트를 비동기 타이밍에서 분리 — `questionGenerationAsyncService` 선례) |

**승격 2개 (P2):** `PdfValidator`, `PdfTextExtractor`. **존치 8개:** `supertoneClient`, `s3Client`, `tosspaymentsClient`, `interviewProceedGptClient`, `interviewProceedBedrockClient`, `answerFeedbackBedrockClient`, `kakaoOAuthClient`, `googleOAuthClient` + 스파이 2개(`redisTemplate`, `redissonClient`).

**승격 근거.** `PdfValidator`/`PdfTextExtractor`를 `BaseTest`로 승격한다 — 원판의 비승격 판정을 뒤집는다.

| 근거 | 판정 |
|---|---|
| ① "기존 두 컨트롤러 테스트의 로컬 선언을 삭제해야 함(D2 위반)" | **소멸.** `ResumeBasedInterviewControllerTest`는 파일째 삭제되고, `CareerMaterialsControllerTest`의 잔존 테스트(`멤버_이력서_반환()`)는 두 타입을 쓰지 않는다 |
| ② "모든 통합 테스트에서 `PdfTextExtractor`가 null 반환 목이 되어 실제 추출에 의존하는 `ResumeContentService` 경로 테스트가 죽는다" | **사실이 아니다.** `ResumeContentService`가 `s3Service.downloadFileFromUrl` → `S3Service` → `S3Client`를 타고, `S3Client`는 이미 `BaseTest`의 목이다. 전 테스트 트리에서 바이트를 반환하는 `s3Client` 스텁은 0건. 저장-자료 경로가 통합 테스트에서 성립하는 유일한 방법은 `MemberResumeFixtureBuilder.content(...)`이고, 그 경우 `ResumeContentService`의 early return으로 **추출기가 호출되지 않는다** |
| ③ 컨텍스트 캐시 키 fork | **중립.** 승격 후 신규 2테스트가 `ResumeAnalysisPdfPolicy` 1개만 로컬 선언 → fork 1. 비승격 + `CareerMaterialsControllerTest` 2줄 삭제 → 신규 2테스트가 3개 선언 → fork 1. 동일 |
| ④ 죽은 코드 | 비승격 시 `CareerMaterialsControllerTest`에 아무 테스트도 쓰지 않는 목 선언 2줄이 남는다 |

승격에 따른 필수 후속: `CareerMaterialsControllerTest`의 로컬 `@MockitoBean` 2개 + import 2개를 삭제한다(동일 타입 로컬 재정의는 컨텍스트 캐시 키를 갈라 fork를 늘린다). 스텁을 빠뜨린 테스트는 신규 파사드의 null/공백 필터에 걸려 **400으로 실패**하므로 조용히 통과하지 않는다.

`BedrockConverseClient`/`BedrockRuntimeClient`는 `BaseTest`에 넣지 않는다(배선 검증은 Spring 없는 `ResumeAnalysisWiringTest`에서 직접 주입 → 컨텍스트 fork 0).

`@MockitoSpyBean redisTemplate`/`redissonClient`는 그대로 사용한다 — 게스트 락 검증은 실제 Redis에 쓰고 `redisTemplate.hasKey(...)`로 읽는다.

### 8-10. 게스트 락과 테스트 격리 (실측 근거)

| 사실 | 근거 |
|---|---|
| `BaseTest.@BeforeEach`가 `redisCleaner.clearAllRedisData()` 호출 | `BaseTest` 실측 |
| `RedisCleaner`는 `redisTemplate.keys("*")` + `delete(keys)` | `RedisCleaner` 전문 |
| 그 `RedisTemplate`은 key/value 직렬화가 모두 `StringRedisSerializer` | `RedisSingleNodeConfig.redisTemplate` |
| `RedisService`는 Redisson `getBucket(key, StringCodec.INSTANCE)`로 평문 키 사용 | `RedisService` 전문 |
| ⇒ **365일 TTL 게스트 락도 매 테스트 전에 삭제된다** | 위 3개의 결합 |

따라서 신규 테스트도 락을 테스트 안에서 직접 심는다:

```java
redisService.acquireLock(
        ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX + guestIp,
        ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_TTL);
```

**리터럴 금지, 상수 참조만 허용**한다 — 프로덕션이 `started:`로 걸고 테스트가 `submitted:`로 시드하면 키가 충돌하지 않아 `같은_IP의_게스트가_두_번_제출하면_예외가_발생한다`가 2회 제출 모두 성공하는데도 초록으로 통과한다.

**IP 격리**: 게스트 테스트마다 다른 IP를 쓰고 `11.22.33.4x` 대역을 피한다(`InterviewControllerTest`가 `.44`/`.45`를 점유). `ClientIpArgumentResolver`는 헤더 부재 시 `"0.0.0.0"`을 쓰므로 **게스트 테스트는 반드시 `X-Forwarded-For`를 명시**한다(빼먹은 테스트끼리 `0.0.0.0` 락을 공유해 서로를 깨뜨린다).

`test-redis`는 named volume을 쓰므로 런 중단 시 락이 다음 런까지 살지만 첫 `@BeforeEach`가 전량 삭제해 무해하다. 수동 초기화는 `docker compose -f test.yml down -v` 또는 `docker exec test-redis redis-cli FLUSHALL`.

### 8-11. 마이그레이션 검증

| 프로파일 | DB | Flyway | V51 실행 |
|---|---|---|---|
| `test` | MySQL 8.4.5 @13306, `ddl-auto: none` | 설정 미지정 → Boot 기본값으로 **활성** | **실행된다.** 통합 테스트 하나만 돌려도 V51이 실제 MySQL에서 검증된다 |
| `docs` | H2 2.2.224, `ddl-auto: create-drop` | `enabled: false` | 실행되지 않는다. 스키마는 Hibernate가 엔티티에서 생성 |

`src/test/resources/application.yml`이 메인 `application.yml`을 가리므로 `application-common.yml`(`out-of-order:false` 등)은 테스트에서 로드되지 않는다. 테스트 Flyway는 Boot/Flyway 11.9.1 기본값으로 동작하고 빈 스키마에서 V1부터 순차 적용되므로 baseline 없이 정상이다.

구체적인 검증 게이트(G1~G5)와 명령은 구현 계획 문서 §6에서 관리한다(§6-C로 이관). 이 절에는 여전히 유효한 항목만 남긴다.

| 검증 항목 | 방법 |
|---|---|
| V51 복원 상태 확인 | §3-8 절차. P1(로컬 재기동 0회) 전제가 성립하는지 착수 전 판정 쿼리로 확인 |
| H2 호환성(`JSON`/`TEXT`/`LONGTEXT`, `@Table(name)`, `id` 컬럼) | 신규 엔티티 커밋 직후 `./gradlew test --tests "com.samhap.kokomen.interview.docs.*"`를 게이트로 실행(Docker 불필요, 가장 빠른 스키마 스모크) |
| V51 SQL ↔ 엔티티 매핑 일치 | `test` 프로파일은 `ddl-auto: none`이고 스키마 검증도 하지 않으므로 컬럼명 오타는 **쿼리 실행 시점**에 터진다 → §8-5의 `JD포함`/`JD미포함` 두 케이스가 15개 차원 컬럼 전부에 write/read를 발생시켜야 한다 |
| `CHECK` 제약 | MySQL 8.0.16+ 필요(테스트 컨테이너 8.4.5 확인). 운영 버전은 §10 |
| V53 퍼지 스크립트 실행 검증 | G5(§6). 로컬에 구 데이터가 없어 Flyway 경로만으로는 검증되지 않는 문제의 대응 |

---

## 9. 구현 순서

**구현 순서는 구현 계획 문서(§Z)가 정본이다.** 이 설계 문서는 순서를 규정하지 않는다 — 삭제·가산이 뒤섞인 22개 이상의 세부 태스크를 단일 표로 유지하면 태스크 경계가 바뀔 때마다 두 문서가 갈리기 쉽다. 대신 아래 3가지 원칙만 남긴다.

1. **프롬프트 상수 이전(§4-2)이 구 코드 삭제보다 반드시 앞선다** — 뒤집으면 `ResumeAnalysisSystemMessages`가 컴파일되지 않고, 급하게 상수를 다시 만드는 과정에서 프롬프트가 바뀔 위험이 생긴다.
2. **엔티티 M3 변경(§3-4)과 마이그레이션 M3 변경(V54)은 같은 커밋이어야 한다** — `docs`는 엔티티에서, `test`는 마이그레이션에서 스키마를 만들므로 갈리면 한쪽만 조용히 통과한다.
3. **삭제 커밋 내부에서는 컴파일이 깨진다.** 정상이며 커밋 경계에서만 초록이어야 한다.

---

## 10. 남은 확인 필요 항목

### 10-A. 인간 판정 필요 (착수 전 결정)

| # | 항목 | 선택지 | 권고 | 근거 / 종속 |
|---|---|---|---|---|
| **X-1** | `ocr_waiting_list` 처리 (§3-2) | A: 테이블째 DROP / B: `fk_ocr_recruit` + `recruit_id` 컬럼만 제거하고 테이블 존치 | **A** (SQL·게이트를 A로 확정해 두었다) | Java 엔티티·리포지토리·픽스처 0건. `recruit_id`가 `NOT NULL`(실측)이라 B는 신규 INSERT가 불가한 반쪽 테이블을 남긴다. **M5 목록의 집계 누락이며 M5의 결정이 아니다.** B를 택하면 §3-2 1단계 교체 + §0-1 기대 테이블 총수 20→21, 두 곳만 고친다 |
| **X-2** | `member.score` 표류 (§3-4) | A: 무보정 / B: V53에 대상 확정 + 재계산 `UPDATE` / C: 별도 운영 스크립트 | **판정 필요** (SQL은 A로 확정 배포 가능) | `addScore` 호출처 1곳(`InterviewProceedService`, `member != null` 가드 포함)이라 재계산이 정확하다. **B의 전제:** §3-4의 사전 검증 쿼리가 0행. **B의 대가:** `CREATE TEMPORARY TABLE`이 DDL이라 V53이 "순수 DML"이 아니게 되어 파일 선두 주석을 정정해야 한다. 규모는 §3-9-E `members_score_affected` |
| **X-3** | 무료 1회 재부여 (§7-3) | A: 수용 (구 유료 사용 회원 전원에게 무료 1회 재부여) / B: 이력 계승 테이블 / C: 첫 사용 무료 폐지 | **A** | B는 M1과 정면 충돌(§11-D). A의 비용은 5토큰 × 대상 회원 수의 일회성이며, 규모는 §3-9-E `members_regaining_free_use`로 확정한다. **A를 택해도 코드 주석에 "판정 완료"라고 쓰지 않는다** — 판정 시점의 근거를 명시한다 |
| **X-4** | 배포 순서 (§1-4) | 1: 동시 배포 (M1~M5 원안) / 2: 프론트 선행 + 구 API 호출 0건 관측 후 삭제 배포 | **1** | 2를 택하면 ①단계가 곧 하위호환이고 nullable `analysis_id` + XOR CHECK의 과도기 마이그레이션이 1개 늘어나 **M3의 예외를 만드는 결정**이 된다. 2를 택할 경우 관측 기간(모바일 앱·캐시된 프론트 번들 고려)도 함께 판정한다 |
| **X-5** | 과거 이력 소멸 고지 | 고지 / 무고지 | **고지** | 이력서 분석 결과와 이력서 기반 면접 기록이 사라진다. 고지 없이 지우면 문의가 발생한다 |
| **X-6** | 루트의 `1 역량별 평가 세부항목.md` | A: `docs/`로 이동 / B: 루트 존치 / C: 스테이징 취소 | — | 입력 자료. 레포 루트에 공백·숫자 접두 파일명을 남길지의 판단 |
| **X-7** | 10MB 초과 업로드의 500 응답 | A: 이번 범위에 포함(400으로) / B: 미포함 | — | `GlobalExceptionHandler`에 `MaxUploadSizeExceededException` 핸들러 추가. **D1 폐기로 반대 근거가 소멸**했고 남은 영향면은 `POST /api/v1/resume-analyses`와 `PdfUploadService` 경유 업로드뿐이다 |
| **X-8** | `ResumeAnalysisEvaluationResultRenderer`의 `total_score` 포맷 | A: `withTotalScore(int)` 선행 호출을 계약으로 못박음 / B: `%s`로 변경 | — | `Integer`(nullable) + `%d`이므로 null이면 `total_score=null`이 프롬프트에 출력된다 |
| **X-9** | `charged_token_count` 타입 비대칭 | A: 현행 유지 / B: `columnDefinition = "SMALLINT"` | **A** | MySQL `SMALLINT`(≤32767) vs H2 `integer`, 엔티티는 `Integer`. 32767 초과는 **MySQL만** 거절하지만 도메인 범위에서 도달 불가 |
| **X-10** | `ResumeContentService` 위치 | A: `interview/service/resume/`에 단독 잔존 / B: `resume/service/`로 이동 | — | 이동하면 신규 파사드의 참조 경로도 함께 고친다. 미룰 경우 디렉터리에 파일 1개가 남는다. **M1~M5 범위 밖 결정** |

**이미 확정한 항목 (재논의 불필요):** V51의 `technical_skills_reason` 줄 JSON 키워드 정렬 1칸 어긋남 = 영구 보존(§3-2), `ResumeAnalysisPdfPolicy` 별 클래스 유지, `ResumeContentService` 추출 경로 비대칭 유지, `spring.task.scheduling.pool.size: 3` 유지, 브랜치 머지 전략 = `feat/new` 일괄 머지(삭제 선행 배치가 최적).

### 10-B. 선행 과제 / 미확인 사실

| # | 항목 | 상태 |
|---|---|---|
| 1 | **`SPRING_FLYWAY_TARGET` 단계 적용의 실효성** | `build.gradle`에 Flyway Gradle 플러그인이 **없다**(실측: `flyway-core`/`flyway-mysql` 11.9.1 라이브러리만). `./gradlew flywayMigrate`는 존재하지 않는 태스크다. Spring 프로퍼티 경로가 유일하며 **배포를 두 번 해야 한다** — 배포 인프라가 환경변수 주입 + 2회 릴리스를 지원하는지 확인 필요 |
| 2 | **운영/dev MySQL 버전** | `CHECK` 강제에 8.0.16+ 필요. 미만이면 `chk_resume_analysis_owner`/`chk_resume_analysis_scores`/`chk_generated_question_parent`가 파싱만 되고 무시된다 |
| 3 | **dev/prod `flyway_schema_history`의 최고 version** | `develop` 기준 **50**으로 확정(§3-9-A). dev 환경에서 51 이상이 이미 적용돼 있다면 **V51~V54 네 개를 동시에 시프트**해야 한다 |
| 4 | **`X-Forwarded-For` 재작성 여부** | ALB/Nginx가 신뢰 경계에서 덮어쓰지 않고 append만 하면 게스트 IP 락을 무제한 우회할 수 있다. 신규 API가 유일한 게스트 경로가 되어 노출면이 커진다 |
| 5 | **배포 플랫폼의 grace period** | `awaitTerminationSeconds(60)`보다 짧으면 in-flight 태스크가 SIGKILL로 죽는다 |
| 6 | **`SET SESSION TRANSACTION ISOLATION LEVEL`의 Flyway 트랜잭션 내 유효성** | MySQL 문서상 "진행 중인 트랜잭션에는 영향 없음". 무효라면 §3-9-G의 통제는 유지보수 창 + `innodb_lock_wait_timeout`뿐이다 |
| 7 | **Flyway가 실패한 마이그레이션의 커넥션을 재사용하는지** | §3-10 변형의 `CREATE TEMPORARY TABLE IF NOT EXISTS` + 선행 `DELETE` 방어의 근거 |
| 8 | **`PdfValidator`/`PdfTextExtractor` 승격 후 실제 추출을 요구하는 잔존 테스트** | 실측상 0건일 것으로 판단되나(`S3Client`가 목, 바이트 반환 스텁 0건), 최종 회귀(`./gradlew clean build`) 결과로 확정한다. 남으면 그 테스트만 명시 스텁으로 전환 |
| 9 | **게스트 보존 30일 vs 락 TTL 365일** | 제품 정책 |
| 10 | **`SOURCE_TEXT_RETENTION_DAYS = 30`** | 만료 후 `question_retryable`이 false가 되는 것이 제품 정책과 맞는지 |
| 11 | **신규 평가 콜의 출력 길이** | `resume-evaluation-max-tokens: 10000` 도달 시 `OUTPUT_TRUNCATED` → GPT 폴백. 실측 후 조정 판정(프로퍼티 신설 시 test yml 동시 수정 필수) |
| 12 | **`GeneratedQuestionTest`의 `MemberFixtureBuilder` import** | 삭제되는 테스트가 단독 사용인지, private 헬퍼도 쓰는지 확인 후 import 삭제 여부 결정 |

### 10-C. 주요 파일 절대경로

- 원장 — `/Users/osang0731/IdeaProjects/kokomen-backend/.superpowers/sdd/2026-07-29-resume-analysis-merge/progress.md`
- 이 설계 문서 — `/Users/osang0731/IdeaProjects/kokomen-backend/docs/superpowers/specs/2026-07-29-resume-analysis-merge-design.md`
- 구현 계획 — `/Users/osang0731/IdeaProjects/kokomen-backend/docs/superpowers/plans/2026-07-29-resume-analysis-merge.md`
- 버려야 할 파일 — `/Users/osang0731/IdeaProjects/kokomen-backend/src/main/resources/db/migration/V51__rename_resume_evaluation_to_resume_analysis.sql`
- 복원 명령 — `git show d1eae65:src/main/resources/db/migration/V51__create_resume_analysis.sql`
- 신설 마이그레이션 3개 — `src/main/resources/db/migration/V52__drop_recruit_domain.sql`, `V53__purge_resume_based_interviews.sql`, `V54__repoint_generated_question_and_drop_legacy_resume_tables.sql`
- 신설 테스트 (G5) — `src/test/java/com/samhap/kokomen/global/migration/ResumeBasedPurgeScriptTest.java`
- 프로파일 비대칭 근거 — `src/test/resources/application.yml`, `src/test/resources/application-common-test.yml`

---

## 11. 이번 전환으로 해소된/우선도 상승/잔존 findings와 의도적으로 채택하지 않은 지적

### 11-A. 자동 해소 (조치 불필요 — 결함 조건 자체가 소멸)

| finding | 소멸 근거 |
|---|---|
| Task 4 parked Important — 프롬프트 ~3.3KB 바이트 동일 복사, 소스로의 기계적 연결 없음 | 구 `ResumePromptFragments` 삭제로 **원본이 존재하지 않아** "연결 없음"이라는 조건이 소멸하고 복사본이 유일본이 된다 |
| Task 3 deferred — `chk_generated_question_parent` XOR 제약의 DB 레벨 검증 테스트 없음 | **최종 스키마에 제약이 없다**(V54가 제거). `analysis_id NOT NULL` + `@JoinColumn(nullable=false)`이 DB·엔티티 양쪽에서 강제하고 위반 경로가 타입 시스템에 존재하지 않는다 |
| 코드 복제 3건 — `bulletArraySchema`/`buildToolConfig`, `nullToEmpty`, `SCORE_MIN/MAX`·`BULLET_MIN/MAX_ITEMS` | 원본(구 팩토리·`ResumeGptRequest`·`ResumeEvaluationSchema`) 삭제로 **유일본화**. 코드는 0바이트 수정이고 Javadoc만 고친다 |
| Task 3 관련 — nullable FK 2개 + XOR로 인한 설계 복잡도 | M3로 최종 스키마에서 소멸 |
| 구 `usage-status`가 문서화되지 않음 | 구 엔드포인트 삭제로 누락 자체가 소멸. 신규는 `resume-analysis-usage-status`로 처음부터 문서화 |
| §7-3의 "의도된 비대칭" — 신규 API를 먼저 쓴 회원이 레거시에서 무료 1회를 또 받는다 | 레거시 소멸로 해소 |
| `MethodArgumentTypeMismatchException` 전역 핸들러 미채택 근거 | 원 근거(D1)는 소멸했으나 **결론은 유지**: 존치 엔드포인트 다수(`interview`, `answer`, `member`, `payment`, `token`, `admin`)가 `@PathVariable Long`을 쓰므로 그 전부의 응답 코드가 바뀐다. 이력서 작업이 무관 도메인의 계약을 바꿀 이유가 없다 |

### 11-B. 이번 전환으로 우선도가 올라간 findings (병합 전 필수 수정)

| finding | 이유 | 조치 |
|---|---|---|
| `doesNotContain("지속적 학습")`이 구 원문의 부분 문자열이 아니다 | 구 프롬프트가 레포에서 사라진 뒤 **이 단정이 폐기 문구 재유입을 막는 유일한 방어선**이 된다 | `"지속적인 학습"`으로 교정(§4-1·§8-4 두 곳 모두) |
| 소프트스킬 단정에 `"갈등 해결"` 누락 | 동일 | 단정 인자에 추가(§8-4) |

### 11-C. 여전히 남는 findings (이번 범위에서 조치하지 않음)

| finding | 상태 |
|---|---|
| `ResumeAnalysisEvaluationResultRenderer`의 `total_score` 포맷 — `Integer`(nullable)인데 `%d`이므로 null이면 `total_score=null`이 프롬프트에 출력된다 | Task 4 deferred. §10 X-8 |
| `charged_token_count`의 H2/MySQL 타입 비대칭 | 도메인 범위에서 도달 불가. §10 X-9 |
| V51의 `technical_skills_reason` JSON 키워드 정렬 1칸 어긋남 | **영구 보존 확정** — 고치면 checksum이 바뀌어 재기동 0회와 Task 1 무변경 증명이 깨진다 |
| `X-Forwarded-For` 재작성 여부 | 미확인 → §10-B 4. 구 비회원 평가 API가 사라져 신규 API가 유일한 게스트 경로가 되므로 이 구멍의 노출면이 커진다 |
| GPT 폴백이 `max_tokens`를 전송하지 않음 — 잘림 시 Bedrock과 동작이 다르다 | 미조치 유지 |
| Bedrock Converse 캐시 프리픽스 구성 순서 | 지표 관측 후 재검토 |
| Flyway가 실패한 마이그레이션의 커넥션을 재사용하는지 | 미확인. §3-10 변형의 `IF NOT EXISTS` 방어의 근거 |
| `SET SESSION TRANSACTION ISOLATION LEVEL`이 Flyway 트랜잭션 내에서 유효한지 | 미확인. §3-9-G의 1차 통제를 배포 방식으로 둔 이유 |

### 11-D. 검토했으나 채택하지 않은 지적

- **[중] 마이그레이션을 단일 파일로 유지** — 약 32문장 중 14문장이 비가역이고 MySQL DDL이 암묵 커밋하므로, 20번째 문장에서 실패하면 반쯤 뜯긴 스키마 + `success = 0`이 남아 `flyway repair` 없이 재시도가 불가능하다. 4분할로 실패 지점을 문장 단위로 특정하고 `SPRING_FLYWAY_TARGET=53` 감사 지점을 만들었다(§3-2).
- **[중] V53의 `DELETE FROM interview`에 `OR generated_question_id IS NOT NULL`을 붙여 FK 실패를 예방** — M2가 정의한 삭제 범위를 넘어 다른 타입의 면접을 조용히 지운다. 그런 행이 있다면 6단계가 `ERROR 1451`로 죽는 것이 옳고, §3-9-D (8)이 미리 알려주며 G5의 대조군 단정이 회귀를 막는다.
- **[하] `GeneratedQuestion.forAnalysis`를 `of`로 개명하거나 public 생성자로 병합** — 부모가 하나가 되어 "판별" 기능은 사라지지만 존재 이유(영속화 직전 `abbreviate`)는 유효하다. 병합하면 생성자가 인자를 조용히 변형하는 형태가 되어 가독성이 떨어지고 호출자 diff만 늘어난다. 순증 가치 0.
- **[중] `MaxUploadSizeExceededException` 핸들러로 10MB 초과를 400으로** — **미채택에서 재판정 대상으로 이동**(§10 X-7). 원 근거(구 평가 업로드 API의 응답까지 바뀐다)가 D1 폐기로 소멸했다.
- **[중] 구 `resume_question_generation`의 `member_id` 목록을 보존 테이블로 옮겨 무료 1회 판정에 사용** — **M1(구 테이블 전부 DROP)과 정면 충돌**한다. 폐기 예정 데이터를 위해 영구 구조물을 만드는 것이므로 "과감한 정리"와 방향이 반대다(§10 X-3 B안 배제 근거).
- **[중] 게스트 락을 2단계 TTL(10분 → 365일)로 승격** — D12가 `acquireLockWithValue(..., Duration.ofDays(365))`를 명시했다. 대신 락 획득 시점을 추출 이후·INSERT 직전으로 옮겨 급사 창을 수십 초에서 수 ms로 줄이고, 획득 로그로 수동 `DEL` 런북을 성립시켰다.
- **[중] Redis 키 스캔 기반 고아 락 회수 스케줄러** — 30일 후 행이 삭제된 정상 소진 락과 고아 락을 구별할 수 없어(생성 시각을 알 수 없다) 정상 소진 락을 풀어 무료 1회를 재부여하는 새 결함을 만든다. 락 획득 시점 이동으로 대체했다.
- **[중] 목록 응답에서 `question_count`를 프로젝션에 포함** — `generated_question`은 별 테이블이라 조인 프로젝션이 필요하다. `countByAnalysisIdIn` 1회 병합으로 대체(N+1 없음).
- **[하] `charged_token_count`와 별개로 `token_charged BOOLEAN`을 둔다** — 같은 사실을 두 컬럼으로 표현하면 CAS 대상이 갈린다. `WHERE charged_token_count = 0` 하나로 통합했다.
- **[하] `failure_detail` 자유 텍스트 컬럼 추가** — enum + 로그로 충분하고, 컬럼 길이 초과가 실패 기록 자체를 롤백시키는 위험을 다시 들인다.
- **[하] `MAX_SWEEP_COUNT` 상한 도달 시 루프로 즉시 이어서 처리** — 5분 주기 × 200건 = 시간당 2,400건이면 대량 장애의 배수로 충분하다. 상한 도달을 `log.warn` + 메트릭으로 관측 가능하게만 했다.
- **[하] `EXTRACTION_SEMAPHORE`를 회원 경로에는 적용하지 않는다** — 인증된 회원도 같은 Tomcat 스레드를 쓰므로 동일하게 적용했다(지적을 확대 채택).
- **[하] `TEXT_EXTRACTION` 실패 원인 enum 값** — 추출 실패는 행 생성 전이라 저장될 수 없는 도달 불가 값이므로 enum에서 제거했다.
- **[하] `weight_percent` 컬럼을 감사용으로 남긴다** — "쓰기 전용, 어떤 계산에도 읽지 않는다"를 지켜도 가중치 정책 변경 시 과거 행과 enum이 갈려 어느 쪽이 정답인지 판정할 수 없다. 컬럼을 만들지 않는다.
- **[하] `{dim}_reasoning`을 파싱 DTO에 추가해 `reasoning` 컬럼에 보존** — 프롬프트 튜닝용 가치보다 저장 비용·폴링 비용·컬럼 15→20 증가가 크다. 필요해지면 로그로 남기는 것이 먼저다.