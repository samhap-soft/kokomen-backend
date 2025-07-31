package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.BedrockAsyncClient;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.InterviewSummaryResponses;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.interview.service.event.InterviewLikedEvent;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

@Slf4j
@Service
public class InterviewFacadeService {

    public static final String INTERVIEW_PROCEED_LOCK_KEY_PREFIX = "lock:interview:proceed:";
    public static final String INTERVIEW_PROCEED_STATE_KEY_PREFIX = "interview:proceed:state:";

    private final BedrockClient bedrockClient;
    private final BedrockAsyncClient bedrockAsyncClient;
    private final RedisService redisService;
    private final InterviewProceedService interviewProceedService;
    private final InterviewService interviewService;
    private final InterviewLikeService interviewLikeService;
    private final MemberService memberService;
    private final RootQuestionService rootQuestionService;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final ApplicationEventPublisher eventPublisher;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final InterviewProceedBlockAsyncService interviewProceedBlockAsyncService;

    public InterviewFacadeService(
            BedrockClient bedrockClient,
            BedrockAsyncClient bedrockAsyncClient,
            RedisService redisService,
            InterviewProceedService interviewProceedService,
            InterviewService interviewService,
            InterviewLikeService interviewLikeService,
            MemberService memberService,
            RootQuestionService rootQuestionService,
            QuestionService questionService,
            AnswerService answerService,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("bedrockCallbackExecutor") ThreadPoolTaskExecutor threadPoolTaskExecutor,
            InterviewProceedBlockAsyncService interviewProceedBlockAsyncService) {
        this.bedrockClient = bedrockClient;
        this.bedrockAsyncClient = bedrockAsyncClient;
        this.redisService = redisService;
        this.interviewProceedService = interviewProceedService;
        this.interviewService = interviewService;
        this.interviewLikeService = interviewLikeService;
        this.memberService = memberService;
        this.rootQuestionService = rootQuestionService;
        this.questionService = questionService;
        this.answerService = answerService;
        this.eventPublisher = eventPublisher;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.interviewProceedBlockAsyncService = interviewProceedBlockAsyncService;
    }

    @Transactional
    public InterviewStartResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        memberService.validateEnoughTokenCount(memberAuth.memberId(), interviewRequest.maxQuestionCount());
        Member member = memberService.readById(memberAuth.memberId());
        RootQuestion rootQuestion = rootQuestionService.readRandomRootQuestion(member, interviewRequest);
        Interview interview = interviewService.saveInterview(new Interview(member, rootQuestion, interviewRequest.maxQuestionCount()));
        Question question = questionService.saveQuestion(new Question(interview, rootQuestion.getContent()));

