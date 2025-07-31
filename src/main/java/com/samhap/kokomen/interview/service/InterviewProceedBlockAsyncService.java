package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewProceedBlockAsyncService {

    private final InterviewProceedService interviewProceedService;
    private final RedisService redisService;
    private final BedrockClient bedrockClient;

    @Async("bedrockBlockExecutor")
    public void proceedOrEndInterviewBlockAsync(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            Long interviewId
    ) {
        log.info("블로킹 비동기 스레드 시작 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, questionAndAnswers.readCurQuestion().getId(), memberId);
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());

        try {
            LlmResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
            interviewProceedService.proceedOrEndInterviewBlockAsync(memberId, questionAndAnswers, llmResponse, interviewId);
            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
            log.error("Bedrock API 호출 실패 - {}", interviewProceedStateKey, e);
            throw e;
        } finally {
            redisService.releaseLock(lockKey);
            log.info("블로킹 비동기 스레드 종료 - interviewId: {}, curQuestionId: {}, memberId: {}",
                    interviewId, questionAndAnswers.readCurQuestion().getId(), memberId);
        }
    }
}
