package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.tool.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.service.core.InterviewService;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartVoiceModeResponse;
import com.samhap.kokomen.interview.service.question.QuestionService;
import com.samhap.kokomen.interview.service.question.RootQuestionService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewStartFacadeService {

    public static final String GUEST_INTERVIEW_STARTED_LOCK_KEY_PREFIX = "guest:interview:started:";
    public static final int GUEST_INTERVIEW_MAX_QUESTION_COUNT = 3;
    public static final InterviewMode GUEST_INTERVIEW_MODE = InterviewMode.TEXT;
    public static final Duration GUEST_INTERVIEW_LOCK_TTL = Duration.ofDays(365);

    private static final int TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE = 1;

    private final QuestionVoicePathResolver questionVoicePathResolver;
    private final InterviewService interviewService;
    private final MemberService memberService;
    private final TokenFacadeService tokenFacadeService;
    private final RootQuestionService rootQuestionService;
    private final QuestionService questionService;
    private final RedisService redisService;

    @Transactional
    public InterviewStartResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        InterviewMode interviewMode = interviewRequest.mode();
        validateLiveCodingNotVoice(interviewRequest, interviewMode);
        int requiredTokenCount = interviewRequest.maxQuestionCount() * interviewMode.getRequiredTokenCount()
                - TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE;
        tokenFacadeService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);
        Member member = memberService.readById(memberAuth.memberId());
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);
        validateModeSupportedForRootQuestion(rootQuestion, interviewMode);
        Interview interview = interviewService.saveInterview(
                new Interview(member, rootQuestion, interviewRequest.maxQuestionCount(), interviewMode,
                        resolveInterviewType(rootQuestion)));
        Question question = questionService.saveQuestion(
                new Question(interview, rootQuestion.createInitialQuestionContent()));

        if (interviewMode == InterviewMode.VOICE) {
            return new InterviewStartVoiceModeResponse(interview, question,
                    questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestion.getId()));
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    @Transactional
    public InterviewStartResponse startGuestInterview(ClientIp clientIp) {
        String lockKey = createGuestInterviewStartedLockKey(clientIp);
        String lockValue = UUID.randomUUID().toString();
        if (!redisService.acquireLockWithValue(lockKey, lockValue, GUEST_INTERVIEW_LOCK_TTL)) {
            throw new BadRequestException("비회원 면접은 1회만 가능합니다.");
        }
        try {
            RootQuestion rootQuestion = rootQuestionService.readRandomActiveRootQuestion();
            Interview interview = interviewService.saveInterview(Interview.forGuest(rootQuestion,
                    GUEST_INTERVIEW_MAX_QUESTION_COUNT, GUEST_INTERVIEW_MODE, clientIp));
            Question question = questionService.saveQuestion(
                    new Question(interview, rootQuestion.createInitialQuestionContent()));
            return new InterviewStartTextModeResponse(interview, question);
        } catch (RuntimeException e) {
            redisService.releaseLockSafely(lockKey, lockValue);
            throw e;
        }
    }

    public static String createGuestInterviewStartedLockKey(ClientIp clientIp) {
        return GUEST_INTERVIEW_STARTED_LOCK_KEY_PREFIX + clientIp.address();
    }

    @Transactional
    public InterviewStartResponse startRootQuestionCustomInterview(RootQuestionCustomInterviewRequest request,
                                                                   MemberAuth memberAuth) {
        InterviewMode interviewMode = request.mode();
        RootQuestion rootQuestion = rootQuestionService.readRootQuestion(request.rootQuestionId());
        validateModeSupportedForRootQuestion(rootQuestion, interviewMode);
        int requiredTokenCount = request.maxQuestionCount() * interviewMode.getRequiredTokenCount()
                - TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE;
        tokenFacadeService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.saveInterview(
                new Interview(member, rootQuestion, request.maxQuestionCount(), interviewMode,
                        resolveInterviewType(rootQuestion)));
        Question question = questionService.saveQuestion(
                new Question(interview, rootQuestion.createInitialQuestionContent()));

        if (interviewMode == InterviewMode.VOICE) {
            return new InterviewStartVoiceModeResponse(interview, question,
                    questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestion.getId()));
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    private InterviewType resolveInterviewType(RootQuestion rootQuestion) {
        if (rootQuestion.isCode()) {
            return InterviewType.LIVE_CODING;
        }
        if (rootQuestion.isPersonality()) {
            return InterviewType.PERSONALITY;
        }
        return InterviewType.CATEGORY_BASED;
    }

    private void validateLiveCodingNotVoice(InterviewRequest interviewRequest, InterviewMode interviewMode) {
        if (interviewRequest.includeLiveCoding() && interviewMode == InterviewMode.VOICE) {
            throw new BadRequestException("라이브 코테는 음성 모드를 지원하지 않습니다.");
        }
    }

    private void validateModeSupportedForRootQuestion(RootQuestion rootQuestion, InterviewMode interviewMode) {
        if (rootQuestion.isCode() && interviewMode == InterviewMode.VOICE) {
            throw new BadRequestException("라이브 코테는 음성 모드를 지원하지 않습니다.");
        }
    }
}
