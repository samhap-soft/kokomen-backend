package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface LlmResponse {

    AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper);

    default AnswerRankResponse extractAnswerRankResponse(ObjectMapper objectMapper) {
        throw new UnsupportedOperationException("잘못된 호출입니다. 베드락에서만 사용 가능합니다.");
    }

    NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper);

    TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper);
}
