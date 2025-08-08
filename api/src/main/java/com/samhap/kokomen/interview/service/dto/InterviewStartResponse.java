package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewStartResponse(
        Long interviewId,
        Long questionId,
        String rootQuestion
) {
    public InterviewStartResponse(Interview interview, Question question) {
        this(interview.getId(), question.getId(), question.getContent());
    }

}
