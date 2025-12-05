package com.samhap.kokomen.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.resume.external.BedrockFlowClient;
import com.samhap.kokomen.resume.external.ResumeGptClient;
import com.samhap.kokomen.resume.external.ResumeInvokeFlowRequestFactory;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeEvaluationService {

    private final BedrockFlowClient bedrockFlowClient;
    private final ResumeGptClient resumeGptClient;
    private final ObjectMapper objectMapper;

    public ResumeEvaluationResponse evaluate(ResumeEvaluationRequest request) {
        try {
            return evaluateByBedrockFlow(request);
        } catch (Exception e) {
            log.error("Bedrock Flow 호출 실패, GPT 폴백 시도", e);
            return evaluateByGpt(request);
        }
    }

    private ResumeEvaluationResponse evaluateByBedrockFlow(ResumeEvaluationRequest request) {
        InvokeFlowRequest flowRequest = ResumeInvokeFlowRequestFactory.createResumeEvaluationFlowRequest(request);
        String jsonResponse = bedrockFlowClient.invokeFlow(flowRequest);
        return parseResponse(jsonResponse);
    }

    private ResumeEvaluationResponse evaluateByGpt(ResumeEvaluationRequest request) {
        String jsonResponse = resumeGptClient.requestResumeEvaluation(request);
        return parseResponse(jsonResponse);
    }

    private ResumeEvaluationResponse parseResponse(String jsonResponse) {
        try {
            String cleanedJson = unwrapJsonString(jsonResponse);
            return objectMapper.readValue(cleanedJson, ResumeEvaluationResponse.class);
        } catch (JsonProcessingException e) {
            log.error("이력서 평가 응답 파싱 실패: {}", jsonResponse, e);
            throw new ExternalApiException("이력서 평가 응답을 파싱하는데 실패했습니다.", e);
        }
    }

    private String unwrapJsonString(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                return objectMapper.readValue(trimmed, String.class);
            } catch (JsonProcessingException e) {
                return json;
            }
        }
        return json;
    }
}
