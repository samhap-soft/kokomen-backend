package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewStartVoiceResponse(
        Long interviewId,
        Long questionId,
        String rootQuestionVoiceUrl
) implements InterviewStartResponse {

    public InterviewStartVoiceResponse(Interview interview, Question question) {
        this(interview.getId(), question.getId(), interview.getRootQuestion().getVoiceUrl());
    }

}
