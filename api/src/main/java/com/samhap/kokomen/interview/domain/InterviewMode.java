package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartVoiceModeResponse;
import java.util.function.BiFunction;

public enum InterviewMode {
    TEXT(1, (interview, question) -> new InterviewStartTextModeResponse(interview, question)),
    VOICE(2, (interview, question) -> new InterviewStartVoiceModeResponse(interview, question)),
    ;

    private final int requiredTokenCount;
    private final BiFunction<Interview, Question, InterviewStartResponse> responseCreator; // interviewMode에 따라 응답 DTO가 달라짐

    InterviewMode(int requiredTokenCount, BiFunction<Interview, Question, InterviewStartResponse> responseCreator) {
        this.requiredTokenCount = requiredTokenCount;
        this.responseCreator = responseCreator;
    }

    public int getRequiredTokenCount() {
        return requiredTokenCount;
    }

    public InterviewStartResponse createInterviewStartResponse(Interview interview, Question question) {
        return responseCreator.apply(interview, question);
    }
}
