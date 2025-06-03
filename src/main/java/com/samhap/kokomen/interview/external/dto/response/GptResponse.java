package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public record GptResponse(
        List<Choice> choices
) {

    public GptFeedbackResponse extractGptFeedbackResponse(ObjectMapper objectMapper) {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .extractGptFeedbackResponse(objectMapper);
    }

    public GptNextQuestionResponse extractGptNextQuestionResponse(ObjectMapper objectMapper) {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .extractGptNextQuestionResponse(objectMapper);
    }

    public GptTotalFeedbackResponse extractGptTotalFeedbackResponse(ObjectMapper objectMapper) {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .extractGptTotalFeedbackResponse(objectMapper);
    }

}
