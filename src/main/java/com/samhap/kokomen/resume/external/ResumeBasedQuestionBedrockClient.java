package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.external.dto.response.QuestionResponseWrapper;
import com.samhap.kokomen.resume.external.dto.ResumeBedrockRequestFactory;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@ExecutionTimer
@Component
public class ResumeBasedQuestionBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    public ResumeBasedQuestionBedrockClient(
            BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    public List<GeneratedQuestionDto> generateQuestions(String resumeText, String portfolioText, String jobCareer) {
        ConverseResponse response = converseClient.converse(
                ResumeBedrockRequestFactory.createQuestionGenerationSystem(),
                ResumeBedrockRequestFactory.createQuestionGenerationMessages(resumeText, portfolioText, jobCareer),
                ResumeBedrockRequestFactory.createQuestionGenerationToolConfig(),
                properties.resumeQuestionMaxTokens());

        ToolUseBlock toolUse = converseClient.extractToolUse(response,
                ResumeBedrockRequestFactory.QUESTION_GENERATION_TOOL_NAME);
        QuestionResponseWrapper wrapper = converseClient.parseToolInput(toolUse, QuestionResponseWrapper.class);
        return wrapper.questions();
    }
}
