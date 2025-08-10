package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.TypecastClient;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewProceedBlockAsyncService {

    private final QuestionService questionService;
    private final InterviewProceedService interviewProceedService;
    private final RedisService redisService;
    private final BedrockClient bedrockClient;
    private final TypecastClient typecastClient;

    @Async("bedrockBlockExecutor")
    public void proceedOrEndInterviewBlockAsync(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
        String lockKey = InterviewFacadeService.createInterviewProceedLockKey(memberId);
        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());

        try {
            LlmResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
            Optional<Question> nextQuestionOptional = interviewProceedService.proceedOrEndInterviewBlockAsync(
                    memberId, questionAndAnswers, llmResponse, interviewId);

            nextQuestionOptional.ifPresent(nextQuestion -> {
                if (interviewProceedService.isVoiceMode(interviewId)) {
                    questionService.resolveQuestionVoiceUrl(nextQuestion);
                }
            });
            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
            log.error("인터뷰 진행 실패 - {}", interviewProceedStateKey, e);
            throw e;
        } finally {
            redisService.releaseLock(lockKey);
        }
    }
}
