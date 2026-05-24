package com.samhap.kokomen.resume.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.BaseGptClient;
import com.samhap.kokomen.global.external.gpt.GptProperties;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import com.samhap.kokomen.resume.external.dto.ResumeGptRequest;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponse;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponseMessage;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@ExecutionTimer
@Component
public class ResumeEvaluationGptClient extends BaseGptClient {

    public ResumeEvaluationGptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            GptProperties gptProperties
    ) {
        super(builder, objectMapper, gptProperties);
    }

    public String requestResumeEvaluation(ResumeEvaluationRequest request) {
        ResumeGptRequest gptRequest = ResumeGptRequest.create(request, gptProperties.evaluationTemperature());
        ResumeGptResponse gptResponse = executeRequest(gptRequest, ResumeGptResponse.class);
        ResumeGptResponseMessage message = gptResponse.choices().get(0).message();
        ToolCall toolCall = message.toolCalls().get(0);
        return toolCall.function().arguments();
    }

    @Override
    protected void validateResponse(Object response) {
        if (response == null) {
            throw new ExternalApiException("GPT API로부터 유효한 응답을 받지 못했습니다.");
        }
        if (!(response instanceof ResumeGptResponse gptResponse)) {
            throw new ExternalApiException("GPT API로부터 예기치 않은 타입의 응답을 받았습니다: " + response.getClass().getName());
        }
        if (gptResponse.choices() == null || gptResponse.choices().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 choices가 없습니다.");
        }
        ResumeGptResponseMessage message = gptResponse.choices().get(0).message();
        if (message == null) {
            throw new ExternalApiException("GPT API 응답에 message가 없습니다.");
        }
        if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 tool_calls가 없습니다.");
        }
    }
}
