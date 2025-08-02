package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.dto.AnswerMemos;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record InterviewResultResponse(
        List<FeedbackResponse> feedbacks,
        String totalFeedback,
        Integer totalScore,
        Long interviewViewCount,
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
                null,
                member.getScore(),
                member.getScore() - interview.getTotalScore()
        );
    }

    public static InterviewResultResponse createOfOtherMemberForAuthorized(
            List<Answer> answers,
            Set<Long> likedAnswerIds,
            Interview interview,
            Long interviewViewCount,
            Boolean interviewAlreadyLiked,
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank,
            Map<Long, AnswerMemos> answerMemos
    ) {
        List<FeedbackResponse> feedbackResponses = answers.stream()
                .map(answer -> new FeedbackResponse(answer, likedAnswerIds.contains(answer.getId()), answerMemos.get(answer.getId())))
                .toList();

        return new InterviewResultResponse(
                feedbackResponses,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                interviewViewCount,
                interview.getLikeCount(),
                interviewAlreadyLiked,
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank,
                null,
                null
        );
    }

    public static InterviewResultResponse createOfOtherMemberForUnauthorized(
            List<Answer> answers,
            Interview interview,
            Long interviewViewCount,
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank,
            Map<Long, AnswerMemos> answerMemos
    ) {
        List<FeedbackResponse> feedbackResponses = answers.stream()
                .map(answer -> new FeedbackResponse(answer, false, answerMemos.get(answer.getId())))
                .toList();

        return new InterviewResultResponse(
                feedbackResponses,
                interview.getTotalFeedback(),
                interview.getTotalScore(),
                interviewViewCount,
                interview.getLikeCount(),
                false,
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank,
                null,
                null
        );
    }

    public static InterviewResultResponse of(InterviewResultResponse response, Long viewCount) {
        return new InterviewResultResponse(
                response.feedbacks,
                response.totalFeedback,
                response.totalScore,
                viewCount,
                response.interviewLikeCount,
                response.interviewAlreadyLiked,
                response.intervieweeNickname,
                response.totalMemberCount,
                response.intervieweeRank,
                response.userCurScore,
                response.userPrevScore
        );
    }
}
