package com.samhap.kokomen.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.external.ResumeGptClient;
import com.samhap.kokomen.resume.external.ResumeInvokeFlowRequestFactory;
import com.samhap.kokomen.resume.service.dto.NonMemberResumeEvaluationData;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import com.samhap.kokomen.resume.service.dto.ResumeFileData;
import com.samhap.kokomen.resume.service.dto.TextExtractionResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    private final ResumeEvaluationService resumeEvaluationService;
    private final PdfUploadService pdfUploadService;
    private final RedisService redisService;
    private final S3Service s3Service;
    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final ResumeGptClient resumeGptClient;
    private final PdfTextExtractor pdfTextExtractor;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;

    public ResumeEvaluationAsyncService(
            ResumeEvaluationService resumeEvaluationService,
            PdfUploadService pdfUploadService,
            RedisService redisService,
            S3Service s3Service,
            BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient,
            ResumeGptClient resumeGptClient,
            PdfTextExtractor pdfTextExtractor,
            ObjectMapper objectMapper,
            @Qualifier("resumeEvaluationExecutor")
            ThreadPoolTaskExecutor executor
    ) {
        this.resumeEvaluationService = resumeEvaluationService;
        this.pdfUploadService = pdfUploadService;
        this.redisService = redisService;
        this.s3Service = s3Service;
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
        this.resumeGptClient = resumeGptClient;
        this.pdfTextExtractor = pdfTextExtractor;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public void processAndEvaluateMemberAsync(Long evaluationId, Member member,
                                              ResumeFileData resumeFileData,
                                              ResumeFileData portfolioFileData,
                                              String jobPosition,
                                              String jobDescription,
                                              String jobCareer) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                TextExtractionResult extraction = extractTexts(resumeFileData, portfolioFileData);

                if (!extraction.hasResumeText()) {
                    log.error("이력서 텍스트 추출 실패 - evaluationId: {}", evaluationId);
                    resumeEvaluationService.updateFailed(evaluationId);
                    return;
                }

                MemberResume memberResume = pdfUploadService.saveResume(resumeFileData.content(),
                        resumeFileData.filename(), member, extraction.resumeText());
                MemberPortfolio memberPortfolio = null;
                if (portfolioFileData != null && !portfolioFileData.isEmpty()) {
                    memberPortfolio = pdfUploadService.savePortfolio(portfolioFileData.content(),
                            portfolioFileData.filename(), member, extraction.portfolioText());
                }

                resumeEvaluationService.updateMemberResume(evaluationId, memberResume, memberPortfolio);

                ResumeEvaluationRequest evalRequest = new ResumeEvaluationRequest(
                        extraction.resumeText(), extraction.portfolioText(),
                        jobPosition, jobDescription, jobCareer
                );
                evaluateMemberAsync(evaluationId, evalRequest);
            } catch (Exception e) {
                log.error("회원 이력서 평가 처리 실패 - evaluationId: {}", evaluationId, e);
                resumeEvaluationService.updateFailed(evaluationId);
            } finally {
                MDC.clear();
            }
        });
    }

    public void processAndEvaluateSavedMemberAsync(Long evaluationId,
                                                   MemberResume resume,
                                                   MemberPortfolio portfolio,
                                                   String jobPosition,
                                                   String jobDescription,
                                                   String jobCareer) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);

                String resumeText = getOrExtractText(resume.getContent(), resume.getResumeUrl());
                String portfolioText = portfolio != null
                        ? getOrExtractText(portfolio.getContent(), portfolio.getPortfolioUrl())
                        : null;

                if (resumeText == null || resumeText.isBlank()) {
                    log.error("이력서 텍스트 없음 - evaluationId: {}", evaluationId);
                    resumeEvaluationService.updateFailed(evaluationId);
                    return;
                }

                ResumeEvaluationRequest evalRequest = new ResumeEvaluationRequest(
                        resumeText, portfolioText, jobPosition, jobDescription, jobCareer
                );
                evaluateMemberAsync(evaluationId, evalRequest);
            } catch (Exception e) {
                log.error("저장된 이력서 평가 처리 실패 - evaluationId: {}", evaluationId, e);
                resumeEvaluationService.updateFailed(evaluationId);
            } finally {
                MDC.clear();
            }
        });
    }

    private String getOrExtractText(String content, String url) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        try {
            byte[] pdfBytes = s3Service.downloadFileFromUrl(url);
            return pdfTextExtractor.extractText(pdfBytes);
        } catch (Exception e) {
            log.error("S3에서 PDF 다운로드/추출 실패 - url: {}", url, e);
            return null;
        }
    }

    public void processAndEvaluateNonMemberAsync(String uuid,
                                                 ResumeFileData resumeFileData,
                                                 ResumeFileData portfolioFileData,
                                                 String jobPosition,
                                                 String jobDescription,
                                                 String jobCareer) {
        String redisKey = createRedisKey(uuid);
        saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.pending(null));

        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                TextExtractionResult extraction = extractTexts(resumeFileData, portfolioFileData);

                if (!extraction.hasResumeText()) {
                    log.error("이력서 텍스트 추출 실패 - uuid: {}", uuid);
                    saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.failed(null));
                    return;
                }

                ResumeEvaluationRequest evaluationRequest = new ResumeEvaluationRequest(
                        extraction.resumeText(), extraction.portfolioText(),
                        jobPosition, jobDescription, jobCareer
                );
                evaluateNonMemberAsync(uuid, evaluationRequest);
            } catch (Exception e) {
                log.error("비회원 이력서 평가 처리 실패 - uuid: {}", uuid, e);
                saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.failed(null));
            } finally {
                MDC.clear();
            }
        });
    }

    private TextExtractionResult extractTexts(ResumeFileData resumeFileData, ResumeFileData portfolioFileData) {
        CompletableFuture<String> resumeFuture = CompletableFuture.supplyAsync(
                () -> extractTextSafely(resumeFileData), executor);

        CompletableFuture<String> portfolioFuture = CompletableFuture.supplyAsync(() -> {
            if (portfolioFileData == null || portfolioFileData.isEmpty()) {
                return null;
            }
            return extractTextSafely(portfolioFileData);
        }, executor);

        return resumeFuture.thenCombine(portfolioFuture, TextExtractionResult::of).join();
    }

    private String extractTextSafely(ResumeFileData fileData) {
        try {
            return pdfTextExtractor.extractText(fileData.content());
        } catch (Exception e) {
            log.error("PDF 텍스트 추출 실패", e);
            return null;
        }
    }

    private void evaluateMemberAsync(Long evaluationId, ResumeEvaluationRequest request) {
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
                resumeEvaluationService.updateCompleted(evaluationId, response);
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
                resumeEvaluationService.updateCompleted(evaluationId, response);
            } catch (Exception e) {
                log.error("GPT 폴백 실패 - evaluationId: {}", evaluationId, e);
                resumeEvaluationService.updateFailed(evaluationId);
            } finally {
                MDC.clear();
            }
        });
    }

    private void evaluateNonMemberAsync(String uuid, ResumeEvaluationRequest request) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String redisKey = createRedisKey(uuid);
        InvokeFlowRequest flowRequest = ResumeInvokeFlowRequestFactory.createResumeEvaluationFlowRequest(request);

        saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.pending(request));

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
                saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.completed(request, response));
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
                saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.completed(request, response));
            } catch (Exception e) {
                log.error("GPT 폴백 실패 - uuid: {}", uuid, e);
                saveNonMemberDataToRedis(redisKey, NonMemberResumeEvaluationData.failed(request));
            } finally {
                MDC.clear();
            }
        });
    }

    private void saveNonMemberDataToRedis(String redisKey, NonMemberResumeEvaluationData data) {
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            redisService.setValue(redisKey, jsonData, REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.error("비회원 평가 데이터 직렬화 실패 - redisKey: {}", redisKey, e);
            throw new BadRequestException("비회원 평가 데이터 저장에 실패했습니다.");
        }
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
