package com.samhap.kokomen.interview.service.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationResult;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignHiddenRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationResultRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignEvaluationPointResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignFeedbackResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignReportResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝난 시스템 설계 면접의 리포트. 마지막 답변을 저장할 때 함께 저장한 값(평가 포인트 판정, 리포트 점수, 종합 피드백, 등급 점수 합)을
 * 조립만 하므로 LLM을 부르지 않는다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignReportService {

    private final SystemDesignInterviewService systemDesignInterviewService;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;
    private final SystemDesignEvaluationResultRepository evaluationResultRepository;
    private final SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    private final SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    private final SystemDesignQuestionRepository questionRepository;
    private final SystemDesignAnswerRepository answerRepository;

    /**
     * 소유자(404·403) → 끝난 면접(409) 순서로 본다. OSIV가 꺼져 있어 응답 조립도 이 읽기 트랜잭션 안에서 끝낸다.
     */
    @Transactional(readOnly = true)
    public SystemDesignReportResponse findReport(Long interviewId, Long memberId) {
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(interviewId, memberId);
        systemDesignInterview.validateReportReady();
        return createReport(systemDesignInterview);
    }

    /**
     * 끝난 면접의 저장된 값으로 리포트를 조립한다. 회원 점수는 면접자에게서 읽으므로 요청한 회원과 상관없이 같은 리포트가 나온다.
     * 평가 포인트·판정·숨은 요구사항·역질문·질문·답변을 목록마다 한 번씩 읽어 메모리에서 짝짓는다(원소마다 다시 읽지 않는다).
     */
    private SystemDesignReportResponse createReport(SystemDesignInterview systemDesignInterview) {
        SystemDesignProblem problem = systemDesignInterview.getProblem();
        List<SystemDesignRequirementQuestion> requirementQuestions = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        return SystemDesignReportResponse.of(systemDesignInterview,
                createEvaluationPoints(systemDesignInterview, problem),
                findMissedRequirementNames(problem, requirementQuestions),
                requirementQuestions.stream()
                        .map(SystemDesignRequirementQuestionResponse::from)
                        .toList(),
                createFeedbacks(systemDesignInterview));
    }

    // 문제의 평가 포인트 순서로 판정을 붙인다. 판정은 마지막 답변을 저장할 때 포인트마다 하나씩 저장했다.
    private List<SystemDesignEvaluationPointResponse> createEvaluationPoints(
            SystemDesignInterview systemDesignInterview, SystemDesignProblem problem) {
        Map<Long, Boolean> metByPointId = evaluationResultRepository.findBySystemDesignInterview(systemDesignInterview)
                .stream()
                .collect(Collectors.toMap(result -> result.getEvaluationPoint().getId(),
                        SystemDesignEvaluationResult::isMet));
        return evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem).stream()
                .map(point -> new SystemDesignEvaluationPointResponse(point.getDescription(),
                        readMet(systemDesignInterview, metByPointId, point)))
                .toList();
    }

    // 빠진 판정을 충족하지 않음으로 지어내지 않는다. 끝난 면접에 판정이 빠졌으면 저장이 어긋난 것이다.
    private static boolean readMet(SystemDesignInterview systemDesignInterview, Map<Long, Boolean> metByPointId,
                                   SystemDesignEvaluationPoint point) {
        Boolean met = metByPointId.get(point.getId());
        if (met == null) {
            throw new IllegalStateException("끝난 시스템 설계 면접에 평가 포인트 판정이 없습니다. interviewId: %d, code: %s"
                    .formatted(systemDesignInterview.getInterview().getId(), point.getCode()));
        }
        return met;
    }

    // 모든 역질문에서 물은 요구사항 code의 합집합에 없는 숨은 요구사항의 이름(문제의 순서). 역질문이 없으면 모두 놓친 것이다.
    private List<String> findMissedRequirementNames(SystemDesignProblem problem,
                                                    List<SystemDesignRequirementQuestion> requirementQuestions) {
        Set<String> askedCodes = requirementQuestions.stream()
                .flatMap(requirementQuestion -> requirementQuestion.getAskedRequirementCodes().stream())
                .collect(Collectors.toSet());
        return hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(problem).stream()
                .filter(requirement -> !askedCodes.contains(requirement.getCode()))
                .map(SystemDesignHiddenRequirement::getName)
                .toList();
    }

    // 답이 저장된 질문을 순번대로(면접 조회의 지난 질문과 같은 기준). 끝난 면접은 6개 질문 모두 답이 있다.
    private List<SystemDesignFeedbackResponse> createFeedbacks(SystemDesignInterview systemDesignInterview) {
        List<SystemDesignQuestion> questions =
                questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        Map<Long, SystemDesignAnswer> answersByQuestionId = answerRepository.findByQuestionIn(questions).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity()));
        return questions.stream()
                .filter(question -> answersByQuestionId.containsKey(question.getId()))
                .map(question -> SystemDesignFeedbackResponse.of(question, answersByQuestionId.get(question.getId())))
                .toList();
    }
}
