package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import java.time.LocalDateTime;

public record InterviewSummaryResponse(
        Long interviewId,
        InterviewState interviewState,
        Category interviewCategory,
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
            Interview interview, InterviewState interviewState, Integer curAnswerCount, Long viewCount, Boolean interviewAlreadyLiked,
            Integer submittedAnswerMemoCount, Boolean hasTempAnswerMemo) {
        this(
                interview.getId(),
                interviewState,
                interview.getRootQuestion().getCategory(),
                interview.getCreatedAt(),
                interview.getRootQuestion().getContent(),
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
            Interview interview, Long viewCount, Boolean interviewAlreadyLiked, Integer submittedAnswerMemoCount) {
        return new InterviewSummaryResponse(interview, null, null, viewCount, interviewAlreadyLiked, submittedAnswerMemoCount, null);
    }

    public static InterviewSummaryResponse createOfOtherMemberForUnauthorized(Interview interview, Long viewCount, Integer submittedAnswerMemoCount) {
        return new InterviewSummaryResponse(interview, null, null, viewCount, false, submittedAnswerMemoCount, null);
    }

    public static InterviewSummaryResponse createMine(Interview interview, Integer curAnswerCount, Long viewCount, Boolean interviewAlreadyLiked,
                                                      Integer submittedAnswerMemoCount, Boolean hasTempAnswerMemo) {
        if (interview.isInProgress()) {
            return new InterviewSummaryResponse(
                    interview.getId(),
                    interview.getInterviewState(),
                    interview.getRootQuestion().getCategory(),
                    interview.getCreatedAt(),
                    interview.getRootQuestion().getContent(),
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
                interview.getInterviewState(),
                interview.getRootQuestion().getCategory(),
                interview.getCreatedAt(),
                interview.getRootQuestion().getContent(),
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
