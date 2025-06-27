package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface LLMResponse {

    AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper);

    NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper);

    TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper);
}
