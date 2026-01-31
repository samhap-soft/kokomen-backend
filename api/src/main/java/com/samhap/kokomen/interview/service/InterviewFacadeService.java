package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.InterviewSummaryResponses;
import com.samhap.kokomen.interview.service.dto.AnswerRequestV2;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.RootQuestionResponse;
import com.samhap.kokomen.interview.service.dto.check.InterviewCheckResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateTextModeResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateVoiceModeResponse;
import com.samhap.kokomen.interview.service.dto.resumebased.ResumeBasedInterviewStartRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartVoiceModeResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final RedisService redisService;
    private final InterviewService interviewService;
    private final InterviewLikeService interviewLikeService;
    private final MemberService memberService;
    private final TokenService tokenService;
    private final RootQuestionService rootQuestionService;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final InterviewProceedBedrockFlowAsyncService interviewProceedBedrockFlowAsyncService;
    private final ResumeBasedInterviewService resumeBasedInterviewService;

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

    @Transactional
    public InterviewStartResponse startRootQuestionCustomInterview(RootQuestionCustomInterviewRequest request,
                                                                   MemberAuth memberAuth) {
        InterviewMode interviewMode = request.mode();
        int requiredTokenCount = request.maxQuestionCount() * interviewMode.getRequiredTokenCount()
                - TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE;
        tokenService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);
        Member member = memberService.readById(memberAuth.memberId());
        RootQuestion rootQuestion = rootQuestionService.readRootQuestion(request.rootQuestionId());
        Interview interview = interviewService.saveInterview(
                new Interview(member, rootQuestion, request.maxQuestionCount(), interviewMode));
        Question question = questionService.saveQuestion(new Question(interview, rootQuestion.getContent()));

        if (interviewMode == InterviewMode.VOICE) {
            return new InterviewStartVoiceModeResponse(interview, question,
                    questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestion.getId()));
        }
        return new InterviewStartTextModeResponse(interview, question);
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
            log.info("Bedrock API 호출 시도 - interviewId: {}, curQuestionId: {}, memberId: {}",
                    interviewId, curQuestionId, memberAuth.memberId());
            interviewProceedBedrockFlowAsyncService.proceedInterviewByBedrockFlowAsync(memberAuth.memberId(),
                    questionAndAnswers, interviewId);
        } catch (Exception e) {
            try {
                log.info("Gpt API 호출 시도 - interviewId: {}, curQuestionId: {}, memberId: {}",
                        interviewId, curQuestionId, memberAuth.memberId());
                log.error("Bedrock API 호출 실패, GPT 폴백에시 기록 - {}", e);
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

    public List<RootQuestionResponse> getRootQuestionsByCategory(Category category) {
        return rootQuestionService.findAllRootQuestionByCategory(category).stream()
                .map(RootQuestionResponse::from)
                .toList();
    }

    @Transactional
    public InterviewStartResponse startResumeBasedInterview(
            Long generationId,
            ResumeBasedInterviewStartRequest request,
            MemberAuth memberAuth
    ) {
        Member member = memberService.readById(memberAuth.memberId());
        ResumeQuestionGeneration generation = resumeBasedInterviewService.readGeneration(generationId);
        validateGenerationOwnership(generation, memberAuth.memberId());
        validateGenerationCompleted(generation);
        GeneratedQuestion generatedQuestion = resumeBasedInterviewService.readGeneratedQuestion(
                request.generatedQuestionId(), generationId);

        InterviewMode interviewMode = request.mode();
        int requiredTokenCount = request.maxQuestionCount() * interviewMode.getRequiredTokenCount();
        tokenService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);

        Interview interview = interviewService.saveInterview(
                new Interview(member, generatedQuestion, request.maxQuestionCount(), interviewMode));
        Question question = questionService.saveQuestion(new Question(interview, generatedQuestion.getContent()));

        if (interviewMode == InterviewMode.VOICE) {
            String voiceUrl = questionService.createAndUploadQuestionVoice(question);
            return new InterviewStartVoiceModeResponse(interview, question, voiceUrl);
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    private void validateGenerationOwnership(ResumeQuestionGeneration generation, Long memberId) {
        if (!generation.isOwner(memberId)) {
            throw new ForbiddenException("본인의 질문 생성 결과로만 면접을 시작할 수 있습니다.");
        }
    }

    private void validateGenerationCompleted(ResumeQuestionGeneration generation) {
        if (!generation.isCompleted()) {
            throw new BadRequestException("질문 생성이 완료되지 않았습니다.");
        }
    }
}
