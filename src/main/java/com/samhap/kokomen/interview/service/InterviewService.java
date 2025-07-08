package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.global.service.RedisDistributedLockService;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.repository.InterviewLikeRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.FeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewService {

    private static final int EXCLUDED_RECENT_ROOT_QUESTION_COUNT = 50;

    private final GptClient gptClient;
    private final BedrockClient bedrockClient;
    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final MemberRepository memberRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final RedisDistributedLockService redisDistributedLockService;
    private final InterviewAnswerResponseService interviewAnswerResponseService;
    private final InterviewLikeRepository interviewLikeRepository;
    private final AnswerLikeRepository answerLikeRepository;

    @Transactional
    public InterviewStartResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        validateEnoughTokenCount(member, interviewRequest);
        RootQuestion rootQuestion = readRandomRootQuestion(member, interviewRequest);
        Interview interview = interviewRepository.save(new Interview(member, rootQuestion, interviewRequest.maxQuestionCount()));
        Question question = questionRepository.save(new Question(interview, rootQuestion.getContent()));

        return new InterviewStartResponse(interview, question);
    }

    private void validateEnoughTokenCount(Member member, InterviewRequest interviewRequest) {
        if (!member.hasEnoughTokenCount(interviewRequest.maxQuestionCount())) {
            throw new BadRequestException("생성하려는 인터뷰의 최대 질문 수가 회원이 가진 토큰 개수를 초과합니다.");
        }
    }

    private RootQuestion readRandomRootQuestion(Member member, InterviewRequest interviewRequest) {
        String category = interviewRequest.category().name();

        return rootQuestionRepository.findRandomByCategoryExcludingRecent(
                member.getId(),
                category,
                EXCLUDED_RECENT_ROOT_QUESTION_COUNT
        ).orElseThrow(() -> new IllegalStateException("루트 질문 갯수가 부족합니다. category = " + category));
    }

    // TODO: answer가 question을 들고 있는데, 영속성 컨텍스트를 활용해서 가져오는지 -> lazy 관련해서
    public Optional<InterviewProceedResponse> proceedInterview(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        String lockKey = "interview:proceed:" + memberAuth.memberId();
        acquireLock(lockKey);

        Member member = readMember(memberAuth.memberId());
        validateHasToken(member);
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(curQuestionId, answerRequest, interview);

        try {
            LlmResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
            return interviewAnswerResponseService.handleGptResponse(member.getId(), questionAndAnswers, llmResponse, interview.getId());
        } finally {
            redisDistributedLockService.releaseLock(lockKey);
        }
    }

    private void acquireLock(String lockKey) {
        boolean lockAcquired = redisDistributedLockService.acquireLock(lockKey, Duration.ofSeconds(30));
        if (!lockAcquired) {
            throw new BadRequestException("이미 처리 중인 답변이 있습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private static void validateHasToken(Member member) {
        if (!member.hasEnoughTokenCount(1)) {
            throw new BadRequestException("토큰을 이미 모두 소진하였습니다.");
        }
    }

    private QuestionAndAnswers createQuestionAndAnswers(Long curQuestionId, AnswerRequest answerRequest, Interview interview) {
        List<Question> questions = questionRepository.findByInterview(interview);
        List<Answer> prevAnswers = answerRepository.findByQuestionIn(questions);
        return new QuestionAndAnswers(questions, prevAnswers, answerRequest.answer(), curQuestionId, interview);
    }

    // TODO: 한 명의 사용자가 계속 요청했을 떄 Unique 제약조건에 의해 락 대기가 발생할 수 있음 -> DB에 과부하가 가지 않도록 redis 사용하거나 rate limiter 사용 고려
    @Transactional
    public void likeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Interview interview = readInterview(interviewId);
        if (interviewLikeRepository.existsByMemberIdAndInterviewId(member.getId(), interviewId)) {
            throw new BadRequestException("이미 좋아요를 누른 인터뷰입니다.");
        }
        interviewLikeRepository.save(new InterviewLike(member, interview));
        interviewRepository.increaseLikeCount(interviewId);
    }

    @Transactional
    public void unlikeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Interview interview = readInterview(interviewId);
        int affectedRows = interviewLikeRepository.deleteByMemberAndInterview(member, interview);
        if (affectedRows == 0) {
            throw new BadRequestException("좋아요를 누르지 않은 인터뷰입니다.");
        }
        interviewRepository.decreaseLikeCount(interviewId);
    }

    // TODO: 인터뷰 안 끝나면 예외 던지기
    @Transactional(readOnly = true)
    public InterviewResultResponse findMyResults(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        validateInterviewFinished(interview);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));
        List<FeedbackResponse> feedbackResponses = FeedbackResponse.createMine(answers);

        return InterviewResultResponse.createMyResultResponse(feedbackResponses, interview, member);
    }

    @Transactional(readOnly = true)
    public InterviewResultResponse findResults(Long interviewId, MemberAuth memberAuth) {
        Interview interview = readInterview(interviewId);
        validateInterviewFinished(interview);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));
        if (memberAuth.isAuthenticated()) {
            Member readerMember = readMember(memberAuth.memberId());
            List<Long> answerIds = answers.stream().map(Answer::getId).toList();
            List<Long> likedAnswerIds = answerLikeRepository.findLikedAnswerIds(readerMember.getId(), answerIds);
            List<FeedbackResponse> feedbackResponses = answers.stream()
                    .map(answer -> new FeedbackResponse(answer, likedAnswerIds.contains(answer.getId())))
                    .toList();
            boolean interviewAlreadyLiked = interviewLikeRepository.existsByMemberIdAndInterviewId(readerMember.getId(), interview.getId());
            return InterviewResultResponse.createResultResponse(feedbackResponses, interview, interviewAlreadyLiked);
        }

        List<FeedbackResponse> feedbackResponses = FeedbackResponse.createForNotAuthenticatedUser(answers);
        return InterviewResultResponse.createResultResponse(feedbackResponses, interview, false);
    }

    private void validateInterviewFinished(Interview interview) {
        if (interview.isInProgress()) {
            throw new BadRequestException("해당 인터뷰는 아직 진행 중입니다. 인터뷰가 종료된 후 결과를 조회할 수 있습니다.");
        }
    }

    @Transactional(readOnly = true)
    public InterviewResponse findInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        List<Question> questions = questionRepository.findByInterviewOrderById(interview);
        List<Answer> answers = answerRepository.findByQuestionInOrderById(questions);

        return InterviewResponse.of(interview, questions, answers);
    }

    @Transactional(readOnly = true)
    public List<InterviewSummaryResponse> findMyInterviews(MemberAuth memberAuth, InterviewState state, Pageable pageable) {
        Member member = readMember(memberAuth.memberId());
        List<Interview> interviews = findInterviews(member, state, pageable);
        List<Long> finishedInterviewIds = interviews.stream()
                .filter(interview -> !interview.isInProgress())
                .map(Interview::getId)
                .toList();
        List<Long> likedInterviewIds = interviewLikeRepository.findLikedInterviewIds(member.getId(), finishedInterviewIds);

        return interviews.stream()
                .map(interview -> InterviewSummaryResponse.createMine(interview, countCurAnswers(interview), likedInterviewIds.contains(interview.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewSummaryResponse> findMemberInterviews(Long targetMemberId, MemberAuth memberAuth, Pageable pageable) {
        Member targetMember = readMember(targetMemberId);
        List<Interview> interviews = findInterviews(targetMember, InterviewState.FINISHED, pageable);
        if (memberAuth.isAuthenticated()) {
            Member readerMember = readMember(memberAuth.memberId());
            List<Long> interviewIds = interviews.stream().map(Interview::getId).toList();
            List<Long> likedInterviewIds = interviewLikeRepository.findLikedInterviewIds(readerMember.getId(), interviewIds);

            return interviews.stream()
                    .map(interview -> InterviewSummaryResponse.createOfTargetMember(interview, likedInterviewIds.contains(interview.getId())))
                    .toList();
        }
        return interviews.stream()
                .map(interview -> InterviewSummaryResponse.createOfTargetMember(interview, false))
                .toList();
    }

    // TODO: 동적 쿼리 개선하기
    private List<Interview> findInterviews(Member member, InterviewState state, Pageable pageable) {
        if (state == null) {
            return interviewRepository.findByMember(member, pageable);
        }
        return interviewRepository.findByMemberAndInterviewState(member, state, pageable);
    }

    private int countCurAnswers(Interview interview) {
        int qurQuestionCount = questionRepository.countByInterview(interview);

        // TODO: 해당 로직 적절한 도메인에 부여하기
        if (interview.isInProgress()) {
            return qurQuestionCount - 1;
        }
        return qurQuestionCount;
    }

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    private Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
    }

    private void validateInterviewee(Interview interview, Member member) {
        if (!interview.isInterviewee(member)) {
            throw new ForbiddenException("해당 인터뷰를 생성한 회원이 아닙니다.");
        }
    }
}
