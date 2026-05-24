package com.samhap.kokomen.global.external.gpt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "open-ai")
public record GptProperties(
        String apiKey,
        Double evaluationTemperature,
        Double generationTemperature,
        Double feedbackTemperature
) {
}
