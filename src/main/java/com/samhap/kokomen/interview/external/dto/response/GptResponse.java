package com.samhap.kokomen.interview.external.dto.response;

import java.util.List;

public record GptResponse(
        List<Choice> choices
) {

    public GptProceedResponse toGptProceedResponse() {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .toGptProceedResponse();
    }

    public GptEndResponse toGptEndResponse() {
        return choices.get(0)
                .message()
                .toolCalls()
                .get(0)
                .function()
                .toGptEndResponse();
    }
}
