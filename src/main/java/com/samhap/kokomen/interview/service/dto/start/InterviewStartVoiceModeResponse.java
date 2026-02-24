package com.samhap.kokomen.interview.service.dto.start;

import com.samhap.kokomen.interview.entity.Interview;
import com.samhap.kokomen.interview.entity.Question;

public record InterviewStartVoiceModeResponse(
        Long interviewId,
        Long questionId,
        String rootQuestionVoiceUrl
) implements InterviewStartResponse {

    public InterviewStartVoiceModeResponse(Interview interview, Question question, String rootQuestionVoiceUrl) {
        this(interview.getId(), question.getId(), rootQuestionVoiceUrl);
    }

}
