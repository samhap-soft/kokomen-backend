package com.samhap.kokomen.interview.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewCategory;
import com.samhap.kokomen.interview.domain.Question;
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
            Long questionId,
            AnswerRequest answerRequest,
            MemberAuth memberAuth
    ) {
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        List<Question> questions = readSortedQuestions(interview);
        Question curQuestion = readCurrentQuestion(questions, questionId);
        List<Answer> prevAnswers = readSortedAnswers(questions);
        String curAnswerContent = answerRequest.answer();
        GptResponse gptResponse = gptClient.requestToGpt(prevAnswers, curQuestion, curAnswerContent);
        Answer curAnswer = saveCurrentAnswer(curQuestion, curAnswerContent, gptResponse);

        if (gptClient.isProceedRequest(prevAnswers)) {
            Question next = saveNextQuestion(gptResponse, interview, curQuestion);
            return Optional.of(NextQuestionResponse.createFollowingQuestionResponse(next));
        }

        evaluateInterview(interview, curAnswer, prevAnswers, gptResponse, member);
        return Optional.empty();
    }

    private List<Question> readSortedQuestions(Interview interview) {
        List<Question> questions = questionRepository.findByInterview(interview);
        if (questions.isEmpty()) {
            throw new IllegalStateException("인터뷰에 질문이 없습니다.");
        }
        questions.sort(Comparator.comparing(Question::getId));
        return questions;
    }

    private Question readCurrentQuestion(List<Question> questions, Long questionId) {
        Question curQuestion = questions.get(questions.size() - 1);
        if (!curQuestion.getId().equals(questionId)) {
            throw new IllegalArgumentException("마지막 질문이 아닙니다. 마지막 질문: " + curQuestion.getContent());
        }
        return curQuestion;
    }

    private Answer saveCurrentAnswer(Question curQuestion, String curAnswerContent, GptResponse gptResponse) {
        GptFeedbackResponse feedback = gptResponse.extractGptFeedbackResponse(objectMapper);
        Answer answer = new Answer(
                curQuestion,
                curAnswerContent,
                AnswerRank.valueOf(feedback.rank()),
                feedback.feedback()
        );
        return answerRepository.save(answer);
    }

    private Question saveNextQuestion(GptResponse gptResponse, Interview interview, Question curQuestion) {
        GptNextQuestionResponse gptNextQuestionResponse = gptResponse.extractGptNextQuestionResponse(objectMapper);
        Question next = new Question(interview, curQuestion.getRootQuestion(), gptNextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(Interview interview, Answer curAnswer, List<Answer> prevAnswers, GptResponse gptResponse, Member member) {
        GptTotalFeedbackResponse gptTotalFeedbackResponse = gptResponse.extractGptTotalFeedbackResponse(objectMapper);
        int totalScore = prevAnswers.stream()
                .map(answer -> answer.getAnswerRank().getScore())
                .reduce(curAnswer.getAnswerRank().getScore(), Integer::sum);

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
        List<Answer> answers = readSortedAnswers(questionRepository.findByInterview(interview));

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

    private List<Answer> readSortedAnswers(List<Question> questions) {
        List<Answer> answers = answerRepository.findByQuestionIn(questions);
        if (answers.isEmpty()) {
            throw new IllegalStateException("인터뷰에 답변이 없습니다.");
        }
        answers.sort(Comparator.comparing(Answer::getId));
        return answers;
    }
}
