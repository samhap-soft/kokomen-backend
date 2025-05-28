package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;

public record InterviewTotalResponse(
        List<FeedbackResponse> feedbacks,
        Integer totalScore,
        Integer userCurScore,
        Integer userPrevScore,
        String userCurRank,
        String userPrevRank
) {

    public static InterviewTotalResponse of(
            List<FeedbackResponse> feedbacks,
            Interview interview,
            Member member
    ) {
        return new InterviewTotalResponse(
                feedbacks,
                interview.getTotalScore(),
                member.getScore(),
                member.getScore() - interview.getTotalScore(),
                "BRONZE",
                "BRONZE"
        );
    }
}
