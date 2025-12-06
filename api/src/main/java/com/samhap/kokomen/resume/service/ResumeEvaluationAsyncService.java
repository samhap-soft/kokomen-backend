package com.samhap.kokomen.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.resume.external.ResumeGptClient;
import com.samhap.kokomen.resume.external.ResumeInvokeFlowRequestFactory;
import com.samhap.kokomen.resume.service.dto.NonMemberResumeEvaluationData;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowResponseStream;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@Slf4j
@Service
public class ResumeEvaluationAsyncService {

    private static final String REDIS_KEY_PREFIX = "resume:evaluation:nonmember:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(5);

    private final ResumeEvaluationPersistenceService resumeEvaluationPersistenceService;
    private final RedisService redisService;
    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final ResumeGptClient resumeGptClient;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;

    public ResumeEvaluationAsyncService(
            ResumeEvaluationPersistenceService resumeEvaluationPersistenceService,
            RedisService redisService,
            BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient,
            ResumeGptClient resumeGptClient,
            ObjectMapper objectMapper,
            @Qualifier("resumeEvaluationExecutor")
            ThreadPoolTaskExecutor executor
    ) {
        this.resumeEvaluationPersistenceService = resumeEvaluationPersistenceService;
        this.redisService = redisService;
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
        this.resumeGptClient = resumeGptClient;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public void evaluateMemberAsync(Long evaluationId, ResumeEvaluationRequest request) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        InvokeFlowRequest flowRequest = ResumeInvokeFlowRequestFactory.createResumeEvaluationFlowRequest(request);

        bedrockAgentRuntimeAsyncClient.invokeFlow(
                flowRequest,
                createMemberEvaluationResponseHandler(evaluationId, request, mdcContext)
        );
    }

    private InvokeFlowResponseHandler createMemberEvaluationResponseHandler(
            Long evaluationId, ResumeEvaluationRequest request, Map<String, String> mdcContext) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() ->
                                handleMemberBedrockResponse(event, evaluationId, request, mdcContext))))
                .onError(ex ->
                        executor.execute(() ->
                                handleMemberBedrockError(ex, evaluationId, request, mdcContext)))
                .build();
    }

    private void handleMemberBedrockResponse(FlowResponseStream event, Long evaluationId,
                                             ResumeEvaluationRequest request, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            if (event instanceof FlowOutputEvent outputEvent) {
                String jsonPayload = outputEvent.content().document().toString();
                ResumeEvaluationResponse response = parseResponse(jsonPayload);
                resumeEvaluationPersistenceService.updateCompleted(evaluationId, response);
            }
        } catch (Exception e) {
            log.error("Bedrock 응답 처리 실패, GPT 폴백 시도 - evaluationId: {}", evaluationId, e);
            fallbackToGptForMember(evaluationId, request, mdcContext);
        } finally {
            MDC.clear();
        }
    }

    private void handleMemberBedrockError(Throwable ex, Long evaluationId,
                                          ResumeEvaluationRequest request, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            log.error("Bedrock 호출 실패, GPT 폴백 시도 - evaluationId: {}", evaluationId, ex);
            fallbackToGptForMember(evaluationId, request, mdcContext);
        } finally {
            MDC.clear();
        }
    }

    private void fallbackToGptForMember(Long evaluationId, ResumeEvaluationRequest request,
                                        Map<String, String> mdcContext) {
        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                String jsonResponse = resumeGptClient.requestResumeEvaluation(request);
                ResumeEvaluationResponse response = parseResponse(jsonResponse);
                resumeEvaluationPersistenceService.updateCompleted(evaluationId, response);
            } catch (Exception e) {
                log.error("GPT 폴백 실패 - evaluationId: {}", evaluationId, e);
                resumeEvaluationPersistenceService.updateFailed(evaluationId);
            } finally {
                MDC.clear();
            }
        });
    }

    public void evaluateNonMemberAsync(String uuid, ResumeEvaluationRequest request) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String redisKey = createRedisKey(uuid);
        InvokeFlowRequest flowRequest = ResumeInvokeFlowRequestFactory.createResumeEvaluationFlowRequest(request);

        redisService.setValue(redisKey, NonMemberResumeEvaluationData.pending(request), REDIS_TTL);

        bedrockAgentRuntimeAsyncClient.invokeFlow(
                flowRequest,
                createNonMemberEvaluationResponseHandler(uuid, request, mdcContext)
        );
    }

    private InvokeFlowResponseHandler createNonMemberEvaluationResponseHandler(
            String uuid, ResumeEvaluationRequest request, Map<String, String> mdcContext) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() ->
                                handleNonMemberBedrockResponse(event, uuid, request, mdcContext))))
                .onError(ex ->
                        executor.execute(() ->
                                handleNonMemberBedrockError(ex, uuid, request, mdcContext)))
                .build();
    }

    private void handleNonMemberBedrockResponse(FlowResponseStream event, String uuid,
                                                ResumeEvaluationRequest request, Map<String, String> mdcContext) {
        String redisKey = createRedisKey(uuid);
        try {
            setMdcContext(mdcContext);
            if (event instanceof FlowOutputEvent outputEvent) {
                String jsonPayload = outputEvent.content().document().toString();
                ResumeEvaluationResponse response = parseResponse(jsonPayload);
                redisService.setValue(redisKey, NonMemberResumeEvaluationData.completed(request, response), REDIS_TTL);
            }
        } catch (Exception e) {
            log.error("Bedrock 응답 처리 실패, GPT 폴백 시도 - uuid: {}", uuid, e);
            fallbackToGptForNonMember(uuid, request, mdcContext);
        } finally {
            MDC.clear();
        }
    }

    private void handleNonMemberBedrockError(Throwable ex, String uuid,
                                             ResumeEvaluationRequest request, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            log.error("Bedrock 호출 실패, GPT 폴백 시도 - uuid: {}", uuid, ex);
            fallbackToGptForNonMember(uuid, request, mdcContext);
        } finally {
            MDC.clear();
        }
    }

    private void fallbackToGptForNonMember(String uuid, ResumeEvaluationRequest request,
                                           Map<String, String> mdcContext) {
        String redisKey = createRedisKey(uuid);
        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                String jsonResponse = resumeGptClient.requestResumeEvaluation(request);
                ResumeEvaluationResponse response = parseResponse(jsonResponse);
                redisService.setValue(redisKey, NonMemberResumeEvaluationData.completed(request, response), REDIS_TTL);
            } catch (Exception e) {
                log.error("GPT 폴백 실패 - uuid: {}", uuid, e);
                redisService.setValue(redisKey, NonMemberResumeEvaluationData.failed(request), REDIS_TTL);
            } finally {
                MDC.clear();
            }
        });
    }

    private ResumeEvaluationResponse parseResponse(String jsonResponse) {
        try {
            String cleanedJson = unwrapJsonString(jsonResponse);
            return objectMapper.readValue(cleanedJson, ResumeEvaluationResponse.class);
        } catch (JsonProcessingException e) {
            log.error("이력서 평가 응답 파싱 실패: {}", jsonResponse, e);
            throw new BadRequestException("이력서 평가 응답을 파싱하는데 실패했습니다.");
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

    private void setMdcContext(Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }

    public static String createRedisKey(String uuid) {
        return REDIS_KEY_PREFIX + uuid;
    }
}
