package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.member.service.MemberService;
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
    private final MemberService memberService;
    private final InterviewProceedService interviewProceedService;
    private final RedisService redisService;
    private final BedrockClient bedrockClient;

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
                    questionService.createQuestionVoiceUrl(nextQuestion);
                    memberService.useToken(memberId);
                }
            });
            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            // LLM 호출은 성공해서 answer는 저장됐는데 typecast 요청이 실패한 경우, FAILED로만 떠서 클라이언트 입장에서는 위 API를 다시 시도할텐데, 사실 LLM 호출은 성공해서 Answer는 저장됐기 때문에
            // QuestionAndAnswers를 생성하는 과정에서 에러 던져진다.
            // TODO: 따라서 AnswerProceedState를 따로 두고, LLM_PENDING, TYPECAST_PENDING, LLM_FAILED, TYPECAST_FAILED, COMPLETED로 나누는게 좋을 것 같다.
            // TODO: 그리고 TYPECAST_FAILED일 때는 위 API를 다시 시도하는게 아니라 Typecast 요청만 수행하는 API를 따로 뚫어줘야 할 듯?
            // TODO: LLM 호출에 성공하면 토큰을 2개 한 번에 감소시키고 tts fallback 처리를 해서 어떻게든 성공시켜주는 방향으로 개선할 것
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
            log.error("인터뷰 진행 실패 - {}", interviewProceedStateKey, e);
            throw e;
        } finally {
            redisService.releaseLock(lockKey);
        }
    }
}
