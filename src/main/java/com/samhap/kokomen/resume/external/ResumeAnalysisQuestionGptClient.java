package com.samhap.kokomen.resume.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.BaseGptClient;
import com.samhap.kokomen.global.external.gpt.GptProperties;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionGptRequest;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionResult;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionsFlatResponse;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponse;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponseMessage;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@ExecutionTimer
@Component
public class ResumeAnalysisQuestionGptClient extends BaseGptClient {

    private final ObjectMapper objectMapper;

    public ResumeAnalysisQuestionGptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            GptProperties gptProperties
    ) {
        super(ResumeAnalysisGptTimeouts.apply(builder), objectMapper, gptProperties);
        this.objectMapper = objectMapper;
    }

    public ResumeAnalysisQuestionResult generateQuestions(ResumeAnalysisQuestionCallCommand command) {
        ResumeAnalysisQuestionGptRequest request = ResumeAnalysisQuestionGptRequest.create(
                command, gptProperties.generationTemperature());
        ResumeGptResponse gptResponse = executeRequest(request, ResumeGptResponse.class);
        ToolCall toolCall = gptResponse.choices().get(0).message().toolCalls().get(0);
        return parseQuestions(toolCall.function().arguments());
    }

    private ResumeAnalysisQuestionResult parseQuestions(String arguments) {
        try {
            return objectMapper.readValue(unwrapJsonString(arguments), ResumeAnalysisQuestionsFlatResponse.class)
                    .toResult();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GPT 이력서 분석 질문 응답 파싱에 실패했습니다.", e);
        }
    }

    private String unwrapJsonString(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        return json;
    }

    @Override
    protected void validateResponse(Object response) {
        if (response == null) {
            throw new ExternalApiException("GPT API로부터 유효한 응답을 받지 못했습니다.");
        }
        if (!(response instanceof ResumeGptResponse gptResponse)) {
            throw new ExternalApiException(
                    "GPT API로부터 예기치 않은 타입의 응답을 받았습니다: " + response.getClass().getName());
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
