package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.InterviewProceedResult;
import com.samhap.kokomen.interview.domain.InterviewProceedState;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.request.InterviewInvokeFlowRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
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
    private final TokenFacadeService tokenFacadeService;
    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final RedisService redisService;
    private final ThreadPoolTaskExecutor executor;
    private final ThreadPoolTaskExecutor gptCallbackExecutor;
    private final GptClient gptClient;

    public InterviewProceedBedrockFlowAsyncService(
            InterviewProceedService interviewProceedService,
            QuestionService questionService,
            TokenFacadeService tokenFacadeService,
            BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient,
            RedisService redisService,
            @Qualifier("bedrockFlowCallbackExecutor")
            ThreadPoolTaskExecutor bedrockFlowCallbackExecutor,
            @Qualifier("gptCallbackExecutor")
            ThreadPoolTaskExecutor gptCallbackExecutor,
            GptClient gptClient) {
        this.interviewProceedService = interviewProceedService;
        this.questionService = questionService;
        this.tokenFacadeService = tokenFacadeService;
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
        this.redisService = redisService;
        this.executor = bedrockFlowCallbackExecutor;
        this.gptCallbackExecutor = gptCallbackExecutor;
        this.gptClient = gptClient;
    }

    public void proceedInterviewByBedrockFlowAsync(Long memberId, QuestionAndAnswers questionAndAnswers,
                                                   Long interviewId) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId,
                questionAndAnswers.readCurQuestion().getId());

        bedrockAgentRuntimeAsyncClient.invokeFlow(
                InterviewInvokeFlowRequestFactory.createInterviewProceedInvokeFlowRequest(questionAndAnswers),
                createInterviewProceedInvokeFlowResponseHandler(memberId, questionAndAnswers, interviewId, lockKey,
                        interviewProceedStateKey, mdcContext));
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_PENDING.name(),
                Duration.ofSeconds(300));
    }

    public void proceedInterviewByGptFlowAsync(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId,
                questionAndAnswers.readCurQuestion().getId());

        try {
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_PENDING.name(),
                    Duration.ofSeconds(300));

            gptCallbackExecutor.execute(() ->
                    callbackGptFlow(memberId, questionAndAnswers, interviewId, lockKey, interviewProceedStateKey,
                            mdcContext));
        } catch (Exception e) {
            redisService.releaseLock(lockKey);
            throw e;
        }
    }

    private void callbackGptFlow(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId,
                                 String lockKey, String interviewProceedStateKey, Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);

            GptResponse response = gptClient.requestToGpt(questionAndAnswers);
            log.info("GPT 응답 받음: {}", response);

            interviewProceedService.proceedOrEndInterview(
                    memberId, questionAndAnswers, response, interviewId);

            redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(),
                    Duration.ofSeconds(300));
        } catch (Exception e) {
            log.error("GPT 실패 - {}", interviewProceedStateKey, e);
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_FAILED.name(),
                    Duration.ofSeconds(300));
        } finally {
            redisService.releaseLock(lockKey);
            MDC.clear();
        }
    }

    private InvokeFlowResponseHandler createInterviewProceedInvokeFlowResponseHandler(Long memberId,
                                                                                      QuestionAndAnswers questionAndAnswers,
                                                                                      Long interviewId,
                                                                                      String lockKey,
                                                                                      String interviewProceedStateKey,
                                                                                      Map<String, String> mdcContext) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() ->
                                callbackInterviewProceedBedrockFlow(event, memberId, questionAndAnswers, interviewId,
                                        lockKey, interviewProceedStateKey,
                                        mdcContext))))
                .onError(ex ->
                        executor.execute(
                                () -> handleInterviewProceedBedrockFlowException(ex, memberId, questionAndAnswers,
                                        interviewId, lockKey, interviewProceedStateKey, mdcContext)))
                .build();
    }

    // TODO: FlowFailureEvent 이 있던데 베드락 흐름 실패 시 어떻게 처리해야하는지 다시 확인하기
    private void callbackInterviewProceedBedrockFlow(FlowResponseStream event, Long memberId,
                                                     QuestionAndAnswers questionAndAnswers, Long interviewId,
                                                     String lockKey, String interviewProceedStateKey,
                                                     Map<String, String> mdcContext) {
        log.info("callbackInterviewProceedBedrockFlow 호출됨 interviewProceedStateKey={}", interviewProceedStateKey);
        try {
            setMdcContext(mdcContext);
            if (event instanceof FlowOutputEvent outputEvent) {
                callbackInterviewProceedBedrockFlow(outputEvent, memberId, questionAndAnswers, interviewId, lockKey,
                        interviewProceedStateKey, mdcContext);
            }
        } catch (Exception e) {
            log.error("Exception :: status: {}, message: {}, stackTrace: ", HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private void handleInterviewProceedBedrockFlowException(Throwable ex, Long memberId,
                                                            QuestionAndAnswers questionAndAnswers,
                                                            Long interviewId, String lockKey,
                                                            String interviewProceedStateKey,
                                                            Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            log.error("Bedrock API 호출 실패, GPT 폴백 시도 - {}", interviewProceedStateKey, ex);
            fallbackToGptForInterview(memberId, questionAndAnswers, interviewId, lockKey,
                    interviewProceedStateKey, mdcContext);
        } finally {
            MDC.clear();
        }
    }

    private void fallbackToGptForInterview(Long memberId, QuestionAndAnswers questionAndAnswers,
                                           Long interviewId, String lockKey,
                                           String interviewProceedStateKey,
                                           Map<String, String> mdcContext) {
        gptCallbackExecutor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                log.info("GPT 폴백 시작 - {}", interviewProceedStateKey);

                GptResponse response = gptClient.requestToGpt(questionAndAnswers);
                log.info("GPT 폴백 응답 받음 - {}: {}", interviewProceedStateKey, response);

                interviewProceedService.proceedOrEndInterview(
                        memberId, questionAndAnswers, response, interviewId);

                redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(),
                        Duration.ofSeconds(300));
            } catch (Exception e) {
                log.error("GPT 폴백 실패 - {}", interviewProceedStateKey, e);
                redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_FAILED.name(),
                        Duration.ofSeconds(300));
            } finally {
                redisService.releaseLock(lockKey);
                MDC.clear();
            }
        });
    }

    private void callbackInterviewProceedBedrockFlow(FlowOutputEvent outputEvent, Long memberId,
                                                     QuestionAndAnswers questionAndAnswers, Long interviewId,
                                                     String lockKey, String interviewProceedStateKey,
                                                     Map<String, String> mdcContext) {
        try {
            InterviewProceedResult result = submitAnswerToLlm(outputEvent, memberId, questionAndAnswers, interviewId,
                    interviewProceedStateKey, mdcContext);
            if (result.isInProgress() && interviewProceedService.isVoiceMode(interviewId)) {
                createNextQuestionTtsAndUploadToS3(memberId, interviewProceedStateKey, result);
            }
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(),
                    Duration.ofSeconds(300));
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    private InterviewProceedResult submitAnswerToLlm(FlowOutputEvent outputEvent, Long memberId,
                                                     QuestionAndAnswers questionAndAnswers,
                                                     Long interviewId, String interviewProceedStateKey,
                                                     Map<String, String> mdcContext) {
        try {
            String jsonPayload = outputEvent.content()
                    .document()
                    .toString();
            log.info("Bedrock 응답 받음 interviewProceedStateKey={}, payload={}", interviewProceedStateKey, jsonPayload);
            LlmResponse llmResponse = new BedrockResponse(jsonPayload);
            InterviewProceedResult result =
                    interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(memberId, questionAndAnswers,
                            llmResponse, interviewId);
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.TTS_PENDING.name(),
                    Duration.ofSeconds(300));
            Answer curAnswer = result.getCurAnswer();
            requestAndSaveAnswerFeedbackAsync(questionAndAnswers, mdcContext, curAnswer.getAnswerRank(),
                    curAnswer.getId());
            return result;
        } catch (Exception e) {
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_FAILED.name(),
                    Duration.ofSeconds(300));
            throw e;
        }
    }

    private void createNextQuestionTtsAndUploadToS3(Long memberId, String interviewProceedStateKey,
                                                    InterviewProceedResult result) {
        try {
            questionService.createAndUploadQuestionVoice(result.getNextQuestion());
            tokenFacadeService.useToken(memberId); // TODO: TTS는 성공했는데 useToken만 실패하는 경우 고려 필요
        } catch (Exception e) {
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.TTS_FAILED.name(),
                    Duration.ofSeconds(300));
            throw e;
        }
    }

    // TODO: 답변 피드백 요청 또는 저장 실패 시 처리 고려하기
    private void requestAndSaveAnswerFeedbackAsync(QuestionAndAnswers questionAndAnswers,
                                                   Map<String, String> mdcContext,
                                                   AnswerRank curAnswerRank, Long curAnswerId) {
        try {
            bedrockAgentRuntimeAsyncClient.invokeFlow(
                    InterviewInvokeFlowRequestFactory.createAnswerFeedbackInvokeFlowRequest(questionAndAnswers,
                            curAnswerRank),
                    createAnswerFeedbackInvokeFlowResponseHandler(curAnswerId, mdcContext));
        } catch (Exception e) {
            log.error("답변 피드백 베드락 흐름 요청 실패 curAnswerId={}", curAnswerId, e);
        }
    }

    private InvokeFlowResponseHandler createAnswerFeedbackInvokeFlowResponseHandler(Long curAnswerId,
                                                                                    Map<String, String> mdcContext) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() -> callbackAnswerFeedbackBedrockFlow(event, curAnswerId, mdcContext))))
                .onError(ex ->
                        executor.execute(() -> handleAnswerFeedbackBedrockFlowException(ex, curAnswerId, mdcContext)))
                .build();
    }

    private void callbackAnswerFeedbackBedrockFlow(FlowResponseStream event, Long curAnswerId,
                                                   Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            if (event instanceof FlowOutputEvent outputEvent) {
                callbackAnswerFeedbackBedrockFlow(outputEvent, curAnswerId);
            }
        } catch (Exception e) {
            log.error("Exception :: status: {}, message: {}, stackTrace: ", HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private void callbackAnswerFeedbackBedrockFlow(FlowOutputEvent outputEvent, Long curAnswerId) {
        String curAnswerFeedback = outputEvent.content()
                .document()
                .asString();
        interviewProceedService.saveAnswerFeedback(curAnswerId, curAnswerFeedback);
    }

    private void handleAnswerFeedbackBedrockFlowException(Throwable ex, Long curAnswerId,
                                                          Map<String, String> mdcContext) {
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
