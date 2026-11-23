package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import java.time.LocalDateTime;

public record InterviewSummaryResponse(
        Long interviewId,
        InterviewType interviewType,
        InterviewState interviewState,
        InterviewMode interviewMode,
        String interviewCategory,
        LocalDateTime createdAt,
        String rootQuestion,
        Integer maxQuestionCount,
        Integer curAnswerCount,
        Integer score,
        Long interviewViewCount,
        Long interviewLikeCount,
        Boolean interviewAlreadyLiked,
        Integer submittedAnswerMemoCount,
        Boolean hasTempAnswerMemo
) {
    public InterviewSummaryResponse(
            Interview interview, String displayQuestion, InterviewState interviewState, Integer curAnswerCount,
            Long viewCount, Boolean interviewAlreadyLiked, Integer submittedAnswerMemoCount,
            Boolean hasTempAnswerMemo) {
        this(
                interview.getId(),
                interview.getInterviewType(),
                interviewState,
                interview.getInterviewMode(),
                interview.getDisplayCategory(),
                interview.getCreatedAt(),
                displayQuestion,
                interview.getMaxQuestionCount(),
                curAnswerCount,
                interview.getTotalScore(),
                viewCount,
                interview.getLikeCount(),
                interviewAlreadyLiked,
                submittedAnswerMemoCount,
                hasTempAnswerMemo
        );
    }

    public static InterviewSummaryResponse createOfOtherMemberForAuthorized(
            Interview interview, String displayQuestion, Long viewCount, Boolean interviewAlreadyLiked,
            Integer submittedAnswerMemoCount) {
        return new InterviewSummaryResponse(interview, displayQuestion, null, null, viewCount, interviewAlreadyLiked,
                submittedAnswerMemoCount, null);
    }

    public static InterviewSummaryResponse createOfOtherMemberForUnauthorized(
            Interview interview, String displayQuestion, Long viewCount, Integer submittedAnswerMemoCount) {
        return new InterviewSummaryResponse(interview, displayQuestion, null, null, viewCount, false,
                submittedAnswerMemoCount, null);
    }

    public static InterviewSummaryResponse createMine(Interview interview, String displayQuestion,
                                                      Integer curAnswerCount, Long viewCount,
                                                      Boolean interviewAlreadyLiked, Integer submittedAnswerMemoCount,
                                                      Boolean hasTempAnswerMemo) {
        if (interview.isInProgress()) {
            return new InterviewSummaryResponse(
                    interview.getId(),
                    interview.getInterviewType(),
                    interview.getInterviewState(),
                    interview.getInterviewMode(),
                    interview.getDisplayCategory(),
                    interview.getCreatedAt(),
                    displayQuestion,
                    interview.getMaxQuestionCount(),
                    curAnswerCount,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return new InterviewSummaryResponse(
                interview.getId(),
                interview.getInterviewType(),
                interview.getInterviewState(),
                interview.getInterviewMode(),
                interview.getDisplayCategory(),
                interview.getCreatedAt(),
                displayQuestion,
                interview.getMaxQuestionCount(),
                curAnswerCount,
                interview.getTotalScore(),
                viewCount,
                interview.getLikeCount(),
                interviewAlreadyLiked,
                submittedAnswerMemoCount,
                hasTempAnswerMemo
        );
    }
}
