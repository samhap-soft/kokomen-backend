package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
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

    @Async("bedrockBlockExecutor")
    public void proceedOrEndInterviewBlockAsync(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            Long interviewId
    ) {
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());

        try {
            interviewProceedService.proceedOrEndInterviewBlockAsync(memberId, questionAndAnswers, interviewId);
            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
            log.error("Bedrock API 호출 실패 - {}", interviewProceedStateKey, e);
            throw e;
        } finally {
            redisService.releaseLock(lockKey);
        }
    }
}
