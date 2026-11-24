package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.INTERVIEW_NOT_FOUND;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.NOT_OWNER;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.PROBLEM_UNAVAILABLE;

import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignQuestionAndAnswerResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class SystemDesignInterviewService {

    private static final SystemDesignDifficulty START_DIFFICULTY = SystemDesignDifficulty.BASIC;
    private static final int FIRST_QUESTION_ORDER = 1;
    private static final String NO_CUR_ANSWER_PROCEED_STATE = "NONE";

    private final TokenFacadeService tokenFacadeService;
    private final MemberService memberService;
    private final InterviewRepository interviewRepository;
    private final SystemDesignInterviewRepository systemDesignInterviewRepository;
    private final SystemDesignProblemRepository problemRepository;
    private final SystemDesignProblemQuestionRepository problemQuestionRepository;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;
    private final SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    private final SystemDesignQuestionRepository questionRepository;
    private final SystemDesignAnswerRepository answerRepository;
    private final SystemDesignProceedStateStore stateStore;

    /**
     * 6문항을 모두 답할 토큰이 있는지만 확인하고 차감하지 않는다. 토큰은 답변이 저장될 때마다 1개씩 차감한다.
     */
    @Transactional
    public SystemDesignInterviewStartResponse start(Long memberId) {
        tokenFacadeService.validateEnoughTokens(memberId,
                InterviewMode.TEXT.calculateRequiredTokenCountToStart(SystemDesignInterview.MAX_QUESTION_COUNT));
        Member member = memberService.readById(memberId);
        SystemDesignProblem problem = pickProblem();
        validateProblemLayout(problem);
        Interview interview = interviewRepository.save(Interview.forSystemDesign(member));
        systemDesignInterviewRepository.save(SystemDesignInterview.start(interview, problem));
        return new SystemDesignInterviewStartResponse(interview.getId());
    }

    private SystemDesignProblem pickProblem() {
        List<SystemDesignProblem> problems = problemRepository.findByDifficultyAndState(START_DIFFICULTY,
                SystemDesignProblemState.ACTIVE);
        if (problems.isEmpty()) {
            log.error("출제할 시스템 설계 문제가 없습니다. difficulty: {}, state: {}", START_DIFFICULTY,
                    SystemDesignProblemState.ACTIVE);
            throw new InternalServerErrorException(PROBLEM_UNAVAILABLE.getMessage());
        }
        return problems.get(ThreadLocalRandom.current().nextInt(problems.size()));
    }

    // 기준 질문 배치나 평가 포인트가 어긋난 문제는 끝까지 진행하거나 채점할 수 없으므로 시드 오류로 보고 시작하지 않는다.
    private void validateProblemLayout(SystemDesignProblem problem) {
        List<SystemDesignProblemQuestion> questions =
                problemQuestionRepository.findByProblemOrderByQuestionOrderAsc(problem);
        boolean hasEvaluationPoint = !evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem).isEmpty();
        if (!hasValidQuestionLayout(questions) || !hasEvaluationPoint) {
            log.error("시스템 설계 문제의 기준 질문이나 평가 포인트가 잘못됐습니다. problemId: {}, code: {}, "
                            + "questionCount: {}, hasEvaluationPoint: {}", problem.getId(), problem.getCode(),
                    questions.size(), hasEvaluationPoint);
            throw new InternalServerErrorException(PROBLEM_UNAVAILABLE.getMessage());
        }
    }

    // 순번 1~MAX_QUESTION_COUNT가 하나씩 있고 순번마다 단계가 SystemDesignStage.ofQuestionOrder와 같아야 한다.
    private static boolean hasValidQuestionLayout(List<SystemDesignProblemQuestion> questions) {
        if (questions.size() != SystemDesignInterview.MAX_QUESTION_COUNT) {
            return false;
        }
        for (int index = 0; index < questions.size(); index++) {
            int expectedOrder = index + 1;
            SystemDesignProblemQuestion question = questions.get(index);
            if (question.getQuestionOrder() != expectedOrder
                    || question.getStage() != SystemDesignStage.ofQuestionOrder(expectedOrder)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 시스템 설계 면접을 소유자 확인과 함께 읽는다. system_design_interview 행이 없는 면접 id(기존 면접 포함)는 404, 다른 회원의 면접은 403이다.
     */
    @Transactional(readOnly = true)
    public SystemDesignInterview readOwnedInterview(Long interviewId, Long memberId) {
        SystemDesignInterview systemDesignInterview = readInterview(interviewId);
        if (!systemDesignInterview.getInterview().isInterviewee(memberId)) {
            throw new ForbiddenException(NOT_OWNER.getMessage());
        }
        return systemDesignInterview;
    }

    /**
     * 시스템 설계 면접을 소유자 확인 없이 읽는다(공개 리포트). system_design_interview 행이 없는 면접 id(기존 면접·비회원 체험 면접 포함)는
     * 404다.
     */
    @Transactional(readOnly = true)
    public SystemDesignInterview readInterview(Long interviewId) {
        return systemDesignInterviewRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new NotFoundException(INTERVIEW_NOT_FOUND.getMessage()));
    }

    // 면접·역질문·질문·답변을 한 트랜잭션(한 스냅샷)에서 읽어, 요구사항 정리 완료나 답변 저장이 그 사이에 커밋돼도
    // 단계·현재 질문·답변·다이어그램이 서로 어긋나지 않게 한다. OSIV가 꺼져 있어 응답 조립도 이 트랜잭션 안에서 끝낸다.
    @Transactional(readOnly = true)
    public SystemDesignInterviewResponse findInterview(Long interviewId, Long memberId) {
        SystemDesignInterview systemDesignInterview = readOwnedInterview(interviewId, memberId);
        List<SystemDesignRequirementQuestionResponse> requirementQuestions = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview).stream()
                .map(SystemDesignRequirementQuestionResponse::from)
                .toList();
        List<SystemDesignQuestion> questions =
                questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        Map<Long, SystemDesignAnswer> answersByQuestionId = findAnswersByQuestionId(questions);
        List<SystemDesignQuestionAndAnswerResponse> prevQuestionsAndAnswers = questions.stream()
                .filter(question -> answersByQuestionId.containsKey(question.getId()))
                .map(question -> SystemDesignQuestionAndAnswerResponse.of(question,
                        answersByQuestionId.get(question.getId())))
                .toList();
        // 현재 질문은 진행 중인 면접에서 답이 없는 마지막 질문이다. 요구사항 단계와 끝난 면접에는 없고, 그때는 채점 상태 키도 없다.
        Optional<SystemDesignQuestion> curQuestion = systemDesignInterview.findCurrentQuestion(questions,
                answersByQuestionId.keySet());
        return SystemDesignInterviewResponse.of(systemDesignInterview, requirementQuestions,
                curQuestion.map(SystemDesignQuestion::getQuestionOrder).orElse(null),
                curQuestion.map(SystemDesignQuestion::getContent).orElse(null),
                prevQuestionsAndAnswers,
                curQuestion.map(question -> findCurAnswerProceedState(interviewId, question.getQuestionOrder()))
                        .orElse(null));
    }

    private Map<Long, SystemDesignAnswer> findAnswersByQuestionId(List<SystemDesignQuestion> questions) {
        return answerRepository.findByQuestionIn(questions).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity()));
    }

    /**
     * 현재 질문에 낸 답변의 채점 상태(답변 결과 폴링과 같은 Redis 값). 기록이 없으면 NONE이다. 현재 질문을 DB에서 정한 뒤 Redis를
     * 읽기 전에 워커가 끝나면 COMPLETED가 보이는데, 이때는 LLM_PENDING으로 보내 프론트가 폴링으로 결과를 받게 한다.
     * Redis를 읽지 못하면 조회가 Redis 장애로 막히지 않게 NONE으로 두고 WARN 로그만 남긴다.
     */
    private String findCurAnswerProceedState(Long interviewId, int curQuestionOrder) {
        try {
            return stateStore.find(interviewId, curQuestionOrder)
                    .map(SystemDesignInterviewService::toCurAnswerProceedState)
                    .orElse(NO_CUR_ANSWER_PROCEED_STATE);
        } catch (RuntimeException e) {
            log.warn("시스템 설계 현재 답변 채점 상태 조회 실패 - interviewId: {}, questionOrder: {}", interviewId,
                    curQuestionOrder, e);
            return NO_CUR_ANSWER_PROCEED_STATE;
        }
    }

    private static String toCurAnswerProceedState(InterviewProceedState state) {
        return switch (state) {
            case LLM_FAILED -> InterviewProceedState.LLM_FAILED.name();
            case LLM_PENDING, COMPLETED -> InterviewProceedState.LLM_PENDING.name();
            // 시스템 설계 채점은 음성 단계를 쓰지 않는다.
            case TTS_PENDING, TTS_FAILED -> NO_CUR_ANSWER_PROCEED_STATE;
        };
    }

    // 진행 락을 잡기 전에 흔한 오류(404·403·409)를 빨리 돌려준다. 락 안에서 completeRequirements가 같은 검사를 다시 한다.
    @Transactional(readOnly = true)
    public void validateRequirementsCompletable(Long interviewId, Long memberId) {
        readOwnedInterview(interviewId, memberId).validateRequirementsCompletable();
    }

    /**
     * 진행 락 안에서 부른다. 단계를 개략 설계로 바꾸고 문제의 1번 기준 질문을 LLM 없이 그대로 첫 질문으로 저장한다.
     */
    @Transactional
    public SystemDesignRequirementsCompleteResponse completeRequirements(Long interviewId, Long memberId) {
        SystemDesignInterview systemDesignInterview = readOwnedInterview(interviewId, memberId);
        systemDesignInterview.completeRequirements();
        SystemDesignProblemQuestion firstProblemQuestion = problemQuestionRepository
                .findByProblemAndQuestionOrder(systemDesignInterview.getProblem(), FIRST_QUESTION_ORDER)
                .orElseThrow(() -> new IllegalStateException(
                        "시스템 설계 문제에 1번 기준 질문이 없습니다. problemId: "
                                + systemDesignInterview.getProblem().getId()));
        SystemDesignQuestion firstQuestion = questionRepository.save(new SystemDesignQuestion(systemDesignInterview,
                firstProblemQuestion, firstProblemQuestion.getContent()));
        return SystemDesignRequirementsCompleteResponse.of(systemDesignInterview, firstQuestion);
    }
}
