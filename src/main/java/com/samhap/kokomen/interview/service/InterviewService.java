package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.response.GptFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.GptNextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.GptTotalFeedbackResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.FeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewTotalResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
// TODO: 루트 질문 가져올 때 AtomicLong 이용해서 순서대로 하나씩 가져오기
public class InterviewService {

    private static final int EXCLUDED_RECENT_ROOT_QUESTION_COUNT = 10;

    private final GptClient gptClient;
    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final MemberRepository memberRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public InterviewResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
        RootQuestion rootQuestion = readRandomRootQuestion(member, interviewRequest);
        Interview interview = interviewRepository.save(new Interview(member, rootQuestion, interviewRequest.maxQuestionCount()));
        Question question = questionRepository.save(new Question(interview, rootQuestion.getContent()));

        return new InterviewResponse(interview, question);
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
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(curQuestionId, answerRequest, interview);
        GptResponse gptResponse = gptClient.requestToGpt(questionAndAnswers);
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, gptResponse);

        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(gptResponse, interview);
            return Optional.of(InterviewProceedResponse.createFollowingQuestionResponse(curAnswer, nextQuestion));
        }

        evaluateInterview(interview, questionAndAnswers, curAnswer, gptResponse, member);
        return Optional.empty();
    }

    private QuestionAndAnswers createQuestionAndAnswers(Long curQuestionId, AnswerRequest answerRequest, Interview interview) {
        List<Question> questions = questionRepository.findByInterview(interview);
        List<Answer> prevAnswers = answerRepository.findByQuestionIn(questions);
        return new QuestionAndAnswers(questions, prevAnswers, answerRequest.answer(), curQuestionId, interview);
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, GptResponse gptResponse) {
        GptFeedbackResponse feedback = gptResponse.extractGptFeedbackResponse(objectMapper);
        return answerRepository.save(questionAndAnswers.createCurAnswer(feedback));
    }

    private Question saveNextQuestion(GptResponse gptResponse, Interview interview) {
        GptNextQuestionResponse gptNextQuestionResponse = gptResponse.extractGptNextQuestionResponse(objectMapper);
        Question next = new Question(interview, gptNextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(Interview interview, QuestionAndAnswers questionAndAnswers, Answer curAnswer, GptResponse gptResponse, Member member) {
        GptTotalFeedbackResponse gptTotalFeedbackResponse = gptResponse.extractGptTotalFeedbackResponse(objectMapper);
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        interview.evaluate(gptTotalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }

    // TODO: 인터뷰 안 끝나면 예외 던지기
    @Transactional(readOnly = true)
    public InterviewTotalResponse findTotalFeedbacks(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));

        List<FeedbackResponse> feedbackResponses = FeedbackResponse.from(answers);

        return InterviewTotalResponse.of(feedbackResponses, interview, member);
    }

    private Member readMember(MemberAuth memberAuth) {
        return memberRepository.findById(memberAuth.memberId())
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    private Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
    }
}
