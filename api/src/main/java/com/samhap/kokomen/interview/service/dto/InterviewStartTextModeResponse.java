package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewStartTextModeResponse(
        Long interviewId,
        Long questionId,
        String rootQuestion
) implements InterviewStartResponse {

    public InterviewStartTextModeResponse(Interview interview, Question question) {
        this(interview.getId(), question.getId(), question.getContent());
    }

}
