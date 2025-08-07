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
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.dto.response.InterviewSummaryResponses;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewProceedStateResponse;
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

    private final BedrockClient bedrockClient;
    private final RedisService redisService;
    private final InterviewProceedService interviewProceedService;
    private final InterviewService interviewService;
    private final InterviewLikeService interviewLikeService;
    private final MemberService memberService;
    private final RootQuestionService rootQuestionService;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final ApplicationEventPublisher eventPublisher;
    private final InterviewProceedBlockAsyncService interviewProceedBlockAsyncService;

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

    public void proceedInterviewBlockAsync(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        memberService.validateEnoughTokenCount(memberAuth.memberId(), 1);
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String lockKey = createInterviewProceedLockKey(memberAuth.memberId());
        acquireLockForProceedInterview(lockKey);
        try {
            String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, curQuestionId);
            QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interviewId, curQuestionId, answerRequest);
            interviewProceedBlockAsyncService.proceedOrEndInterviewBlockAsync(memberAuth.memberId(), questionAndAnswers, interviewId);
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

    public static String createInterviewProceedStateKey(Long interviewId, Long curQuestionId) {
        return INTERVIEW_PROCEED_STATE_KEY_PREFIX + interviewId + ":" + curQuestionId;
    }

    @Transactional(readOnly = true)
    public InterviewProceedStateResponse findInterviewProceedState(Long interviewId, Long curQuestionId, MemberAuth memberAuth) {
        interviewService.validateInterviewee(interviewId, memberAuth.memberId());
        String interviewProceedStateKey = createInterviewProceedStateKey(interviewId, curQuestionId);

        Optional<String> stateOptional = redisService.get(interviewProceedStateKey, String.class);
        if (stateOptional.isPresent()) {
            LlmProceedState llmProceedState = LlmProceedState.valueOf(stateOptional.get());
            return createResponseByLlmProceedState(interviewId, curQuestionId, llmProceedState);
        }

        return answerService.findByQuestionId(curQuestionId)
                .map(answer -> createCompletedResponse(interviewId, curQuestionId))
                .orElseGet(() -> createResponseByLlmProceedState(interviewId, curQuestionId, LlmProceedState.FAILED));
    }

    private InterviewProceedStateResponse createResponseByLlmProceedState(Long interviewId, Long curQuestionId, LlmProceedState llmProceedState) {
        if (llmProceedState == LlmProceedState.PENDING || llmProceedState == LlmProceedState.FAILED) {
            return InterviewProceedStateResponse.createOfStatus(llmProceedState);
        }
        return createCompletedResponse(interviewId, curQuestionId);
    }

    private InterviewProceedStateResponse createCompletedResponse(Long interviewId, Long curQuestionId) {
        Interview interview = interviewService.readInterview(interviewId);
        List<Question> lastTwoQuestions = questionService.readLastTwoQuestionsByInterviewId(interviewId);
        if (interview.getInterviewState() == InterviewState.FINISHED) {
            return handleFinishedInterview(interview, curQuestionId, lastTwoQuestions);
        }
        return handleInProgressInterview(interview, curQuestionId, lastTwoQuestions);
    }

    private InterviewProceedStateResponse handleFinishedInterview(Interview interview, Long curQuestionId, List<Question> lastTwoQuestions) {
        Question lastQuestion = lastTwoQuestions.get(0);
        if (!curQuestionId.equals(lastQuestion.getId())) {
            throw new BadRequestException("현재 질문이 아닙니다. 현재 질문 id: " + lastQuestion.getId());
        }
        return InterviewProceedStateResponse.createCompletedAndFinished(interview);
    }

    private InterviewProceedStateResponse handleInProgressInterview(Interview interview, Long curQuestionId, List<Question> lastTwoQuestions) {
        Question lastQuestion = lastTwoQuestions.get(0);
        Question curQuestion = lastTwoQuestions.get(1);
        if (!curQuestionId.equals(curQuestion.getId())) {
            throw new BadRequestException("현재 질문이 아닙니다. 현재 질문 id: " + curQuestion.getId());
        }
        Answer curAnswer = answerService.readByQuestionId(curQuestionId);

        return InterviewProceedStateResponse.createCompletedAndInProgress(interview, curAnswer, lastQuestion);
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
