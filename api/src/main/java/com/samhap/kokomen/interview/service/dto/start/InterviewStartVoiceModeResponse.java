package com.samhap.kokomen.interview.service.dto.start;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewStartVoiceModeResponse(
        Long interviewId,
        Long questionId,
        String rootQuestionVoiceUrl
) implements InterviewStartResponse {

    public InterviewStartVoiceModeResponse(Interview interview, Question question) {
        this(interview.getId(), question.getId(), interview.getRootQuestion().getVoiceUrl());
    }

}
