package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import com.samhap.kokomen.interview.service.core.InterviewService;
import com.samhap.kokomen.interview.service.dto.AnswerRequestV2;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateTextModeResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateVoiceModeResponse;
import com.samhap.kokomen.interview.service.infra.InterviewProceedBedrockFlowAsyncService;
import com.samhap.kokomen.interview.service.question.QuestionService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewProceedFacadeService {

    public static final String INTERVIEW_PROCEED_LOCK_KEY_PREFIX = "lock:interview:proceed:";
    public static final String INTERVIEW_PROCEED_STATE_KEY_PREFIX = "interview:proceed:state:";

    private final RedisService redisService;
    private final InterviewService interviewService;
    private final TokenFacadeService tokenFacadeService;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final InterviewProceedBedrockFlowAsyncService interviewProceedBedrockFlowAsyncService;

    public void proceedInterviewByBedrockFlow(Long interviewId, Long curQuestionId, AnswerRequestV2 answerRequest,
                                              MemberAuth memberAuth) {
        tokenFacadeService.validateEnoughTokens(memberAuth.memberId(), answerRequest.mode().getRequiredTokenCount());
        interviewService.validateInterviewMode(interviewId, answerRequest.mode());
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        String lockValue = UUID.randomUUID().toString();
        acquireLockForProceedInterview(lockKey, lockValue);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId,
                answerRequest.answer());
        try {
            log.info("Bedrock API 호출 시도 - interviewId: {}, curQuestionId: {}, memberId: {}",
                    interviewId, curQuestionId, memberAuth.memberId());
            interviewProceedBedrockFlowAsyncService.proceedInterviewByBedrockFlowAsync(memberAuth.memberId(),
                    questionAndAnswers, interviewId, lockValue);
        } catch (Exception e) {
            try {
                log.info("Gpt API 호출 시도 - interviewId: {}, curQuestionId: {}, memberId: {}",
                        interviewId, curQuestionId, memberAuth.memberId());
                log.error("Bedrock API 호출 실패, GPT 폴백에시 기록 - {}", e);
                interviewProceedBedrockFlowAsyncService.proceedInterviewByGptFlowAsync(memberAuth.memberId(),
                        questionAndAnswers, interviewId, lockValue);
            } catch (Exception ex) {
                log.error("Gpt API 호출 실패 - {}", ex);
                redisService.releaseLockSafely(lockKey, lockValue);
            }
        }
    }

    public static String createInterviewProceedLockKey(Long memberId) {
        return INTERVIEW_PROCEED_LOCK_KEY_PREFIX + memberId;
    }

    private void acquireLockForProceedInterview(String lockKey, String lockValue) {
        boolean lockAcquired = redisService.acquireLockWithValue(lockKey, lockValue, Duration.ofMinutes(5));
        if (!lockAcquired) {
            throw new BadRequestException("이미 처리 중인 답변이 있습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private QuestionAndAnswers createQuestionAndAnswers(Long interviewId, Long curQuestionId, String answerContent) {
        Interview interview = interviewService.readInterview(interviewId);

        List<Question> questions = questionService.findByInterview(interview);
        List<Answer> prevAnswers = answerService.findByQuestionIn(questions);

        return new QuestionAndAnswers(questions, prevAnswers, answerContent, curQuestionId, interview);
    }

    @Transactional(readOnly = true)
    public InterviewProceedStateResponse findInterviewProceedState(Long interviewId, Long curQuestionId,
                                                                   InterviewMode mode, MemberAuth memberAuth) {
        interviewService.validateInterviewMode(interviewId, mode);
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, curQuestionId);

        Optional<String> interviewProceedStateOptional = redisService.get(interviewProceedStateKey, String.class);
        if (interviewProceedStateOptional.isPresent()) {
            InterviewProceedState interviewProceedState = InterviewProceedState.valueOf(
                    interviewProceedStateOptional.get());
            return createResponseByLlmProceedState(interviewId, curQuestionId, interviewProceedState);
        }

        return recoverWhenRedisStateMissing(interviewId, curQuestionId);
    }

    public static String createInterviewProceedStateKey(Long interviewId, Long curQuestionId) {
        return INTERVIEW_PROCEED_STATE_KEY_PREFIX + interviewId + ":" + curQuestionId;
    }

    private InterviewProceedStateResponse recoverWhenRedisStateMissing(Long interviewId, Long curQuestionId) {
        return answerService.findByQuestionId(curQuestionId)
                .map(answer -> createCompletedResponse(interviewId, curQuestionId))
                .orElseGet(() -> InterviewProceedStateResponse.createPendingOrFailed(InterviewProceedState.LLM_FAILED));
    }

    private InterviewProceedStateResponse createResponseByLlmProceedState(Long interviewId, Long curQuestionId,
                                                                          InterviewProceedState interviewProceedState) {
        if (interviewProceedState != InterviewProceedState.COMPLETED) {
            return InterviewProceedStateResponse.createPendingOrFailed(interviewProceedState);
        }
        return createCompletedResponse(interviewId, curQuestionId);
    }

    private InterviewProceedStateResponse createCompletedResponse(Long interviewId, Long curQuestionId) {
        Interview interview = interviewService.readInterview(interviewId);
        List<Question> lastTwoQuestions = questionService.readLastTwoQuestionsByInterviewId(interviewId);
        if (interview.getInterviewState() == InterviewState.FINISHED) {
            return createCompletedAndFinishedInterviewResponse(curQuestionId, lastTwoQuestions);
        }
        return createCompletedAndInProgressInterviewResponse(interview, curQuestionId, lastTwoQuestions);
    }

    private InterviewProceedStateResponse createCompletedAndFinishedInterviewResponse(
            Long curQuestionId,
            List<Question> lastTwoQuestions
    ) {
        Question lastQuestion = lastTwoQuestions.get(0);
        if (!curQuestionId.equals(lastQuestion.getId())) {
            throw new BadRequestException("현재 질문이 아닙니다. 현재 질문 id: " + lastQuestion.getId());
        }
        return InterviewProceedStateResponse.createCompletedAndFinished();
    }

    private InterviewProceedStateResponse createCompletedAndInProgressInterviewResponse(
            Interview interview,
            Long curQuestionId,
            List<Question> lastTwoQuestions
    ) {
        Question lastQuestion = lastTwoQuestions.get(0);
        Question curQuestion = lastTwoQuestions.get(1);
        if (!curQuestionId.equals(curQuestion.getId())) {
            throw new BadRequestException("현재 질문이 아닙니다. 현재 질문 id: " + curQuestion.getId());
        }
        Answer curAnswer = answerService.readByQuestionId(curQuestionId);

        if (interview.getInterviewMode() == InterviewMode.VOICE) {
            String questionVoiceUrl = questionService.resolveQuestionVoiceUrl(lastQuestion);
            return InterviewProceedStateVoiceModeResponse.createCompletedAndInProgress(curAnswer, lastQuestion,
                    questionVoiceUrl);
        }

        return InterviewProceedStateTextModeResponse.createCompletedAndInProgress(curAnswer, lastQuestion);
    }
}
