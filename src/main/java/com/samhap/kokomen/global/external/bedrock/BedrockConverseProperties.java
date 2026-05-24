package com.samhap.kokomen.global.external.bedrock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.bedrock")
public record BedrockConverseProperties(
        @NotBlank String modelId,
        @NotNull Integer proceedMaxTokens,
        @NotNull Integer endMaxTokens,
        @NotNull Integer answerFeedbackMaxTokens,
        @NotNull Integer resumeQuestionMaxTokens,
        @NotNull Integer resumeEvaluationMaxTokens,
        @NotNull Float evaluationTemperature,
        @NotNull Float generationTemperature,
        @NotNull Float feedbackTemperature
) {
}
