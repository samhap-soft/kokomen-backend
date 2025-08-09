package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.TypecastClient;
import com.samhap.kokomen.interview.external.dto.request.TypecastRequest;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.external.dto.response.TypecastResponse;
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

    public static final String QUESTION_VOICE_URL_KEY_FORMAT = "question:%d:voice_url";
    private static final int TYPECAST_FORCED_TTL_HOURS = 24;

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
                    requestTypecastAndSaveUrlToRedisAsync(nextQuestion);
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

    /*
    1. 굳이 새로운 Executor 만들지 않고, bedrockBlockExecutor를 재사용
    2. proceedOrEndInterviewBlockAsync에서는 아래 메서드를 비동기 호출할 필요가 없어서 의도적으로 내부 호출
     */
    @Async("bedrockBlockExecutor")
    public void requestTypecastAndSaveUrlToRedisAsync(Question question) {
        TypecastResponse typecastResponse = typecastClient.request(new TypecastRequest(question.getContent()));
        redisService.setValue(QUESTION_VOICE_URL_KEY_FORMAT.formatted(question.getId()),
                typecastResponse.getSpeakV2Url(), Duration.ofHours(TYPECAST_FORCED_TTL_HOURS - 1));
    }

    public static String createQuestionVoiceUrlKey(Long questionId) {
        return QUESTION_VOICE_URL_KEY_FORMAT.formatted(questionId);
    }
}
