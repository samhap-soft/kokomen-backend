package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public record GptFunctionCall(
        String name,
        String arguments
) {

    public GptProceedResponse toGptProceedResponse() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(arguments, GptProceedResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 파싱 실패", e);
        }
    }

    public GptEndResponse toGptEndResponse() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(arguments, GptEndResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 파싱 실패", e);
        }
    }
}
