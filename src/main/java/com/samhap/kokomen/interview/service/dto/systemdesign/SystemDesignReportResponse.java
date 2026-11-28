package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;

/**
 * 끝난 시스템 설계 면접의 리포트. totalScore는 리포트 점수(0~100)이고 랭킹에 더하는 등급 점수 합(interview.total_score)과 다르다.
 * userCurScore·userPrevScore는 면접자의 랭킹 점수로, 기존 면접 결과(InterviewResultResponse.createMine)와 같은 계산이다.
 * 순위 필드(user_cur_rank, user_prev_rank)는 프론트와 합의해 두지 않는다.
 */
public record SystemDesignReportResponse(
        Long interviewId,
        SystemDesignTopicResponse topic,
        SystemDesignDifficulty difficulty,
        Integer totalScore,
        String totalFeedback,
        String difficultyComment,
        List<SystemDesignEvaluationPointResponse> evaluationPoints,
        List<String> missedRequirements,
        List<SystemDesignRequirementQuestionResponse> requirementQuestions,
        List<SystemDesignConfirmedRequirementResponse> confirmedRequirements,
        List<SystemDesignFeedbackResponse> feedbacks,
        SystemDesignDiagramResponse finalDiagram,
        Integer userCurScore,
        Integer userPrevScore
) {

    /**
     * 회원 점수는 요청한 회원이 아니라 면접자(interview.member)에게서 읽는다. 끝난 면접의 현재 다이어그램은 마지막 답변과 함께 낸
     * 다이어그램이다.
     *
     * @param evaluationPoints      문제의 평가 포인트 순서
     * @param missedRequirements    묻지 않은 숨은 요구사항의 이름(문제의 순서)
     * @param requirementQuestions  물은 순서
     * @param confirmedRequirements 역질문으로 확정한 요구사항(SystemDesignConfirmedRequirement.collect의 순서)
     * @param feedbacks             질문 순번 순서
     */
    public static SystemDesignReportResponse of(
            SystemDesignInterview systemDesignInterview,
            List<SystemDesignEvaluationPointResponse> evaluationPoints,
            List<String> missedRequirements,
            List<SystemDesignRequirementQuestionResponse> requirementQuestions,
            List<SystemDesignConfirmedRequirementResponse> confirmedRequirements,
            List<SystemDesignFeedbackResponse> feedbacks
    ) {
        Interview interview = systemDesignInterview.getInterview();
        SystemDesignProblem problem = systemDesignInterview.getProblem();
        Member interviewee = interview.getMember();
        return new SystemDesignReportResponse(
                interview.getId(),
                SystemDesignTopicResponse.from(problem),
                problem.getDifficulty(),
                systemDesignInterview.getReportScore(),
                interview.getTotalFeedback(),
                problem.getDifficultyComment(),
                evaluationPoints,
                missedRequirements,
                requirementQuestions,
                confirmedRequirements,
                feedbacks,
                SystemDesignDiagramResponse.from(systemDesignInterview.getCurrentDiagram()),
                interviewee.getScore(),
                interviewee.getScore() - interview.getTotalScore()
        );
    }
}
