package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.LLMResponse;
import com.samhap.kokomen.interview.external.dto.response.NextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.FeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.InterviewTotalResponse;
import com.samhap.kokomen.interview.service.dto.MyInterviewResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
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
    private final ObjectMapper objectMapper;

    @Transactional
    public InterviewStartResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
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
    @Transactional
    public Optional<InterviewProceedResponse> proceedInterview(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        decreaseTokenCount(memberAuth.memberId());
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(curQuestionId, answerRequest, interview);

        LLMResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, llmResponse);

        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(llmResponse, interview);
            return Optional.of(InterviewProceedResponse.createFollowingQuestionResponse(curAnswer, nextQuestion));
        }

        evaluateInterview(interview, questionAndAnswers, curAnswer, llmResponse, member);
        return Optional.empty();
    }

    private void decreaseTokenCount(Long memberId) {
        int affectedRows = memberRepository.decreaseFreeTokenCount(memberId);
        if (affectedRows == 0) {
            throw new BadRequestException("회원의 토큰 개수가 부족해 인터뷰를 더 이상 진행할 수 없습니다.");
        }
    }

    private QuestionAndAnswers createQuestionAndAnswers(Long curQuestionId, AnswerRequest answerRequest, Interview interview) {
        List<Question> questions = questionRepository.findByInterview(interview);
        List<Answer> prevAnswers = answerRepository.findByQuestionIn(questions);
        return new QuestionAndAnswers(questions, prevAnswers, answerRequest.answer(), curQuestionId, interview);
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, LLMResponse llmResponse) {
        AnswerFeedbackResponse feedback = llmResponse.extractAnswerFeedbackResponse(objectMapper);
        return answerRepository.save(questionAndAnswers.createCurAnswer(feedback));
    }

    private Question saveNextQuestion(LLMResponse llmResponse, Interview interview) {
        NextQuestionResponse nextQuestionResponse = llmResponse.extractNextQuestionResponse(objectMapper);
        Question next = new Question(interview, nextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(Interview interview, QuestionAndAnswers questionAndAnswers, Answer curAnswer, LLMResponse llmResponse, Member member) {
        TotalFeedbackResponse totalFeedbackResponse = llmResponse.extractTotalFeedbackResponse(objectMapper);
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        interview.evaluate(totalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }

    // TODO: 인터뷰 안 끝나면 예외 던지기
    @Transactional(readOnly = true)
    public InterviewTotalResponse findTotalFeedbacks(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));

        List<FeedbackResponse> feedbackResponses = FeedbackResponse.from(answers);

        return InterviewTotalResponse.of(feedbackResponses, interview, member);
    }

    @Transactional(readOnly = true)
    public InterviewResponse findInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        List<Question> questions = questionRepository.findByInterviewOrderById(interview);
        List<Answer> answers = answerRepository.findByQuestionInOrderById(questions);

        return InterviewResponse.of(interview, questions, answers);
    }

    @Transactional(readOnly = true)
    public List<MyInterviewResponse> findMyInterviews(MemberAuth memberAuth, InterviewState state, Pageable pageable) {
        Member member = readMember(memberAuth);
        List<Interview> interviews = findInterviews(member, state, pageable);

        return interviews.stream()
                .map(interview -> new MyInterviewResponse(interview, countCurAnswers(interview)))
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

    private Member readMember(MemberAuth memberAuth) {
        return memberRepository.findById(memberAuth.memberId())
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
