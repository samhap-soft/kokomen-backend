package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public record GptFunctionCall(
        String name,
        String arguments
) {

    public GptAnswerResponse toGptAnswerResponse() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(arguments, GptAnswerResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 파싱 실패", e);
        }
    }
}
