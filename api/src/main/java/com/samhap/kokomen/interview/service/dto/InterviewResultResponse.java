package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.dto.AnswerMemos;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
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
        Integer userPrevScore,
        InterviewMode interviewMode
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
                member.getScore() - interview.getTotalScore(),
                interview.getInterviewMode()
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
                null,
                interview.getInterviewMode()
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
                null,
                interview.getInterviewMode()
        );
    }
}
