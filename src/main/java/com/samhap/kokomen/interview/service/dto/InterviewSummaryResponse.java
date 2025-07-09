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
        Long interviewLikeCount,
        Boolean interviewAlreadyLiked
) {
    public InterviewSummaryResponse(Interview interview, Integer curAnswerCount, Boolean interviewAlreadyLiked) {
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
                interviewAlreadyLiked
        );
    }

    public static InterviewSummaryResponse createOfTargetMember(Interview interview, Boolean interviewAlreadyLiked) {
        return new InterviewSummaryResponse(interview, null, interviewAlreadyLiked);
    }

    public static InterviewSummaryResponse createMine(Interview interview, Integer curAnswerCount, Boolean interviewAlreadyLiked) {
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
                interviewAlreadyLiked
        );
    }
} 
