package com.samhap.kokomen.interview.external.dto.request;

public record GptFunction(
        String name,
        GptFunctionParameters parameters
) {
}
