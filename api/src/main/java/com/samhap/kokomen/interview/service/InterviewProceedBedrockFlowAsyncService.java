package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.InterviewProceedResult;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.request.InterviewInvokeFlowRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.member.service.MemberService;
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
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@Slf4j
@Service
public class InterviewProceedBedrockFlowAsyncService {

    private final InterviewProceedService interviewProceedService;
    private final QuestionService questionService;
    private final MemberService memberService;
    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final RedisService redisService;
    private final ThreadPoolTaskExecutor executor;

    public InterviewProceedBedrockFlowAsyncService(
            InterviewProceedService interviewProceedService,
            QuestionService questionService,
            MemberService memberService,
            BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient,
            RedisService redisService,
            @Qualifier("bedrockFlowCallbackExecutor")
            ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.interviewProceedService = interviewProceedService;
        this.questionService = questionService;
        this.memberService = memberService;
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
        this.redisService = redisService;
        this.executor = threadPoolTaskExecutor;
    }

    public void proceedInterviewByBedrockFlowAsync(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());

        try {
            bedrockAgentRuntimeAsyncClient.invokeFlow(InterviewInvokeFlowRequestFactory.createInterviewProceedInvokeFlowRequest(questionAndAnswers),
                    createInterviewProceedInvokeFlowResponseHandler(memberId, questionAndAnswers, interviewId, lockKey, interviewProceedStateKey, mdcContext));
            redisService.setValue(interviewProceedStateKey, LlmProceedState.PENDING.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.releaseLock(lockKey);
            throw e;
        }
    }

    private InvokeFlowResponseHandler createInterviewProceedInvokeFlowResponseHandler(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId,
                                                                                      String lockKey, String interviewProceedStateKey,
                                                                                      Map<String, String> mdcContext) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() ->
                                callbackInterviewProceedBedrockFlow(event, memberId, questionAndAnswers, interviewId, lockKey, interviewProceedStateKey,
                                        mdcContext))))
                .onError(ex ->
                        executor.execute(() -> handleInterviewProceedBedrockFlowException(ex, lockKey, interviewProceedStateKey, mdcContext)))
                .build();
    }

    // TODO: FlowFailureEvent 이 있던데 베드락 흐름 실패 시 어떻게 처리해야하는지 다시 확인하기
    private void callbackInterviewProceedBedrockFlow(FlowResponseStream event, Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId,
                                                     String lockKey, String interviewProceedStateKey, Map<String, String> mdcContext) {
        if (event instanceof FlowOutputEvent outputEvent) {
            callbackInterviewProceedBedrockFlow(outputEvent, memberId, questionAndAnswers, interviewId, lockKey, interviewProceedStateKey, mdcContext);
        }
    }

    private void handleInterviewProceedBedrockFlowException(Throwable ex, String lockKey, String interviewProceedStateKey, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            log.error("Bedrock API 호출 실패 - {}", interviewProceedStateKey, ex);
            redisService.releaseLock(lockKey);
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
        } finally {
            MDC.clear();
        }
    }

    private void callbackInterviewProceedBedrockFlow(FlowOutputEvent outputEvent, Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId,
                                                     String lockKey, String interviewProceedStateKey, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            String jsonPayload = outputEvent.content()
                    .document()
                    .toString();
            LlmResponse llmResponse = new BedrockResponse(jsonPayload);
            InterviewProceedResult result =
                    interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(memberId, questionAndAnswers, llmResponse, interviewId);

            if (result.isInProgress() && interviewProceedService.isVoiceMode(interviewId)) {
                questionService.createQuestionVoiceUrl(result.getNextQuestion());
                memberService.useToken(memberId);
            }

            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));

            Answer curAnswer = result.getCurAnswer();
            requestAndSaveAnswerFeedbackAsync(questionAndAnswers, mdcContext, curAnswer.getAnswerRank(), curAnswer.getId());
        } catch (Exception e) {
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
            throw e;
        } finally {
            redisService.releaseLock(lockKey);
            MDC.clear();
        }
    }

    // TODO: 답변 피드백 요청 또는 저장 실패 시 처리 고려하기
    private void requestAndSaveAnswerFeedbackAsync(QuestionAndAnswers questionAndAnswers, Map<String, String> mdcContext,
                                                   AnswerRank curAnswerRank, Long curAnswerId) {
        try {
            bedrockAgentRuntimeAsyncClient.invokeFlow(
                    InterviewInvokeFlowRequestFactory.createAnswerFeedbackInvokeFlowRequest(questionAndAnswers, curAnswerRank),
                    createAnswerFeedbackInvokeFlowResponseHandler(curAnswerId, mdcContext));
        } catch (Exception e) {
            log.error("답변 피드백 베드락 흐름 요청 실패 curAnswerId={}", curAnswerId, e);
        }
    }

    private InvokeFlowResponseHandler createAnswerFeedbackInvokeFlowResponseHandler(Long curAnswerId, Map<String, String> mdcContext) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() -> callbackAnswerFeedbackBedrockFlow(event, curAnswerId, mdcContext))))
                .onError(ex ->
                        executor.execute(() -> handleAnswerFeedbackBedrockFlowException(ex, curAnswerId, mdcContext)))
                .build();
    }

    private void callbackAnswerFeedbackBedrockFlow(FlowResponseStream event, Long curAnswerId, Map<String, String> mdcContext) {
        if (event instanceof FlowOutputEvent outputEvent) {
            callbackAnswerFeedbackBedrockFlow(outputEvent, curAnswerId, mdcContext);
        }
    }

    private void callbackAnswerFeedbackBedrockFlow(FlowOutputEvent outputEvent, Long curAnswerId, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            String curAnswerFeedback = outputEvent.content()
                    .document()
                    .asString();
            interviewProceedService.saveAnswerFeedback(curAnswerId, curAnswerFeedback);
        } finally {
            MDC.clear();
        }
    }

    private void handleAnswerFeedbackBedrockFlowException(Throwable ex, Long curAnswerId, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            log.error("Bedrock API 호출 실패 - curAnswerId={}", curAnswerId, ex);
        } finally {
            MDC.clear();
        }
    }

    private void setMdcContext(Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }
}
