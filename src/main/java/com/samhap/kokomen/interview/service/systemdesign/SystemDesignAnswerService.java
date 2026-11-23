package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.QUESTION_NOT_FOUND;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationResult;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignReportScore;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationResultRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerCompletedResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerPendingResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerResultResponse;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerTurn;
import com.samhap.kokomen.interview.tool.SystemDesignEndCommand;
import com.samhap.kokomen.interview.tool.SystemDesignNextBaseQuestion;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementTurn;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설계 답변의 검사·준비·저장과 채점 결과 조회. LLM 호출은 준비와 저장 사이(트랜잭션 밖)에서 워커가 한다.
 * 토큰은 답변 저장에 성공한 뒤에만 1개 차감한다. 실패한 제출에는 차감이 없으므로 돌려줄 토큰도 없다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignAnswerService {

    private final SystemDesignInterviewService systemDesignInterviewService;
    private final SystemDesignQuestionRepository questionRepository;
    private final SystemDesignAnswerRepository answerRepository;
    private final SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    private final SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;
    private final SystemDesignEvaluationResultRepository evaluationResultRepository;
    private final SystemDesignProblemQuestionRepository problemQuestionRepository;
    private final SystemDesignProceedStateStore stateStore;
    private final TokenFacadeService tokenFacadeService;
    private final MemberService memberService;
    private final EntityManager entityManager;

    /**
     * 진행 락을 잡기 전에 흔한 오류를 빨리 돌려준다. 소유자(404·403) → 끝난 면접 → 요구사항 단계 → 질문 순번(409 세 가지) 순서다.
     * 락 안에서 {@link #prepare}가 같은 검사를 다시 한다.
     *
     * @return 지금 답할 질문의 순번
     */
    @Transactional(readOnly = true)
    public int precheck(Long interviewId, Long memberId, Integer questionOrder) {
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(interviewId, memberId);
        return readAnswerableQuestion(systemDesignInterview, readDesignDialogue(systemDesignInterview), questionOrder)
                .getQuestionOrder();
    }

    /**
     * 진행 락 안에서 {@link #precheck}의 검사를 최신 상태로 다시 하고, 채점 입력을 값만으로 만든다(트랜잭션 밖에서 LLM을 불러도
     * 지연 로딩이 일어나지 않는다). 마지막 질문이면 마지막 답변 평가 입력(L3), 아니면 답변 평가 입력(L2)을 만든다.
     *
     * @param content 다듬어 길이를 검사한 답변
     * @param diagram 검사를 마친 다이어그램
     */
    @Transactional(readOnly = true)
    public SystemDesignAnswerDraft prepare(Long interviewId, Long memberId, Integer questionOrder, String content,
                                           SystemDesignDiagram diagram) {
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(interviewId, memberId);
        DesignDialogue dialogue = readDesignDialogue(systemDesignInterview);
        SystemDesignQuestion question = readAnswerableQuestion(systemDesignInterview, dialogue, questionOrder);
        SystemDesignProblem problem = systemDesignInterview.getProblem();
        SystemDesignProblemContext problemContext = SystemDesignProblemContext.of(problem,
                hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(problem),
                evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem));
        List<SystemDesignRequirementQuestion> requirementQuestions = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        List<SystemDesignRequirementTurn> requirementTurns = requirementQuestions.stream()
                .map(requirementQuestion -> new SystemDesignRequirementTurn(requirementQuestion.getContent(),
                        requirementQuestion.getAnswer()))
                .toList();
        List<SystemDesignAnswerTurn> previousTurns = dialogue.turnsBefore(question);
        SystemDesignAnswerTurn currentTurn = new SystemDesignAnswerTurn(question.getQuestionOrder(),
                question.getStage(), question.getContent(), content);
        if (isLastQuestion(question)) {
            SystemDesignEndCommand endCommand = new SystemDesignEndCommand(interviewId, problemContext,
                    requirementTurns, previousTurns, currentTurn, diagram,
                    collectConfirmedRequirementCodes(requirementQuestions));
            return new SystemDesignAnswerDraft(interviewId, memberId, question.getQuestionOrder(), content, diagram,
                    null, endCommand);
        }
        SystemDesignProblemQuestion nextBaseQuestion = readProblemQuestion(problem, question.getQuestionOrder() + 1);
        SystemDesignAnswerCommand answerCommand = new SystemDesignAnswerCommand(interviewId, problemContext,
                requirementTurns, previousTurns, currentTurn, diagram,
                new SystemDesignNextBaseQuestion(nextBaseQuestion.getQuestionOrder(), nextBaseQuestion.getStage(),
                        nextBaseQuestion.getContent()));
        return new SystemDesignAnswerDraft(interviewId, memberId, question.getQuestionOrder(), content, diagram,
                answerCommand, null);
    }

    // 면접자가 확인한 숨은 요구사항: 모든 역질문에서 물은 요구사항 code의 합집합(처음 나온 순서)
    private static List<String> collectConfirmedRequirementCodes(
            List<SystemDesignRequirementQuestion> requirementQuestions) {
        return requirementQuestions.stream()
                .flatMap(requirementQuestion -> requirementQuestion.getAskedRequirementCodes().stream())
                .distinct()
                .toList();
    }

    /**
     * 1~5번째 답변을 저장하고 토큰 1개를 차감한다. 준비한 뒤 LLM을 부르는 동안 상태가 바뀌었을 수 있어 답할 수 있는지 다시 본다.
     * 답변 → 다음 질문(LLM 문장, 없으면 기준 질문) → 단계·현재 다이어그램 → flush → 차감 순서라, 어디서 실패하든 모두 롤백되고
     * 토큰은 그대로다.
     */
    @Transactional
    public void saveAnswer(SystemDesignAnswerDraft draft, SystemDesignAnswerEvaluation evaluation) {
        if (draft.isLast()) {
            throw new IllegalArgumentException("마지막 답변은 saveLastAnswer로 저장합니다. questionOrder: "
                    + draft.questionOrder());
        }
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(draft.interviewId(), draft.memberId());
        SystemDesignQuestion question = readAnswerableQuestion(systemDesignInterview,
                readDesignDialogue(systemDesignInterview), draft.questionOrder());
        answerRepository.save(new SystemDesignAnswer(question, draft.content(), draft.diagram(), evaluation.rank(),
                evaluation.feedback()));
        int nextQuestionOrder = question.getQuestionOrder() + 1;
        SystemDesignProblemQuestion nextBaseQuestion = readProblemQuestion(systemDesignInterview.getProblem(),
                nextQuestionOrder);
        String nextQuestionContent = evaluation.nextQuestion() != null
                ? evaluation.nextQuestion() : nextBaseQuestion.getContent();
        questionRepository.save(new SystemDesignQuestion(systemDesignInterview, nextBaseQuestion,
                nextQuestionContent));
        systemDesignInterview.recordAnswer(draft.diagram(), SystemDesignStage.ofQuestionOrder(nextQuestionOrder));
        chargeAnswerToken(draft.memberId());
    }

    /**
     * 마지막 답변을 저장하고 면접을 끝낸 뒤 토큰 1개를 차감한다. 평가 포인트 판정, 리포트 점수, 종합 피드백, 등급 점수 합을 같은
     * 트랜잭션에 저장하므로 COMPLETED가 보이면 리포트를 바로 조립할 수 있다. 회원 점수는 차감 뒤에 더하고, 그 뒤에는 아무것도 하지 않는다.
     */
    @Transactional
    public void saveLastAnswer(SystemDesignAnswerDraft draft, SystemDesignEndEvaluation evaluation) {
        if (!draft.isLast()) {
            throw new IllegalArgumentException("마지막 답변이 아닙니다. questionOrder: " + draft.questionOrder());
        }
        Member member = memberService.readById(draft.memberId());
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(draft.interviewId(), draft.memberId());
        DesignDialogue dialogue = readDesignDialogue(systemDesignInterview);
        SystemDesignQuestion question = readAnswerableQuestion(systemDesignInterview, dialogue, draft.questionOrder());
        answerRepository.save(new SystemDesignAnswer(question, draft.content(), draft.diagram(), evaluation.rank(),
                evaluation.feedback()));
        systemDesignInterview.recordAnswer(draft.diagram(), null);
        List<SystemDesignEvaluationPoint> points = evaluationPointRepository
                .findByProblemOrderByDisplayOrderAsc(systemDesignInterview.getProblem());
        int metCount = saveEvaluationResults(systemDesignInterview, points, evaluation.metByPointCode());
        List<AnswerRank> ranks = dialogue.ranksWith(question, evaluation.rank());
        systemDesignInterview.finish(SystemDesignReportScore.calculate(metCount, points.size(), ranks));
        int totalScore = ranks.stream()
                .mapToInt(AnswerRank::getScore)
                .sum();
        systemDesignInterview.getInterview().evaluate(evaluation.totalFeedback(), totalScore);
        chargeAnswerToken(draft.memberId());
        // 회원 UPDATE가 차감 전 flush에 들어가지 않도록 점수는 차감 뒤에 더한다. 이유는 chargeAnswerToken에 적었다.
        member.addScore(totalScore);
    }

    // 문제의 평가 포인트 순서로 판정을 저장하고 충족한 수를 돌려준다. 판정이 빠진 포인트가 있으면 아무것도 저장하지 않고 실패한다.
    private int saveEvaluationResults(SystemDesignInterview systemDesignInterview,
                                      List<SystemDesignEvaluationPoint> points, Map<String, Boolean> metByPointCode) {
        List<SystemDesignEvaluationResult> results = points.stream()
                .map(point -> new SystemDesignEvaluationResult(systemDesignInterview, point,
                        readVerdict(metByPointCode, point)))
                .toList();
        evaluationResultRepository.saveAll(results);
        return Math.toIntExact(results.stream()
                .filter(SystemDesignEvaluationResult::isMet)
                .count());
    }

    private static boolean readVerdict(Map<String, Boolean> metByPointCode, SystemDesignEvaluationPoint point) {
        Boolean met = metByPointCode.get(point.getCode());
        if (met == null) {
            throw new IllegalStateException("평가 포인트 판정이 없습니다. code: " + point.getCode());
        }
        return met;
    }

    /**
     * useTokens는 REQUIRES_NEW라 호출하는 즉시 따로 커밋되어, 이후 이 트랜잭션이 롤백돼도 돌아오지 않는다. 그래서 저장을 모두
     * flush로 먼저 실행해 저장에 실패한 답변에는 과금하지 않는다. 토큰이 모자라 차감이 실패하면 저장한 답변도 함께 롤백된다.
     *
     * <p>이 flush에 회원 UPDATE가 들어가면 안 된다. flush한 행의 락은 커밋 때까지 남는데, 차감은 그동안 토큰 락을 기다린다.
     * 토큰 구매는 토큰 락을 쥔 채 token_purchase INSERT의 FK 검사로 회원 행 S 락을 기다리므로, 회원 행 X 락을 쥔 채 토큰 락을
     * 기다리면 두 쪽이 서로를 기다리다 토큰 락 대기 시간이 지나 차감이 실패한다(InterviewProceedService와 같은 순서).
     */
    private void chargeAnswerToken(Long memberId) {
        entityManager.flush();
        tokenFacadeService.useTokens(memberId, InterviewMode.ANSWER_TOKEN_COST);
    }

    /**
     * 그 질문에 답변이 저장돼 있으면 Redis 값과 상관없이 COMPLETED와 그 답변을 처리한 결과를 준다. 워커는 저장을 커밋한 뒤에
     * Redis를 바꾸므로 DB가 먼저다. 답변이 없으면 Redis의 채점 상태를 본다.
     *
     * @throws NotFoundException 그 순번의 질문이 아직 없다(순번이 null인 경우 포함)
     */
    @Transactional(readOnly = true)
    public SystemDesignAnswerResultResponse findAnswerResult(Long interviewId, Long memberId, Integer questionOrder) {
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(interviewId, memberId);
        if (questionOrder == null) {
            throw new NotFoundException(QUESTION_NOT_FOUND.getMessage());
        }
        SystemDesignQuestion question = questionRepository
                .findBySystemDesignInterviewAndQuestionOrder(systemDesignInterview, questionOrder)
                .orElseThrow(() -> new NotFoundException(QUESTION_NOT_FOUND.getMessage()));
        Optional<SystemDesignAnswer> answer = answerRepository.findByQuestion(question);
        if (answer.isPresent()) {
            return createCompletedResponse(systemDesignInterview, question, answer.get());
        }
        return new SystemDesignAnswerPendingResponse(
                toPendingOrFailed(stateStore.find(interviewId, question.getQuestionOrder())));
    }

    // 1~5번째 답변의 결과는 그 답변을 저장할 때 함께 저장한 다음 질문이다. 마지막 답변이면 면접이 끝났고 다음 질문이 없다.
    private SystemDesignAnswerCompletedResponse createCompletedResponse(SystemDesignInterview systemDesignInterview,
                                                                        SystemDesignQuestion question,
                                                                        SystemDesignAnswer answer) {
        if (isLastQuestion(question)) {
            return SystemDesignAnswerCompletedResponse.finished(question, answer);
        }
        SystemDesignQuestion nextQuestion = questionRepository
                .findBySystemDesignInterviewAndQuestionOrder(systemDesignInterview, question.getQuestionOrder() + 1)
                .orElseThrow(() -> new IllegalStateException("답변이 저장된 질문의 다음 질문이 없습니다. interviewId: "
                        + systemDesignInterview.getInterview().getId() + ", questionOrder: "
                        + question.getQuestionOrder()));
        return SystemDesignAnswerCompletedResponse.inProgress(answer, nextQuestion);
    }

    /**
     * 답변이 없는 질문의 상태. 채점 중이면 LLM_PENDING이고, 실패했거나 기록이 없으면(제출하지 않았거나 TTL이 지났다) LLM_FAILED다.
     * 워커는 저장을 커밋한 뒤 COMPLETED를 쓰므로, 답변을 읽은 뒤 Redis를 읽기 전에 워커가 끝나면 답변 없이 COMPLETED가 보인다.
     * 이때는 실패로 보내지 않고 LLM_PENDING으로 보내 다음 폴링이 저장된 답변을 읽게 한다.
     */
    private static InterviewProceedState toPendingOrFailed(Optional<InterviewProceedState> state) {
        if (state.isPresent() && (state.get() == InterviewProceedState.LLM_PENDING
                || state.get() == InterviewProceedState.COMPLETED)) {
            return InterviewProceedState.LLM_PENDING;
        }
        return InterviewProceedState.LLM_FAILED;
    }

    private DesignDialogue readDesignDialogue(SystemDesignInterview systemDesignInterview) {
        List<SystemDesignQuestion> questions =
                questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        Map<Long, SystemDesignAnswer> answersByQuestionId = answerRepository.findByQuestionIn(questions).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity()));
        return new DesignDialogue(questions, answersByQuestionId);
    }

    // 끝난 면접 → 요구사항 단계 → 질문 순번 순서로 검사하고 지금 답할 질문을 돌려준다.
    private static SystemDesignQuestion readAnswerableQuestion(SystemDesignInterview systemDesignInterview,
                                                               DesignDialogue dialogue, Integer questionOrder) {
        Optional<SystemDesignQuestion> currentQuestion = systemDesignInterview.findCurrentQuestion(
                dialogue.questions(), dialogue.answeredQuestionIds());
        systemDesignInterview.validateAnswerable(questionOrder,
                currentQuestion.map(SystemDesignQuestion::getQuestionOrder).orElse(null));
        return currentQuestion.orElseThrow();
    }

    private static boolean isLastQuestion(SystemDesignQuestion question) {
        return question.getQuestionOrder() == SystemDesignInterview.MAX_QUESTION_COUNT;
    }

    private SystemDesignProblemQuestion readProblemQuestion(SystemDesignProblem problem, int questionOrder) {
        return problemQuestionRepository.findByProblemAndQuestionOrder(problem, questionOrder)
                .orElseThrow(() -> new IllegalStateException("시스템 설계 문제에 %d번 기준 질문이 없습니다. problemId: %d"
                        .formatted(questionOrder, problem.getId())));
    }

    /**
     * 락 안에서 준비한 답변. content는 다듬은 답변, diagram은 검사를 마친 다이어그램이고 questionOrder는 답하는 질문의 순번이다.
     * 1~5번째 답변이면 answerCommand(L2 입력)만, 마지막 답변이면 endCommand(L3 입력)만 있다.
     * command에는 숨은 요구사항의 답까지 들어 있으므로 toString은 식별 값만 남긴다.
     */
    public record SystemDesignAnswerDraft(
            Long interviewId,
            Long memberId,
            int questionOrder,
            String content,
            SystemDesignDiagram diagram,
            SystemDesignAnswerCommand answerCommand,
            SystemDesignEndCommand endCommand
    ) {

        public SystemDesignAnswerDraft {
            if ((answerCommand == null) == (endCommand == null)) {
                throw new IllegalArgumentException("답변 평가 입력과 마지막 답변 평가 입력 중 하나만 있어야 합니다.");
            }
        }

        public boolean isLast() {
            return endCommand != null;
        }

        @Override
        public String toString() {
            return "SystemDesignAnswerDraft[interviewId=" + interviewId + ", memberId=" + memberId
                    + ", questionOrder=" + questionOrder + ", last=" + isLast() + "]";
        }
    }

    /**
     * 이 면접의 설계 질문(순번 순서)과 질문 PK별 답변.
     */
    private record DesignDialogue(
            List<SystemDesignQuestion> questions,
            Map<Long, SystemDesignAnswer> answersByQuestionId
    ) {

        private Set<Long> answeredQuestionIds() {
            return answersByQuestionId.keySet();
        }

        // 이번 질문 앞의 질문과 답변(질문 순서). 저장된 질문 문장(기준 질문이거나 LLM이 다듬은 문장)을 그대로 쓴다.
        private List<SystemDesignAnswerTurn> turnsBefore(SystemDesignQuestion currentQuestion) {
            return questions.stream()
                    .filter(question -> question.getQuestionOrder() < currentQuestion.getQuestionOrder())
                    .map(question -> new SystemDesignAnswerTurn(question.getQuestionOrder(), question.getStage(),
                            question.getContent(), readAnswer(question).getContent()))
                    .toList();
        }

        // 질문 순서대로 저장된 답변의 등급, 이번 질문 자리에는 이번 답변의 등급
        private List<AnswerRank> ranksWith(SystemDesignQuestion currentQuestion, AnswerRank currentRank) {
            return questions.stream()
                    .map(question -> question.getId().equals(currentQuestion.getId())
                            ? currentRank : readAnswer(question).getAnswerRank())
                    .toList();
        }

        private SystemDesignAnswer readAnswer(SystemDesignQuestion question) {
            SystemDesignAnswer answer = answersByQuestionId.get(question.getId());
            if (answer == null) {
                throw new IllegalStateException("현재 질문 앞의 질문에 답변이 없습니다. questionOrder: "
                        + question.getQuestionOrder());
            }
            return answer;
        }
    }
}
