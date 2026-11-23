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
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new NotFoundException(INTERVIEW_NOT_FOUND.getMessage()));
        if (!systemDesignInterview.getInterview().isInterviewee(memberId)) {
            throw new ForbiddenException(NOT_OWNER.getMessage());
        }
        return systemDesignInterview;
    }

    // OSIV가 꺼져 있어 지연 로딩이 필요한 응답 조립까지 이 트랜잭션 안에서 끝낸다.
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
        Optional<SystemDesignQuestion> curQuestion = findCurrentQuestion(questions, answersByQuestionId);
        return SystemDesignInterviewResponse.of(systemDesignInterview, requirementQuestions,
                curQuestion.map(SystemDesignQuestion::getQuestionOrder).orElse(null),
                curQuestion.map(SystemDesignQuestion::getContent).orElse(null),
                prevQuestionsAndAnswers);
    }

    private Map<Long, SystemDesignAnswer> findAnswersByQuestionId(List<SystemDesignQuestion> questions) {
        return answerRepository.findByQuestionIn(questions).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity()));
    }

    // 현재 질문은 답이 없는 마지막 질문이다. 요구사항 단계에는 질문이 없고, 끝난 면접은 모든 질문에 답이 있어 없다.
    private static Optional<SystemDesignQuestion> findCurrentQuestion(
            List<SystemDesignQuestion> questions,
            Map<Long, SystemDesignAnswer> answersByQuestionId
    ) {
        if (questions.isEmpty()) {
            return Optional.empty();
        }
        SystemDesignQuestion lastQuestion = questions.get(questions.size() - 1);
        if (answersByQuestionId.containsKey(lastQuestion.getId())) {
            return Optional.empty();
        }
        return Optional.of(lastQuestion);
    }
}