        return new InterviewStartResponse(interview, question);
    }

    public Optional<InterviewProceedResponse> proceedInterview(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        memberService.validateEnoughTokenCount(memberAuth.memberId(), 1);
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        acquireLockForProceedInterview(lockKey);
        try {
            QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId, answerRequest);
            LlmResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
            return interviewProceedService.proceedOrEndInterview(memberAuth.memberId(), questionAndAnswers, llmResponse, interviewId);
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    public void proceedInterviewNonblockAsync(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        memberService.validateEnoughTokenCount(memberAuth.memberId(), 1);
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        acquireLockForProceedInterview(lockKey);
        try {
            QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId, answerRequest);
            String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, curQuestionId);

            log.info("논블로킹 비동기 요청 보내기 직전 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, curQuestionId, memberAuth.memberId());
            CompletableFuture<ConverseResponse> completableFuture = bedrockAsyncClient.requestToBedrock(questionAndAnswers);
            log.info("논블로킹 비동기 요청 보내기 직후 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, curQuestionId, memberAuth.memberId());
            completableFuture.thenAcceptAsync(
                            response -> callbackBedrock(response, memberAuth.memberId(), questionAndAnswers, interviewId, lockKey, mdcContext),
                            threadPoolTaskExecutor)
                    .exceptionallyAsync(ex -> handleBedrockException(ex, lockKey, interviewProceedStateKey, mdcContext), threadPoolTaskExecutor);

            redisService.setValue(interviewProceedStateKey, LlmProceedState.PENDING.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.releaseLock(lockKey);
            throw e;
        }
    }

    public void proceedInterviewBlockAsync(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        memberService.validateEnoughTokenCount(memberAuth.memberId(), 1);
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        acquireLockForProceedInterview(lockKey);
        try {
            String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, curQuestionId);
            QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId, answerRequest);
            log.info("블로킹 비동기 요청 보내기 직전 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, curQuestionId, memberAuth.memberId());
            interviewProceedBlockAsyncService.proceedOrEndInterviewBlockAsync(memberAuth.memberId(), questionAndAnswers, interviewId);
            log.info("블로킹 비동기 요청 보낸 직후 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, curQuestionId, memberAuth.memberId());

            redisService.setValue(interviewProceedStateKey, LlmProceedState.PENDING.name(), Duration.ofSeconds(300));
        } catch (Exception e) {
            redisService.releaseLock(lockKey);
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

    private QuestionAndAnswers createQuestionAndAnswers(Long interviewId, Long curQuestionId, AnswerRequest answerRequest) {
        Interview interview = interviewService.readInterview(interviewId);

        List<Question> questions = questionService.findByInterview(interview);
        List<Answer> prevAnswers = answerService.findByQuestionIn(questions);

        return new QuestionAndAnswers(questions, prevAnswers, answerRequest.answer(), curQuestionId, interview);
    }

    private void callbackBedrock(ConverseResponse converseResponse, Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId, String lockKey,
                                 Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
        log.info("논블로킹 비동기 스레드 시작 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, questionAndAnswers.readCurQuestion().getId(), memberId);

        try {
            String rawText = converseResponse.output().message().content().get(0).text();
            String cleanedContent = cleanJsonContent(rawText);

            BedrockResponse response = new BedrockResponse(cleanedContent);
            interviewProceedService.proceedOrEndInterviewNonblockAsync(memberId, questionAndAnswers, response, interviewId);

            String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, questionAndAnswers.readCurQuestion().getId());
            redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(300));
            redisService.releaseLock(lockKey);
        } finally {
            log.info("논블로킹 비동기 스레드 종료 - interviewId: {}, curQuestionId: {}, memberId: {}", interviewId, questionAndAnswers.readCurQuestion().getId(), memberId);
            MDC.clear();
        }
    }

    private String cleanJsonContent(String rawText) {
        return rawText
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .replaceAll("`", "")
                .trim();
    }

    public static String createInterviewProceedStateKey(Long interviewId, Long curQuestionId) {
        return INTERVIEW_PROCEED_STATE_KEY_PREFIX + interviewId + ":" + curQuestionId;
    }

    private Void handleBedrockException(Throwable ex, String lockKey, String interviewProceedStateKey, Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
        log.info("논블로킹 비동기 예외 스레드 시작 - {}}", interviewProceedStateKey);

        try {
            log.error("Bedrock API 호출 실패 - {}", interviewProceedStateKey, ex);
            redisService.releaseLock(lockKey);
            redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(300));
            return null;
        } finally {
            log.info("논블로킹 비동기 예외 스레드 종료 - {}}", interviewProceedStateKey);
            MDC.clear();
        }
    }

    public LlmProceedState getInterviewProceedState(Long interviewId, Long curQuestionId) {
        String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, curQuestionId);
        String state = redisService.get(interviewProceedStateKey, String.class).get();
        return LlmProceedState.valueOf(state);
    }

    @Transactional
    public void likeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));
        interviewService.increaseLikeCountModifying(interviewId);
        interview = interviewService.readInterview(interviewId); // @Modifying에서 영속성 컨텍스트를 비운 뒤, 다시 조회

        eventPublisher.publishEvent(new InterviewLikedEvent(interviewId, memberAuth.memberId(), interview.getMember().getId(), interview.getLikeCount()));
    }

    public InterviewResponse checkInterview(Long interviewId, MemberAuth memberAuth) {
        return interviewService.checkInterview(interviewId, memberAuth);
    }

    public List<InterviewSummaryResponse> findMyInterviews(MemberAuth memberAuth, InterviewState state, Pageable pageable) {
        return interviewService.findMyInterviews(memberAuth, state, pageable);
    }

    public InterviewSummaryResponses findOtherMemberInterviews(Long memberId, MemberAuth memberAuth, Pageable pageable) {
        return interviewService.findOtherMemberInterviews(memberId, memberAuth, pageable);
    }

    public InterviewResultResponse findMyInterviewResult(Long interviewId, MemberAuth memberAuth) {
        return interviewService.findMyInterviewResult(interviewId, memberAuth);
    }

    public InterviewResultResponse findOtherMemberInterviewResult(Long interviewId, MemberAuth memberAuth, ClientIp clientIp) {
        return interviewService.findOtherMemberInterviewResult(interviewId, memberAuth, clientIp);
    }

    @Transactional
    public void unlikeInterview(Long interviewId, MemberAuth memberAuth) {
        interviewService.unlikeInterview(interviewId, memberAuth);
    }
} 
