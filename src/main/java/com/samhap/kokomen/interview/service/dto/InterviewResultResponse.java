package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;

public record InterviewResultResponse(
        List<FeedbackResponse> feedbacks,
        String totalFeedback,
        Integer totalScore,
        Integer userCurScore,
        Integer userPrevScore,
        String userCurRank,
        String userPrevRank
) {

    public static InterviewResultResponse of(
            List<FeedbackResponse> feedbacks,
            Interview interview,
            Member member
    ) {
        return new InterviewResultResponse(
                feedbacks,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                member.getScore(),
                member.getScore() - interview.getTotalScore(),
                "BRONZE",
                "BRONZE"
        );
    }
}
