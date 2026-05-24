package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.BaseGptClient;
import com.samhap.kokomen.global.external.gpt.GptProperties;
import com.samhap.kokomen.interview.external.dto.request.ResumeBasedQuestionGptRequest;
import com.samhap.kokomen.interview.external.dto.response.ResumeBasedQuestionGptResponse;
import com.samhap.kokomen.interview.external.dto.response.ResumeBasedQuestionGptResponseMessage;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@ExecutionTimer
@Component
public class ResumeBasedQuestionGptClient extends BaseGptClient {

    public ResumeBasedQuestionGptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            GptProperties gptProperties
    ) {
        super(builder, objectMapper, gptProperties);
    }

    public String generateQuestions(String resumeText, String portfolioText, String jobCareer) {
        ResumeBasedQuestionGptRequest request = ResumeBasedQuestionGptRequest.create(
                resumeText,
                portfolioText,
                jobCareer,
                gptProperties.generationTemperature()
        );
        ResumeBasedQuestionGptResponse response = executeRequest(request, ResumeBasedQuestionGptResponse.class);
        ResumeBasedQuestionGptResponseMessage message = response.choices().get(0).message();
        ToolCall toolCall = message.toolCalls().get(0);
        return toolCall.function().arguments();
    }

    @Override
    protected void validateResponse(Object response) {
        if (response == null) {
            throw new ExternalApiException("GPT API로부터 유효한 응답을 받지 못했습니다.");
        }
        if (!(response instanceof ResumeBasedQuestionGptResponse gptResponse)) {
            throw new ExternalApiException("GPT API로부터 예기치 않은 타입의 응답을 받았습니다: " + response.getClass().getName());
        }
        if (gptResponse.choices() == null || gptResponse.choices().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 choices가 없습니다.");
        }
        ResumeBasedQuestionGptResponseMessage message = gptResponse.choices().get(0).message();
        if (message == null) {
            throw new ExternalApiException("GPT API 응답에 message가 없습니다.");
        }
        if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 tool_calls가 없습니다.");
        }
    }
}
