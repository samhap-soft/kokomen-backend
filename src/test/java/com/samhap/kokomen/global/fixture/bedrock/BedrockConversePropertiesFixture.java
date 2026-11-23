package com.samhap.kokomen.global.fixture.bedrock;

import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;

/**
 * BedrockConverseProperties를 만드는 테스트의 한곳. 기본값은 운영 application.yml의 aws.bedrock 값과 같다
 * (BedrockConversePropertiesFixtureTest가 고정한다). 컴포넌트를 더하면 이 파일만 고친다.
 */
public final class BedrockConversePropertiesFixture {

    private static final String KEY_PREFIX = "aws.bedrock.";

    private BedrockConversePropertiesFixture() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BedrockConverseProperties create() {
        return builder().build();
    }

    /**
     * ApplicationContextRunner의 withPropertyValues에 넣을 {@code aws.bedrock.*=값} 전체.
     */
    public static String[] propertyValues(BedrockConverseProperties properties) {
        return new String[]{
                KEY_PREFIX + "model-id=" + properties.modelId(),
                KEY_PREFIX + "proceed-max-tokens=" + properties.proceedMaxTokens(),
                KEY_PREFIX + "end-max-tokens=" + properties.endMaxTokens(),
                KEY_PREFIX + "answer-feedback-max-tokens=" + properties.answerFeedbackMaxTokens(),
                KEY_PREFIX + "resume-question-max-tokens=" + properties.resumeQuestionMaxTokens(),
                KEY_PREFIX + "resume-evaluation-max-tokens=" + properties.resumeEvaluationMaxTokens(),
                KEY_PREFIX + "evaluation-temperature=" + properties.evaluationTemperature(),
                KEY_PREFIX + "generation-temperature=" + properties.generationTemperature(),
                KEY_PREFIX + "feedback-temperature=" + properties.feedbackTemperature(),
                KEY_PREFIX + "system-design-requirement-max-tokens=" + properties.systemDesignRequirementMaxTokens(),
        };
    }

    public static final class Builder {

        private String modelId = "us.anthropic.claude-sonnet-4-6";
        private int proceedMaxTokens = 2048;
        private int endMaxTokens = 4096;
        private int answerFeedbackMaxTokens = 1024;
        private int resumeQuestionMaxTokens = 10000;
        private int resumeEvaluationMaxTokens = 16000;
        private float evaluationTemperature = 0.2f;
        private float generationTemperature = 0.7f;
        private float feedbackTemperature = 0.5f;
        private int systemDesignRequirementMaxTokens = 1024;

        private Builder() {
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder proceedMaxTokens(int proceedMaxTokens) {
            this.proceedMaxTokens = proceedMaxTokens;
            return this;
        }

        public Builder endMaxTokens(int endMaxTokens) {
            this.endMaxTokens = endMaxTokens;
            return this;
        }

        public Builder answerFeedbackMaxTokens(int answerFeedbackMaxTokens) {
            this.answerFeedbackMaxTokens = answerFeedbackMaxTokens;
            return this;
        }

        public Builder resumeQuestionMaxTokens(int resumeQuestionMaxTokens) {
            this.resumeQuestionMaxTokens = resumeQuestionMaxTokens;
            return this;
        }

        public Builder resumeEvaluationMaxTokens(int resumeEvaluationMaxTokens) {
            this.resumeEvaluationMaxTokens = resumeEvaluationMaxTokens;
            return this;
        }

        public Builder evaluationTemperature(float evaluationTemperature) {
            this.evaluationTemperature = evaluationTemperature;
            return this;
        }

        public Builder generationTemperature(float generationTemperature) {
            this.generationTemperature = generationTemperature;
            return this;
        }

        public Builder feedbackTemperature(float feedbackTemperature) {
            this.feedbackTemperature = feedbackTemperature;
            return this;
        }

        public Builder systemDesignRequirementMaxTokens(int systemDesignRequirementMaxTokens) {
            this.systemDesignRequirementMaxTokens = systemDesignRequirementMaxTokens;
            return this;
        }

        public BedrockConverseProperties build() {
            return new BedrockConverseProperties(modelId, proceedMaxTokens, endMaxTokens, answerFeedbackMaxTokens,
                    resumeQuestionMaxTokens, resumeEvaluationMaxTokens, evaluationTemperature, generationTemperature,
                    feedbackTemperature, systemDesignRequirementMaxTokens);
        }
    }
}
