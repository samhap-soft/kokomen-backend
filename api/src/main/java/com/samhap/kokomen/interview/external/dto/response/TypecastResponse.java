package com.samhap.kokomen.interview.external.dto.response;

public record TypecastResponse(
        TypecastResult result
) {
    public String getSpeakV2Url() {
        return result.speakV2Url();
    }
}
