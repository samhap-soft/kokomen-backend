package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public record GptResponse(
        List<Choice> choices
) {

    public AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper) {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .extractGptFeedbackResponse(objectMapper);
    }

    public NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper) {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .extractGptNextQuestionResponse(objectMapper);
    }

    public TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper) {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .extractGptTotalFeedbackResponse(objectMapper);
    }

}
