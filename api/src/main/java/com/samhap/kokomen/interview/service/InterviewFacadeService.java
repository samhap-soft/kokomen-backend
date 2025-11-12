package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.dto.response.InterviewSummaryResponses;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.AnswerRequestV2;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.interview.service.dto.check.InterviewCheckResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateTextModeResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateVoiceModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartVoiceModeResponse;
import com.samhap.kokomen.interview.service.event.InterviewLikedEvent;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewFacadeService {

    public static final String INTERVIEW_PROCEED_LOCK_KEY_PREFIX = "lock:interview:proceed:";
    public static final String INTERVIEW_PROCEED_STATE_KEY_PREFIX = "interview:proceed:state:";
    private static final int TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE = 1;

    private final QuestionVoicePathResolver questionVoicePathResolver;
    private final BedrockClient bedrockClient;
    private final RedisService redisService;
    private final InterviewProceedService interviewProceedService;
    private final InterviewService interviewService;
    private final InterviewLikeService interviewLikeService;
    private final MemberService memberService;
    private final TokenService tokenService;
    private final RootQuestionService rootQuestionService;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final ApplicationEventPublisher eventPublisher;
    private final InterviewLikeEventProducer interviewLikeEventProducer;
    private final InterviewLikeEventProducerV2 interviewLikeEventProducerV2;
    private final InterviewProceedBedrockFlowAsyncService interviewProceedBedrockFlowAsyncService;

    @Transactional
    public InterviewStartResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        InterviewMode interviewMode = interviewRequest.mode();
        int requiredTokenCount = interviewRequest.maxQuestionCount() * interviewMode.getRequiredTokenCount()
                - TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE;
        tokenService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);
        Member member = memberService.readById(memberAuth.memberId());
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);
        Interview interview = interviewService.saveInterview(
                new Interview(member, rootQuestion, interviewRequest.maxQuestionCount(), interviewMode));
        Question question = questionService.saveQuestion(new Question(interview, rootQuestion.getContent()));

        if (interviewMode == InterviewMode.VOICE) {
            return new InterviewStartVoiceModeResponse(interview, question,
                    questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestion.getId()));
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    public Optional<InterviewProceedResponse> proceedInterview(Long interviewId, Long curQuestionId,
                                                               AnswerRequest answerRequest, MemberAuth memberAuth) {
        tokenService.validateEnoughTokens(memberAuth.memberId(), 1);
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        acquireLockForProceedInterview(lockKey);
        try {
            QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId,
                    answerRequest.answer());
            LlmResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
            return interviewProceedService.proceedOrEndInterview(memberAuth.memberId(), questionAndAnswers, llmResponse,
                    interviewId);
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    public void proceedInterviewByBedrockFlow(Long interviewId, Long curQuestionId, AnswerRequestV2 answerRequest,
                                              MemberAuth memberAuth) {
        tokenService.validateEnoughTokens(memberAuth.memberId(), answerRequest.mode().getRequiredTokenCount());
        interviewService.validateInterviewMode(interviewId, answerRequest.mode());
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        acquireLockForProceedInterview(lockKey);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId,
                answerRequest.answer());
        try {
            interviewProceedBedrockFlowAsyncService.proceedInterviewByBedrockFlowAsync(memberAuth.memberId(),
                    questionAndAnswers, interviewId);
        } catch (Exception e) {
            try {
                interviewProceedBedrockFlowAsyncService.proceedInterviewByGptFlowAsync(memberAuth.memberId(),
                        questionAndAnswers, interviewId);
            } catch (Exception ex) {
                log.error("Gpt API 호출 실패 - {}", ex);
                redisService.releaseLock(lockKey);
            }
        }
    }

    public static String createInterviewProceedLockKey(Long memberId) {
        return INTERVIEW_PROCEED_LOCK_KEY_PREFIX + memberId;
    }

    private void acquireLockForProceedInterview(String lockKey) {
        boolean lockAcquired = redisService.acquireLock(lockKey, Duration.ofSeconds(30));
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

    @Transactional
    public void likeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));
        interviewService.increaseLikeCountModifying(
                interviewId); // X락을 사용하기 때문에 동시에 요청이 와도 올바른 likeCount 값으로 이벤트를 생성할 수 있다.
        interview = interviewService.readInterview(
                interviewId); // @Modifying에서 영속성 컨텍스트를 비운 뒤, 다시 읽어와야 최신 likeCount 값을 가져올 수 있다. 다른 트랜잭션에서 변경했을 수도 있기 때문

        eventPublisher.publishEvent(
                new InterviewLikedEvent(interviewId, memberAuth.memberId(), interview.getMember().getId(),
                        interview.getLikeCount()));
    }

    // TODO: 하나로 합치기
    @Transactional
    public void likeInterviewKafka(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));

        // Kafka 이벤트 발행 (receiverMemberId, likerMemberId, likeCount 모두 전달)
        interviewLikeEventProducer.sendLikeEvent(interviewId, interview.getMember().getId(), memberAuth.memberId(),
                interview.getLikeCount() + 1);
    }

    @Transactional
    public void likeInterviewKafkaV2(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));
        Long likeCount = incrementAndGetLikeCountInRedis(interviewId, interview);

        // Kafka 이벤트 발행 (receiverMemberId, likerMemberId, likeCount 모두 전달)
        interviewLikeEventProducerV2.sendLikeEvent(interviewId, interview.getMember().getId(), memberAuth.memberId(),
                likeCount);
    }

    private Long incrementAndGetLikeCountInRedis(Long interviewId, Interview interview) {
        String likeCountKey = "interview:like:" + interviewId;
        boolean expireSuccess = redisService.expireKey(likeCountKey, Duration.ofDays(2));
        if (!expireSuccess) {
            redisService.setIfAbsent(likeCountKey, String.valueOf(interview.getLikeCount()), Duration.ofDays(2));
        }
        Long likeCount = redisService.incrementKey(likeCountKey);
        return likeCount;
    }

    public InterviewCheckResponse checkInterview(Long interviewId, InterviewMode mode, MemberAuth memberAuth) {
        return interviewService.checkInterview(interviewId, mode, memberAuth);
    }

    public List<InterviewSummaryResponse> findMyInterviews(MemberAuth memberAuth, InterviewState state,
                                                           Pageable pageable) {
        return interviewService.findMyInterviews(memberAuth, state, pageable);
    }

    public InterviewSummaryResponses findOtherMemberInterviews(Long memberId, MemberAuth memberAuth,
                                                               Pageable pageable) {
        return interviewService.findOtherMemberInterviews(memberId, memberAuth, pageable);
    }

    public InterviewResultResponse findMyInterviewResult(Long interviewId, MemberAuth memberAuth) {
        return interviewService.findMyInterviewResult(interviewId, memberAuth);
    }

    public InterviewResultResponse findOtherMemberInterviewResult(Long interviewId, MemberAuth memberAuth,
                                                                  ClientIp clientIp) {
        return interviewService.findOtherMemberInterviewResult(interviewId, memberAuth, clientIp);
    }

    @Transactional
    public void unlikeInterview(Long interviewId, MemberAuth memberAuth) {
        interviewService.unlikeInterview(interviewId, memberAuth);
    }
} 
