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
        Integer score
) {
    public InterviewSummaryResponse(Interview interview, Integer curAnswerCount) {
        this(
                interview.getId(),
                interview.getInterviewState(),
                interview.getRootQuestion().getCategory(),
                interview.getCreatedAt(),
                interview.getRootQuestion().getContent(),
                interview.getMaxQuestionCount(),
                curAnswerCount,
                interview.getTotalScore()
        );
    }
} 
