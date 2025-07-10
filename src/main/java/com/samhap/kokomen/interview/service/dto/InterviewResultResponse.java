package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;
import java.util.Set;

public record InterviewResultResponse(
        List<FeedbackResponse> feedbacks,
        String totalFeedback,
        Integer totalScore,
        Long interviewLikeCount,
        Boolean interviewAlreadyLiked,
        String intervieweeNickname,
        Long totalMemberCount,
        Long intervieweeRank,
        Integer userCurScore,
        Integer userPrevScore
) {

    public static InterviewResultResponse createMine(
            List<FeedbackResponse> feedbacks,
            Interview interview,
            Member member
    ) {
        return new InterviewResultResponse(
                feedbacks,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                null,
                null,
                null,
                null,
                null,
                member.getScore(),
                member.getScore() - interview.getTotalScore()
        );
    }

    public static InterviewResultResponse createOfOtherMemberForLoginMember(
            List<Answer> answers,
            Set<Long> likedAnswerIds,
            Interview interview,
            Boolean interviewAlreadyLiked,
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank
    ) {
        List<FeedbackResponse> feedbackResponses = answers.stream()
                .map(answer -> new FeedbackResponse(answer, likedAnswerIds.contains(answer.getId())))
                .toList();

        return new InterviewResultResponse(
                feedbackResponses,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                interview.getLikeCount(),
                interviewAlreadyLiked,
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank,
                null,
                null
        );
    }

    public static InterviewResultResponse createOfOtherMemberForLogoutMember(
            List<Answer> answers,
            Interview interview,
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank
    ) {
        List<FeedbackResponse> feedbackResponses = answers.stream()
                .map(answer -> new FeedbackResponse(answer, false))
                .toList();

        return new InterviewResultResponse(
                feedbackResponses,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                interview.getLikeCount(),
                false,
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank,
                null,
                null
        );
    }
}
