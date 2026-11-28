package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.INTERVIEW_NOT_FOUND;

import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerComposer;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementTurn;
import com.samhap.kokomen.token.service.TokenFacadeService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요구사항 역질문의 준비와 저장. LLM 호출은 두 트랜잭션 사이(트랜잭션 밖)에서 파사드가 한다. 준비는 토큰 보유를 확인하고, 저장은
 * 역질문 행을 저장한 뒤 토큰 {@value InterviewMode#REQUIREMENT_QUESTION_TOKEN_COST}개를 차감한다. LLM이 실패하거나 다시 보낸
 * 역질문이면 저장하지 않으므로 차감도 없다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignRequirementQuestionService {

    private static final int REQUIRED_TOKEN_COUNT = InterviewMode.TEXT
            .calculateRequiredTokenCountToAskRequirementQuestion(SystemDesignInterview.MAX_QUESTION_COUNT);

    private final SystemDesignInterviewService systemDesignInterviewService;
    private final SystemDesignInterviewRepository systemDesignInterviewRepository;
    private final SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    private final SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;
    private final TokenFacadeService tokenFacadeService;
    private final EntityManager entityManager;

    /**
     * 소유자(404·403) → 단계(409) → 질문 길이(400) → 직전 역질문 재시도 → 개수(400) → 토큰 보유(400) 순서로 검사하고 LLM 입력을
     * 값만으로 만든다. 토큰은 역질문 비용에 본 면접 답변 전부의 비용을 더한 만큼 있어야 한다
     * ({@link InterviewMode#calculateRequiredTokenCountToAskRequirementQuestion(int)}).
     * 진행 락을 잡기 전에 흔한 오류를 빨리 돌려주려고 한 번, 락 안에서 최신 상태로 한 번 더 부른다. 재시도인지는 락 안의 결과로 정한다.
     *
     * <p>직전 역질문(마지막 순번)과 다듬은 내용이 같고 그 역질문을 저장한 지
     * {@link SystemDesignRequirementQuestion#RETRY_WINDOW} 안이면, 응답을 받지 못해 다시 보낸 요청으로 보고 LLM 입력 대신 직전 행의
     * 응답을 담는다. 개수·토큰 검사보다 앞이라 5개를 다 쓴 뒤 5번째를 다시 보내거나, 토큰을 써서 보유가 기준 아래로 내려간 뒤
     * 다시 보내도 오류가 아니다. 더 앞의 역질문과 같거나 시간이 지났으면 새 역질문이다.
     */
    @Transactional(readOnly = true)
    public RequirementQuestionDraft prepare(Long interviewId, Long memberId, String rawQuestion) {
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(interviewId, memberId);
        systemDesignInterview.validateRequirementStage();
        String content = SystemDesignRequirementQuestion.normalizeContent(rawQuestion);
        List<SystemDesignRequirementQuestion> previousQuestions = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        Optional<SystemDesignRequirementQuestion> retriedQuestion = findRetriedQuestion(previousQuestions, content);
        if (retriedQuestion.isPresent()) {
            return RequirementQuestionDraft.retry(interviewId, memberId, content,
                    SystemDesignRequirementAnswerResponse.from(retriedQuestion.get()));
        }
        SystemDesignRequirementQuestion.validateCount(previousQuestions.size());
        tokenFacadeService.validateEnoughTokens(memberId, REQUIRED_TOKEN_COUNT);
        List<SystemDesignRequirementTurn> previousTurns = previousQuestions.stream()
                .map(question -> new SystemDesignRequirementTurn(question.getContent(), question.getAnswer()))
                .toList();
        SystemDesignRequirementAnswerCommand command = new SystemDesignRequirementAnswerCommand(interviewId,
                readProblemContext(systemDesignInterview.getProblem()), previousTurns, content);
        return RequirementQuestionDraft.newQuestion(interviewId, memberId, content, command);
    }

    private static Optional<SystemDesignRequirementQuestion> findRetriedQuestion(
            List<SystemDesignRequirementQuestion> previousQuestions, String content) {
        if (previousQuestions.isEmpty()) {
            return Optional.empty();
        }
        SystemDesignRequirementQuestion lastQuestion = previousQuestions.get(previousQuestions.size() - 1);
        if (lastQuestion.isRetriedBy(content, LocalDateTime.now())) {
            return Optional.of(lastQuestion);
        }
        return Optional.empty();
    }

    private SystemDesignProblemContext readProblemContext(SystemDesignProblem problem) {
        return SystemDesignProblemContext.of(problem,
                hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(problem),
                evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem));
    }

    /**
     * LLM 호出 뒤 단계와 개수를 다시 확인하고, 최종 답을 정해 역질문 행을 저장한 뒤 토큰을 차감한다. 차감 전에 실패하면 아무것도 남지
     * 않는다(차감 뒤 이 트랜잭션의 커밋 자체가 실패하는 경우는 chargeRequirementQuestionToken에 적었다).
     * 재시도로 준비한 역질문은 저장하지 않는다(파사드가 LLM을 부르지 않고 직전 응답을 그대로 준다).
     */
    @Transactional
    public SystemDesignRequirementAnswerResponse save(RequirementQuestionDraft draft,
                                                      SystemDesignRequirementAnswerResult result) {
        if (draft.isRetry()) {
            throw new IllegalArgumentException("다시 보낸 역질문은 저장하지 않습니다. interviewId: " + draft.interviewId());
        }
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository
                .findByInterviewId(draft.interviewId())
                .orElseThrow(() -> new NotFoundException(INTERVIEW_NOT_FOUND.getMessage()));
        systemDesignInterview.validateRequirementStage();
        int savedCount = Math.toIntExact(requirementQuestionRepository.countBySystemDesignInterview(
                systemDesignInterview));
        SystemDesignRequirementQuestion.validateCount(savedCount);
        String answer = SystemDesignRequirementAnswerComposer.compose(draft.command().problem().requirements(),
                result.askedRequirementCodes(), result.answer());
        SystemDesignRequirementQuestion requirementQuestion = requirementQuestionRepository.save(
                new SystemDesignRequirementQuestion(systemDesignInterview, savedCount + 1, draft.content(), answer,
                        result.askedRequirementCodes()));
        chargeRequirementQuestionToken(draft.memberId());
        return SystemDesignRequirementAnswerResponse.from(requirementQuestion);
    }

    /**
     * useTokens는 REQUIRES_NEW라 호출하는 즉시 따로 커밋되어, 이후 이 트랜잭션이 롤백돼도 돌아오지 않는다. 그래서 역질문 저장을 flush로
     * 먼저 실행해 저장에 실패한 역질문에는 과금하지 않는다. 토큰이 모자라 차감이 실패하면 저장한 역질문도 함께 롤백된다.
     * 이 트랜잭션은 회원 행을 고치지 않으므로 답변 차감(SystemDesignAnswerService)의 회원 행 락 순서 문제가 없다.
     *
     * <p>차감이 먼저 커밋되므로, 그 뒤 이 트랜잭션의 커밋이 실패하면(DB 연결 끊김 등) 역질문은 남지 않고 토큰만 빠진다. 답변 차감과
     * 같은 한계다. 또 7개 보유는 준비할 때 한 번 보고 차감은 1개만 확인하므로, LLM을 기다리는 동안 진행 락 밖의 다른 차감(이력서
     * 분석, 기존 음성 면접의 음성 비용)이 토큰을 쓰면 차감 뒤 6개보다 적게 남을 수 있다. 시작할 때의 6개 확인도 같다.
     */
    private void chargeRequirementQuestionToken(Long memberId) {
        entityManager.flush();
        tokenFacadeService.useTokens(memberId, InterviewMode.REQUIREMENT_QUESTION_TOKEN_COST);
    }

    /**
     * 준비한 역질문. content는 정규화한 역질문이고 memberId는 토큰을 차감할 면접자다. 새 역질문이면 command(LLM 입력)만, 응답을 받지
     * 못해 다시 보낸 직전 역질문이면 retriedResponse(직전 행의 응답)만 있다.
     */
    public record RequirementQuestionDraft(
            Long interviewId,
            Long memberId,
            String content,
            SystemDesignRequirementAnswerCommand command,
            SystemDesignRequirementAnswerResponse retriedResponse
    ) {

        public RequirementQuestionDraft {
            if ((command == null) == (retriedResponse == null)) {
                throw new IllegalArgumentException("새 역질문의 LLM 입력과 다시 보낸 역질문의 응답 중 하나만 있어야 합니다.");
            }
        }

        public static RequirementQuestionDraft newQuestion(Long interviewId, Long memberId, String content,
                                                           SystemDesignRequirementAnswerCommand command) {
            return new RequirementQuestionDraft(interviewId, memberId, content, command, null);
        }

        public static RequirementQuestionDraft retry(Long interviewId, Long memberId, String content,
                                                     SystemDesignRequirementAnswerResponse retriedResponse) {
            return new RequirementQuestionDraft(interviewId, memberId, content, null, retriedResponse);
        }

        public boolean isRetry() {
            return retriedResponse != null;
        }
    }
}
