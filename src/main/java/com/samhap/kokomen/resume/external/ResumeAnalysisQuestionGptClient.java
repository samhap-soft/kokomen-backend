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
            return objectMapper.readValue(
                            ResumeAnalysisGptResponses.unwrapJsonString(arguments),
                            ResumeAnalysisQuestionsFlatResponse.class)
                    .toResult();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GPT 이력서 분석 질문 응답 파싱에 실패했습니다.", e);
        }
    }

    @Override
    protected void validateResponse(Object response) {
        ResumeAnalysisGptResponses.validate(response);
    }
}
