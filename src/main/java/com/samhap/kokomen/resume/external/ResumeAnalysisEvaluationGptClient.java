package com.samhap.kokomen.resume.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.BaseGptClient;
import com.samhap.kokomen.global.external.gpt.GptProperties;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisEvaluationFlatResponse;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisEvaluationGptRequest;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@ExecutionTimer
@Component
public class ResumeAnalysisEvaluationGptClient extends BaseGptClient {

    private final ObjectMapper objectMapper;

    public ResumeAnalysisEvaluationGptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            GptProperties gptProperties
    ) {
        super(ResumeAnalysisGptTimeouts.apply(builder), objectMapper, gptProperties);
        this.objectMapper = objectMapper;
    }

    public ResumeAnalysisEvaluation evaluate(ResumeAnalysisCommand command) {
        ResumeAnalysisEvaluationGptRequest request = ResumeAnalysisEvaluationGptRequest.create(
                command, gptProperties.evaluationTemperature());
        ResumeGptResponse gptResponse = executeRequest(request, ResumeGptResponse.class);
        ToolCall toolCall = gptResponse.choices().get(0).message().toolCalls().get(0);
        return parseEvaluation(toolCall.function().arguments(), command.jdProvided());
    }

    /**
     * ExternalApiException을 먼저 재던지는 이유: toEvaluation이 _score 누락 같은 응답 변형을
     * 구체적 메시지가 담긴 ExternalApiException으로 이미 변환하므로, 여기서 무조건 재포장하면
     * 그 메시지가 일반 문구로 덮인다. 질문 클라이언트의 parseQuestions와 같은 형태다.
     */
    private ResumeAnalysisEvaluation parseEvaluation(String arguments, boolean jdProvided) {
        try {
            return objectMapper.readValue(
                            ResumeAnalysisGptResponses.unwrapJsonString(arguments),
                            ResumeAnalysisEvaluationFlatResponse.class)
                    .toEvaluation(jdProvided);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GPT 이력서 분석 평가 응답 파싱에 실패했습니다.", e);
        }
    }

    @Override
    protected void validateResponse(Object response) {
        ResumeAnalysisGptResponses.validate(response);
    }
}
