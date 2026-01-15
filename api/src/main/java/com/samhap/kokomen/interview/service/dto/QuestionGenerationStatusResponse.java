package com.samhap.kokomen.interview.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.samhap.kokomen.interview.domain.InterviewState;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionGenerationStatusResponse(
        InterviewState status,
        Integer questionCount
) {
    public static QuestionGenerationStatusResponse of(InterviewState status) {
        return new QuestionGenerationStatusResponse(status, null);
    }

    public static QuestionGenerationStatusResponse of(InterviewState status, int questionCount) {
        return new QuestionGenerationStatusResponse(status, questionCount);
    }
}
