package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import java.util.List;

/**
 * 끝난 시스템 설계 면접의 공개 리포트. 다른 회원 목록에서 열고, 로그인 여부나 요청한 회원과 상관없이 같은 응답이다.
 * 리포트({@link SystemDesignReportResponse})의 필드에서 소유자 점수(user_cur_score, user_prev_score)를 필드째 빼고(null로 두지 않는다)
 * 면접자 값 셋을 더했다. 세 값은 기존 다른 회원 결과(InterviewResultResponse)와 이름·계산이 같다. 조회수·좋아요 필드는 없다.
 */
public record SystemDesignPublicReportResponse(
        Long interviewId,
        SystemDesignTopicResponse topic,
        SystemDesignDifficulty difficulty,
        Integer totalScore,
        String totalFeedback,
        String difficultyComment,
        List<SystemDesignEvaluationPointResponse> evaluationPoints,
        List<String> missedRequirements,
        List<SystemDesignRequirementQuestionResponse> requirementQuestions,
        List<SystemDesignFeedbackResponse> feedbacks,
        SystemDesignDiagramResponse finalDiagram,
        String intervieweeNickname,
        Long totalMemberCount,
        Long intervieweeRank
) {

    /**
     * 리포트의 값을 소유자 점수만 빼고 그대로 옮긴다.
     *
     * @param totalMemberCount 전체 회원 수
     * @param intervieweeRank  면접자의 지금 회원 점수 순위(동점은 같은 순위)
     */
    public static SystemDesignPublicReportResponse of(SystemDesignReportResponse report, String intervieweeNickname,
                                                      long totalMemberCount, long intervieweeRank) {
        return new SystemDesignPublicReportResponse(
                report.interviewId(),
                report.topic(),
                report.difficulty(),
                report.totalScore(),
                report.totalFeedback(),
                report.difficultyComment(),
                report.evaluationPoints(),
                report.missedRequirements(),
                report.requirementQuestions(),
                report.feedbacks(),
                report.finalDiagram(),
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank
        );
    }
}
