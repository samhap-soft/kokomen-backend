package com.samhap.kokomen.interview.external.dto.response;

import java.util.List;

public record GptResponse(
        List<Choice> choices
) {

    public GptAnswerResponse toGptAnswerResponse() {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .toGptAnswerResponse();
    }
}
