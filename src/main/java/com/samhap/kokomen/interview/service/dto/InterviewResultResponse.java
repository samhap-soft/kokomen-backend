package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;

public record InterviewResultResponse(
        List<FeedbackResponse> feedbacks,
        String totalFeedback,
        Integer totalScore,
        Integer interviewLikeCount,
        Integer userCurScore,
        Integer userPrevScore
) {

    public static InterviewResultResponse createMyResultResponse(
            List<FeedbackResponse> feedbacks,
            Interview interview,
            Member member
    ) {
        return new InterviewResultResponse(
                feedbacks,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                null,
                member.getScore(),
                member.getScore() - interview.getTotalScore()
        );
    }

    public static InterviewResultResponse createResultResponse(
            List<FeedbackResponse> feedbacks,
            Interview interview
    ) {
        return new InterviewResultResponse(
                feedbacks,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                interview.getLikeCount(),
                null,
                null
        );
    }
}
