package com.samhap.kokomen.interview.external.dto.request;

public record TypecastRequest(
        String actorId,
        String text,
        String lang,
        String xapiAudioFormat
) {
    public TypecastRequest(String text) {
        this("63c76c7f0a9ab6c54f4d36bd", text, "auto", "mp3");
    }
}
