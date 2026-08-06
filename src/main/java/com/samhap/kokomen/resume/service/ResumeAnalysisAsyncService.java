package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import com.samhap.kokomen.resume.external.ResumeAnalysisEvaluationBedrockClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisQuestionBedrockClient;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisEvaluationResultRenderer;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * resumeAnalysisExecutor에 제출되는 단일 태스크. 평가 콜과 질문 콜을 같은 스레드에서 순차 실행한다.
 * hop마다 태스크를 다시 제출하면 hop 간 예외 전파가 끊기고, 뒤 hop이 rejection되면 행이 영구 PENDING에 남는다.
 *
 * <p>두 hop을 private가 아니라 패키지 가시성으로 두는 근거는 runQuestionHop이 평가는 성공했지만 질문만 실패한
 * 행의 재시도 진입점이라는 것이다 — 재시도는 평가 콜을 다시 태우지 않아야 하므로 run()을 거치지 않고
 * 들어와야 한다. 그 진입점인 ResumeAnalysisFacadeService.retryQuestionGeneration이 같은 패키지에 있으므로
 * 패키지 가시성으로 충분하고 public까지 열지 않는다. runEvaluationHop을 부르는 프로덕션 코드는 run()뿐이며,
 * 이쪽 가시성은 테스트가 평가 hop만 떼어 검증할 수 있게 하려는 것이다.
 *
 * <p>테스트가 hop을 직접 호출하는 것은 2콜 순차 종단을 폴링 없이 결정적으로 확인하려는 선택이지,
 * 다른 방법이 없어서가 아니다. awaitility가 테스트 클래스패스에 있으므로 run()으로 제출하고 종단 상태를
 * 기다리는 것도 가능하다.
 *
 * <p>어느 지점에서 태스크가 버려져도 행이 워커의 메모리 상태에만 의존해 갇히지 않아야 한다.
 * 상태 전이는 전부 ResumeAnalysisStateService의 조건부 전이를 거치고, 남은 행은 스케줄러가 종단 처리한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeAnalysisAsyncService {

    private static final String TRUNCATED_RESPONSE_MARKER = "tool_use가 아닙니다";

    /**
     * 일시적 예외에만 1회 재시도하고, 결정적으로 재실패하는 예외는 즉시 종단한다.
     */
    private static final int PERSISTENCE_RETRY_LIMIT = 1;

    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeAnalysisStateService resumeAnalysisStateService;
    private final ResumeAnalysisEvaluationBedrockClient evaluationBedrockClient;
    private final ResumeAnalysisQuestionBedrockClient questionBedrockClient;

    public void run(ResumeAnalysisCommand command) {
        ResumeAnalysisEvaluation evaluation = runEvaluationHop(command);
        if (evaluation != null) {
            runQuestionHop(command, evaluation);
        }
    }

    /**
     * 평가 콜 → 평가 커밋 → 과금까지 수행한다. 실패하거나 상태 가드에 걸려 결과를 폐기하면 null을 반환해
     * 호출자가 질문 콜을 실행하지 않게 한다.
     *
     * <p>평가 커밋은 question_started_at도 함께 세팅한다. 이 컬럼 없이 created_at으로 질문 단계를 판정하면
     * 평가에 오래 걸린 정상 요청이 질문 콜 도중 실패로 찍히고, 한참 뒤의 사용자 재시도도 즉시 스윕 대상이 되어
     * 정상 생성한 질문이 상태 가드에 폐기되면서 재시도 횟수만 소모된다.
     */
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

    /**
     * 응답이 잘려 tool_use가 아니었던 경우와 그 밖의 호출 실패를 failure_reason으로 사후 분리한다.
     * 잘림은 프롬프트·maxTokens 조정 대상이고 나머지는 재시도·용량 대상이므로 같은 값으로 묶으면 안 된다.
     */
    private ResumeAnalysisFailureReason classifyEvaluationFailure(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(TRUNCATED_RESPONSE_MARKER)) {
                return ResumeAnalysisFailureReason.OUTPUT_TRUNCATED;
            }
        }
        return ResumeAnalysisFailureReason.EVALUATION_LLM;
    }

    private boolean completeEvaluationWithRetry(Long analysisId, ResumeAnalysisEvaluation evaluation) {
        for (int attempt = 0; ; attempt++) {
            try {
                return resumeAnalysisStateService.completeEvaluation(analysisId, evaluation);
            } catch (RuntimeException e) {
                if (attempt >= PERSISTENCE_RETRY_LIMIT || !isTransientPersistenceFailure(e)) {
                    throw e;
                }
                log.warn("이력서 분석 평가 저장 일시 실패, 재시도 - analysisId: {}, attempt: {}, exception: {}",
                        analysisId, attempt + 1, e.getClass().getName());
            }
        }
    }

    /**
     * 락 획득 실패·데드락 패배·직렬화 실패는 다시 시도하면 성공할 수 있으므로 재시도 대상이다. 세 경우 모두
     * PessimisticLockingFailureException의 하위 타입이고, 상태 전이가 PESSIMISTIC_WRITE로 잠그므로
     * JPA가 이 상위 타입 자체로 번역해 던지는 경우도 함께 걸러야 한다.
     * 반면 같은 데이터를 다시 넣으면 결정적으로 재실패하는 DataIntegrityViolationException은 NonTransient
     * 계열이라 이 판정에서 빠진다 — 무제한으로 catch해 종단시키면 락 경합 한 번에 정상 요청이 실패하고,
     * 반대로 무제한 재시도하면 워커 스레드를 붙잡고 실패 상태 기록마저 늦춘다.
     * Throwable.getCause()는 cause == this면 null을 반환하므로 이 순회는 자기참조로 무한 루프에 빠지지 않는다.
     */
    private boolean isTransientPersistenceFailure(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }

    /**
     * 평가 결과를 메모리로 직접 받아 질문 콜 user 메시지의 evaluation_result로 렌더해 주입한다.
     * jdProvided는 커맨드 값만 쓴다 — jobDescription 문자열로 재계산하면 4지표로 채점한 응답을 5지표 가중치로
     * 합산하는 경로가 열린다.
     *
     * <p>finally의 회수 과금은 평가가 공개된 뒤의 모든 종단 지점에서 과금을 한 번 더 시도하는 것이다.
     * chargeTokensIfNeeded는 CAS 멱등이라 중복 차감이 없고, 평가 직후의 과금이 예외로 끊긴 채 질문만 성공한 행이
     * 무료로 끝나는 경로를 막는다. 재시도 경로는 billingMemberId가 null이므로 무과금 규약이 유지된다.
     *
     * <p>그 회수 과금은 상태를 다시 확인하지 않고 실행되므로, 평가가 공개되지 않은 행으로 이 메서드에 들어오면
     * 사용자가 결과 없이 과금될 수 있다. 그래서 (a) 가시성을 패키지로 좁혀 이 패키지 밖에서는 호출할 수 없게 하고,
     * (b) 평가 결과를 필수 인자로 요구한다. non-null 평가를 얻는 경로는 커밋에 성공한 runEvaluationHop의 반환값과
     * 평가 공개 여부를 스스로 검사하는 ResumeAnalysisService.readEvaluation뿐이므로, 정상 경로로는
     * 평가 이전 상태의 행을 여기까지 가져올 수 없다.
     */
    void runQuestionHop(ResumeAnalysisCommand command, ResumeAnalysisEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "질문 hop은 커밋된 평가 결과 없이 실행할 수 없습니다.");
        try {
            proceedQuestionHop(command, evaluation);
        } finally {
            resumeAnalysisStateService.chargeTokensIfNeeded(command.analysisId(), command.billingMemberId());
        }
    }

    private void proceedQuestionHop(ResumeAnalysisCommand command, ResumeAnalysisEvaluation evaluation) {
        ResumeAnalysisQuestionCallCommand questionCommand = ResumeAnalysisQuestionCallCommand.of(command,
                ResumeAnalysisEvaluationResultRenderer.render(evaluation, command.jdProvided()));
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
        try {
            if (!completeQuestionsWithRetry(command.analysisId(), questions)) {
                log.warn("이력서 분석 질문 결과가 상태 가드로 폐기됨 - analysisId: {}", command.analysisId());
            }
        } catch (RuntimeException e) {
            log.error("이력서 분석 질문 저장 실패 - analysisId: {}, exception: {}",
                    command.analysisId(), e.getClass().getName(), e);
            resumeAnalysisStateService.failQuestions(
                    command.analysisId(), ResumeAnalysisFailureReason.PERSISTENCE);
        }
    }

    private boolean completeQuestionsWithRetry(Long analysisId, List<GeneratedQuestionDto> questions) {
        for (int attempt = 0; ; attempt++) {
            try {
                return resumeAnalysisStateService.completeQuestions(analysisId, questions);
            } catch (RuntimeException e) {
                if (attempt >= PERSISTENCE_RETRY_LIMIT || !isTransientPersistenceFailure(e)) {
                    throw e;
                }
                log.warn("이력서 분석 질문 저장 일시 실패, 재시도 - analysisId: {}, attempt: {}, exception: {}",
                        analysisId, attempt + 1, e.getClass().getName());
            }
        }
    }

    /**
     * 원문 사이드 테이블과 부모 행에서 커맨드를 복원한다. 재추출·S3 재다운로드가 없다.
     * billingMemberId는 항상 null이다 — 질문 재시도는 무과금이고 이미 차감된 토큰은 그대로 유지된다.
     */
    public ResumeAnalysisCommand readCommand(Long analysisId) {
        ResumeAnalysis analysis = resumeAnalysisService.readById(analysisId);
        ResumeAnalysisSourceText sourceText = resumeAnalysisService.readSourceText(analysisId);
        return new ResumeAnalysisCommand(analysis.getId(), null, analysis.isJdProvided(),
                sourceText.getResumeContent(), sourceText.getPortfolioContent(),
                analysis.getJobPosition(), analysis.getJobDescription(), analysis.getJobCareer());
    }
}
