package com.samhap.kokomen.interview.external.dto.request;

public record TypecastRequest(
        String actorId,
        String text,
        String lang,
        int tempo, // 배속
        int volume,
        int pitch,
        boolean xapiHd, // 고음질 여부
        int maxSeconds, // 최대 음성 길이 (초 단위)
        String modelVersion, // latest
        String xapiAudioFormat // 오디오 포맷 -> wav
) {
}
