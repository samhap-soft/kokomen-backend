package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.BedrockFlowAsyncClient;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowResponseStream;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@Slf4j
@Service
public class InterviewProceedBedrockFlowAsyncService {

    private final InterviewProceedService interviewProceedService;
    private final BedrockFlowAsyncClient bedrockFlowAsyncClient;
    private final RedisService redisService;
    private final ThreadPoolTaskExecutor executor;

    public InterviewProceedBedrockFlowAsyncService(
            InterviewProceedService interviewProceedService,
            BedrockFlowAsyncClient bedrockFlowAsyncClient,
            RedisService redisService,
            @Qualifier("bedrockFlowCallbackExecutor")
            ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.interviewProceedService = interviewProceedService;
        this.bedrockFlowAsyncClient = bedrockFlowAsyncClient;
        this.redisService = redisService;
        this.executor = threadPoolTaskExecutor;
    }

    public void proceedInterviewByBedrockFlowAsync(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());

        try {
            InvokeFlowResponseHandler invokeFlowResponseHandler = InvokeFlowResponseHandler.builder()
                    .onEventStream(publisher -> publisher.subscribe(
                            event -> executor.execute(() -> callbackBedrockFlow(event, memberId, questionAndAnswers, interviewId, lockKey, mdcContext))))
                    .onError(ex ->
                            executor.execute(() -> handleBedrockException(ex, lockKey, interviewProceedStateKey, mdcContext)))
                    .build();
            bedrockFlowAsyncClient.requestToBedrock(questionAndAnswers, invokeFlowResponseHandler);
            redisService.setValue(interviewProceedStateKey, LlmProceedState.PENDING.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.releaseLock(lockKey);
            throw e;
        }
    }

    private void callbackBedrockFlow(FlowResponseStream event, Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId, String lockKey,
                                     Map<String, String> mdcContext) {
        if (event instanceof FlowOutputEvent outputEvent) {
            callbackBedrockFlow(outputEvent, memberId, questionAndAnswers, interviewId, lockKey, mdcContext);
        }
    }

    private void callbackBedrockFlow(FlowOutputEvent outputEvent, Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId, String lockKey,
                                     Map<String, String> mdcContext) {
        try {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }

            String jsonPayload = outputEvent.content()
                    .document()
                    .toString();
            log.info("플로우 응답 : " + jsonPayload);

            LlmResponse response = new BedrockResponse(jsonPayload);
            interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(memberId, questionAndAnswers, response, interviewId);
            String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());
            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));
            redisService.releaseLock(lockKey);
        } finally {
            MDC.clear();
        }
    }

    private void handleBedrockException(Throwable ex, String lockKey, String interviewProceedStateKey, Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }

        try {
            log.error("Bedrock API 호출 실패 - {}", interviewProceedStateKey, ex);
            redisService.releaseLock(lockKey);
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
        } finally {
            MDC.clear();
        }
    }
}
