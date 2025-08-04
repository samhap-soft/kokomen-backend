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
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.BedrockClient;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewFacadeService {

    public static final String INTERVIEW_PROCEED_LOCK_KEY_PREFIX = "lock:interview:proceed:";

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
    private final InterviewLikeEventProducer interviewLikeEventProducer;

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

    private String createInterviewProceedLockKey(Long memberId) {
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

    @Transactional
    public void likeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));

        eventPublisher.publishEvent(new InterviewLikedEvent(interviewId, memberAuth.memberId(), interview.getMember().getId(), interview.getLikeCount()));
        interviewService.increaseLikeCountModifying(interviewId);
    }

    // TODO: 하나로 합치기
    @Transactional
    public void likeInterviewKafka(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));

        // Kafka 이벤트 발행 (receiverMemberId, likerMemberId, likeCount 모두 전달)
        interviewLikeEventProducer.sendLikeEvent(interviewId, interview.getMember().getId(), memberAuth.memberId(), interview.getLikeCount() + 1);
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
