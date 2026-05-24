package com.samhap.kokomen.global.external.gpt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "open-ai")
public record GptProperties(
        @NotBlank String apiKey,
        @NotNull Double evaluationTemperature,
        @NotNull Double generationTemperature,
        @NotNull Double feedbackTemperature
) {
}
