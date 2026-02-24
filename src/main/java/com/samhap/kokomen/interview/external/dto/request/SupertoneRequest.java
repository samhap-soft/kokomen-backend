package com.samhap.kokomen.interview.external.dto.request;

public record SupertoneRequest(
        String text,
        String language
) {
    public SupertoneRequest(String text) {
        this(text, "ko");
    }
}
