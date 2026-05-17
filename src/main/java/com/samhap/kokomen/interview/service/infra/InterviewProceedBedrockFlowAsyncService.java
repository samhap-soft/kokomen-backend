package com.samhap.kokomen.interview.service.infra;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.external.AnswerFeedbackBedrockClient;
import com.samhap.kokomen.interview.external.InterviewProceedBedrockClient;
import com.samhap.kokomen.interview.external.InterviewProceedGptClient;
import com.samhap.kokomen.interview.external.dto.response.BedrockConverseResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import com.samhap.kokomen.interview.service.core.InterviewProceedService;
import com.samhap.kokomen.interview.service.question.QuestionService;
import com.samhap.kokomen.interview.tool.InterviewProceedResult;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterviewProceedBedrockFlowAsyncService {

    private final InterviewProceedService interviewProceedService;
    private final QuestionService questionService;
    private final TokenFacadeService tokenFacadeService;
    private final InterviewProceedBedrockClient interviewProceedBedrockClient;
    private final AnswerFeedbackBedrockClient answerFeedbackBedrockClient;
    private final RedisService redisService;
    private final ThreadPoolTaskExecutor executor;
    private final ThreadPoolTaskExecutor gptCallbackExecutor;
    private final InterviewProceedGptClient interviewProceedGptClient;

    public InterviewProceedBedrockFlowAsyncService(
            InterviewProceedService interviewProceedService,
            QuestionService questionService,
            TokenFacadeService tokenFacadeService,
            InterviewProceedBedrockClient interviewProceedBedrockClient,
            AnswerFeedbackBedrockClient answerFeedbackBedrockClient,
            RedisService redisService,
            @Qualifier("bedrockFlowCallbackExecutor")
            ThreadPoolTaskExecutor bedrockFlowCallbackExecutor,
            @Qualifier("gptCallbackExecutor")
            ThreadPoolTaskExecutor gptCallbackExecutor,
            InterviewProceedGptClient interviewProceedGptClient) {
        this.interviewProceedService = interviewProceedService;
        this.questionService = questionService;
        this.tokenFacadeService = tokenFacadeService;
        this.interviewProceedBedrockClient = interviewProceedBedrockClient;
        this.answerFeedbackBedrockClient = answerFeedbackBedrockClient;
        this.redisService = redisService;
        this.executor = bedrockFlowCallbackExecutor;
        this.gptCallbackExecutor = gptCallbackExecutor;
        this.interviewProceedGptClient = interviewProceedGptClient;
    }

    public void proceedInterviewByBedrockFlowAsync(Long memberId, QuestionAndAnswers questionAndAnswers,
                                                   Long interviewId, String lockKey, String lockValue) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String interviewProceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interviewId,
                questionAndAnswers.readCurQuestion().getId());

        redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_PENDING.name(),
                Duration.ofSeconds(300));

        executor.execute(() -> processBedrockProceed(memberId, questionAndAnswers, interviewId, lockKey, lockValue,
                interviewProceedStateKey, mdcContext));
    }

    public void proceedInterviewByGptFlowAsync(Long memberId, QuestionAndAnswers questionAndAnswers,
                                               Long interviewId, String lockKey, String lockValue) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        String interviewProceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interviewId,
                questionAndAnswers.readCurQuestion().getId());

        try {
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_PENDING.name(),
                    Duration.ofSeconds(300));

            gptCallbackExecutor.execute(() ->
                    callbackGptFlow(memberId, questionAndAnswers, interviewId, lockKey, lockValue,
                            interviewProceedStateKey, mdcContext));
        } catch (Exception e) {
            redisService.releaseLockSafely(lockKey, lockValue);
            throw e;
        }
    }

    private void processBedrockProceed(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId,
                                       String lockKey, String lockValue, String interviewProceedStateKey,
                                       Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);
            BedrockConverseResponse llmResponse = interviewProceedBedrockClient.requestToBedrock(questionAndAnswers);
            log.info("Bedrock 응답 받음 interviewProceedStateKey={}", interviewProceedStateKey);

            InterviewProceedResult result = interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(
                    memberId, questionAndAnswers, llmResponse, interviewId);

            if (result.isInProgress() && interviewProceedService.isVoiceMode(interviewId)) {
                redisService.setValue(interviewProceedStateKey, InterviewProceedState.TTS_PENDING.name(),
                        Duration.ofSeconds(300));
                createNextQuestionTtsAndUploadToS3(memberId, interviewProceedStateKey, result);
            }

            if (result.isInProgress()) {
                requestAndSaveAnswerFeedbackAsync(questionAndAnswers, mdcContext,
                        result.getCurAnswer().getAnswerRank(), result.getCurAnswer().getId());
            }

            redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(),
                    Duration.ofSeconds(300));
            redisService.releaseLockSafely(lockKey, lockValue);
        } catch (Exception ex) {
            log.error("Bedrock API 호출 실패, GPT 폴백 시도 - {}", interviewProceedStateKey, ex);
            redisService.setValue(interviewProceedStateKey, InterviewProceedState.LLM_FAILED.name(),
                    Duration.ofSeconds(300));
            fallbackToGptForInterview(memberId, questionAndAnswers, interviewId, lockKey, lockValue,
                    interviewProceedStateKey, mdcContext);
        } finally {
            MDC.clear();
        }
    }

    private void callbackGptFlow(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId,
                                 String lockKey, String lockValue, String interviewProceedStateKey,
                                 Map<String, String> mdcContext) {
        try {
            setMdcContext(mdcContext);

            GptResponse response = interviewProceedGptClient.requestToGpt(questionAndAnswers);
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
            redisService.releaseLockSafely(lockKey, lockValue);
            MDC.clear();
        }
    }

    private void fallbackToGptForInterview(Long memberId, QuestionAndAnswers questionAndAnswers,
                                           Long interviewId, String lockKey, String lockValue,
                                           String interviewProceedStateKey,
                                           Map<String, String> mdcContext) {
        gptCallbackExecutor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                log.info("GPT 폴백 시작 - {}", interviewProceedStateKey);

                GptResponse response = interviewProceedGptClient.requestToGpt(questionAndAnswers);
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
                redisService.releaseLockSafely(lockKey, lockValue);
                MDC.clear();
            }
        });
    }

    private void createNextQuestionTtsAndUploadToS3(Long memberId, String interviewProceedStateKey,
                                                    InterviewProceedResult result) {
        try {
            questionService.createAndUploadQuestionVoice(result.getNextQuestion());
            if (memberId != null) {
                tokenFacadeService.useToken(memberId); // TODO: TTS는 성공했는데 useToken만 실패하는 경우 고려 필요
            }
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
        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                String feedback = answerFeedbackBedrockClient.requestAnswerFeedback(questionAndAnswers, curAnswerRank);
                interviewProceedService.saveAnswerFeedback(curAnswerId, feedback);
            } catch (Exception e) {
                log.error("답변 피드백 Bedrock 호출 실패 curAnswerId={}", curAnswerId, e);
            } finally {
                MDC.clear();
            }
        });
    }

    private void setMdcContext(Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }
}
