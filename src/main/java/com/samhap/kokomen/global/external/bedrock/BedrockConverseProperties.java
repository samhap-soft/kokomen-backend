package com.samhap.kokomen.global.external.bedrock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.bedrock")
public record BedrockConverseProperties(
        String modelId,
        Integer proceedMaxTokens,
        Integer endMaxTokens,
        Integer answerFeedbackMaxTokens,
        Integer resumeQuestionMaxTokens,
        Integer resumeEvaluationMaxTokens,
        Float temperature
) {
}
