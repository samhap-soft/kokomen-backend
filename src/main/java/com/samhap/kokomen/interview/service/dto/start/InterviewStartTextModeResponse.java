package com.samhap.kokomen.interview.service.dto.start;

import com.samhap.kokomen.interview.entity.Interview;
import com.samhap.kokomen.interview.entity.Question;

public record InterviewStartTextModeResponse(
        Long interviewId,
        Long questionId,
        String rootQuestion
) implements InterviewStartResponse {

    public InterviewStartTextModeResponse(Interview interview, Question question) {
        this(interview.getId(), question.getId(), question.getContent());
    }

}
