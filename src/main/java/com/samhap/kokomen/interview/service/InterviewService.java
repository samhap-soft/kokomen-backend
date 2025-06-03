package com.samhap.kokomen.interview.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewCategory;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.response.GptFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.GptNextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.GptTotalFeedbackResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewCategoryRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.FeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewTotalResponse;
import com.samhap.kokomen.interview.service.dto.NextQuestionResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
// TODO: 루트 질문 가져올 때 AtomicLong 이용해서 순서대로 하나씩 가져오기
public class InterviewService {

    private static final AtomicLong rootQuestionIdGenerator = new AtomicLong(1);

    private final GptClient gptClient;
    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final MemberRepository memberRepository;
    private final InterviewCategoryRepository interviewCategoryRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public InterviewResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        List<Category> categories = interviewRequest.categories();
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("카테고리가 없습니다.");
        }

        Member member = readMember(memberAuth);
        Interview interview = interviewRepository.save(new Interview(member));
        categories.forEach(category -> interviewCategoryRepository.save(new InterviewCategory(interview, category)));

        RootQuestion rootQuestion = findRandomRootQuestion();

        Question question = questionRepository.save(new Question(interview, rootQuestion, rootQuestion.getContent()));

        return new InterviewResponse(interview, question);
    }

    private RootQuestion findRandomRootQuestion() {
        Long rootQuestionId = (rootQuestionIdGenerator.getAndIncrement()) % rootQuestionRepository.count() + 1;

        return rootQuestionRepository.findById(rootQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 루트 질문입니다."));
    }

    // TODO: answer가 question을 들고 있는데, 영속성 컨텍스트를 활용해서 가져오는지 -> lazy 관련해서
    @Transactional
    public Optional<NextQuestionResponse> proceedInterview(
            Long interviewId,
            Long curQuestionId,
            AnswerRequest answerRequest,
            MemberAuth memberAuth
    ) {
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(curQuestionId, answerRequest, interview);
        GptResponse gptResponse = gptClient.requestToGpt(questionAndAnswers);
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, gptResponse);

        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(gptResponse, interview, questionAndAnswers.readCurQuestion());
            return Optional.of(NextQuestionResponse.createFollowingQuestionResponse(nextQuestion));
        }

        evaluateInterview(interview, questionAndAnswers, curAnswer, gptResponse, member);
        return Optional.empty();
    }

    private QuestionAndAnswers createQuestionAndAnswers(Long curQuestionId, AnswerRequest answerRequest, Interview interview) {
        List<Question> questions = questionRepository.findByInterview(interview);
        List<Answer> prevAnswers = answerRepository.findByQuestionIn(questions);
        return new QuestionAndAnswers(questions, prevAnswers, answerRequest.answer(), curQuestionId);
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, GptResponse gptResponse) {
        GptFeedbackResponse feedback = gptResponse.extractGptFeedbackResponse(objectMapper);
        return answerRepository.save(questionAndAnswers.createCurAnswer(feedback));
    }

    private Question saveNextQuestion(GptResponse gptResponse, Interview interview, Question curQuestion) {
        GptNextQuestionResponse gptNextQuestionResponse = gptResponse.extractGptNextQuestionResponse(objectMapper);
        Question next = new Question(interview, curQuestion.getRootQuestion(), gptNextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(Interview interview, QuestionAndAnswers questionAndAnswers, Answer curAnswer, GptResponse gptResponse, Member member) {
        GptTotalFeedbackResponse gptTotalFeedbackResponse = gptResponse.extractGptTotalFeedbackResponse(objectMapper);
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        interview.evaluate(gptTotalFeedbackResponse.totalFeedback(), totalScore);
        member.updateScore(totalScore);
    }

    @Transactional(readOnly = true)
    public InterviewTotalResponse findTotalFeedbacks(
            Long interviewId,
            MemberAuth memberAuth
    ) {
        Interview interview = readInterview(interviewId);
        Member member = readMember(memberAuth);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));

        List<FeedbackResponse> feedbackResponses = FeedbackResponse.from(answers);

        return InterviewTotalResponse.of(feedbackResponses, interview, member);
    }

    private Member readMember(MemberAuth memberAuth) {
        return memberRepository.findById(memberAuth.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }

    private Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 인터뷰입니다."));
    }
}
