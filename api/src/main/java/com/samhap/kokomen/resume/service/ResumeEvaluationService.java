package com.samhap.kokomen.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.external.BedrockFlowClient;
import com.samhap.kokomen.resume.external.ResumeGptClient;
import com.samhap.kokomen.resume.external.ResumeInvokeFlowRequestFactory;
import com.samhap.kokomen.resume.repository.ResumeEvaluationRepository;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeEvaluationService {

    private final ResumeEvaluationRepository resumeEvaluationRepository;
    private final BedrockFlowClient bedrockFlowClient;
    private final ResumeGptClient resumeGptClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResumeEvaluation saveEvaluation(ResumeEvaluation evaluation) {
        return resumeEvaluationRepository.save(evaluation);
    }

    @Transactional(readOnly = true)
    public ResumeEvaluation readById(Long id) {
        return resumeEvaluationRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ResumeEvaluation> findByMemberId(Long memberId, Pageable pageable) {
        return resumeEvaluationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    @Transactional
    public void updateCompleted(Long evaluationId, ResumeEvaluationResponse response) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.complete(
                response.technicalSkills().score(),
                response.technicalSkills().reason(),
                response.technicalSkills().improvements(),
                response.projectExperience().score(),
                response.projectExperience().reason(),
                response.projectExperience().improvements(),
                response.problemSolving().score(),
                response.problemSolving().reason(),
                response.problemSolving().improvements(),
                response.careerGrowth().score(),
                response.careerGrowth().reason(),
                response.careerGrowth().improvements(),
                response.documentation().score(),
                response.documentation().reason(),
                response.documentation().improvements(),
                response.totalScore(),
                response.totalFeedback()
        );
    }

    @Transactional
    public void updateMemberResume(Long evaluationId, MemberResume memberResume, MemberPortfolio memberPortfolio) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.updateMemberResume(memberResume, memberPortfolio);
    }

    @Transactional
    public void updateFailed(Long evaluationId) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.fail();
    }

    // TODO: 이력서 평가가 비동기로 전환 완료되면 삭제하기
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
            String unwrapped = trimmed.substring(1, trimmed.length() - 1);
            return unwrapped.replace("\\\"", "\"");
        }
        return json;
    }
}
