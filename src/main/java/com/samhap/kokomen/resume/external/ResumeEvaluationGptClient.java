package com.samhap.kokomen.resume.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.BaseGptClient;
import com.samhap.kokomen.resume.external.dto.ResumeGptRequest;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@ExecutionTimer
@Component
public class ResumeEvaluationGptClient extends BaseGptClient {

    public ResumeEvaluationGptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${open-ai.api-key}") String gptApiKey
    ) {
        super(builder, objectMapper, gptApiKey);
    }

    public String requestResumeEvaluation(ResumeEvaluationRequest request) {
        ResumeGptRequest gptRequest = ResumeGptRequest.create(request);
        ResumeGptResponse gptResponse = executeRequest(gptRequest, ResumeGptResponse.class);
        return gptResponse.choices().get(0).message().content();
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
    }
}
