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
        Integer interviewLikeCount,
        Boolean alreadyLiked
) {
    public InterviewSummaryResponse(Interview interview, Integer curAnswerCount, Boolean alreadyLiked) {
        this(
                interview.getId(),
                interview.getInterviewState(),
                interview.getRootQuestion().getCategory(),
                interview.getCreatedAt(),
                interview.getRootQuestion().getContent(),
                interview.getMaxQuestionCount(),
                curAnswerCount,
                interview.getTotalScore(),
                interview.getLikeCount(),
                alreadyLiked
        );
    }

    public static InterviewSummaryResponse createOfTargetMember(Interview interview, Boolean alreadyLiked) {
        return new InterviewSummaryResponse(interview, null, alreadyLiked);
    }

    public static InterviewSummaryResponse createMine(Interview interview, Integer curAnswerCount, Boolean alreadyLiked) {
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
                interview.getLikeCount(),
                alreadyLiked
        );
    }
} 
