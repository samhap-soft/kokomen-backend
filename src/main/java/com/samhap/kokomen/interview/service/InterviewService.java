package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewCategory;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.request.Message;
import com.samhap.kokomen.interview.external.dto.response.GptAnswerResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewCategoryRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.NextQuestionResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
// TODO: 루트 질문 가져올 때 AtomicLong 이용해서 순서대로 하나씩 가져오기
public class InterviewService {

    private static final int MAX_QUESTION_COUNT = 3;
    private static final AtomicLong rootQuestionIdGenerator = new AtomicLong(1);

    private final GptClient gptClient;
    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final MemberRepository memberRepository;
    private final InterviewCategoryRepository interviewCategoryRepository;
    private final RootQuestionRepository rootQuestionRepository;

    @Transactional
    public InterviewResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        List<Category> categories = interviewRequest.categories();
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("카테고리가 없습니다.");
        }

        Member member = memberRepository.findById(memberAuth.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        Interview interview = interviewRepository.save(new Interview(member));
        categories.forEach(category -> interviewCategoryRepository.save(new InterviewCategory(interview, category)));

        Long rootQuestionId = (rootQuestionIdGenerator.getAndIncrement()) % rootQuestionRepository.count() + 1;
        RootQuestion rootQuestion = rootQuestionRepository.findById(rootQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 루트 질문입니다."));

        Question question = questionRepository.save(new Question(interview, rootQuestion, rootQuestion.getContent()));

        return new InterviewResponse(interview.getId(), question.getId(), rootQuestion.getContent());
    }

    // TODO: answer가 question을 들고 있는데, 영속성 컨텍스트를 활용해서 가져오는지 -> lazy 관련해서
    @Transactional
    public Optional<NextQuestionResponse> proceedInterview(
            Long interviewId,
            Long questionId,
            AnswerRequest answerRequest,
            MemberAuth memberAuth
    ) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 인터뷰입니다."));

        List<Question> questions = questionRepository.findByInterview(interview);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("인터뷰에 질문이 없습니다.");
        }
        questions.sort(Comparator.comparing(Question::getId));
        Question lastQuestion = questions.get(questions.size() - 1);

        if (!lastQuestion.getId().equals(questionId)) {
            throw new IllegalArgumentException("마지막 질문이 아닙니다. 마지막 질문: " + lastQuestion.getContent());
        }

        List<Answer> answers = answerRepository.findByQuestionIn(questions);
        answers.sort(Comparator.comparing(Answer::getId));

        List<Message> messages = new ArrayList<>();

        messages.add(
                new Message("system", """
                                        너는 면접관이야. 존댓말로 대답해줘.
                                        질문과 답변을 전달해주면, 맨 마지막 답변에 대해서만 피드백을 줘.
                                        이 때 피드백은 각 답변에 대해 랭크를 매겨줘야 하는데, A+~F 대학 학점 중 하나로 매겨줘. 기준은 다음과 같아.
                                        A: 딱히 흠잡을 곳이 없고, 논리적으로 잘 설명한 경우. 질문의 요지에 대한 핵심도 잘 파악한 경우
                                        B: 논리적으로 설명이 맞고, 질문의 요지에 대해서도 잘 대답했지만, 중요한 개념을 빠뜨린 것이 있는 경우. 예를 들어, 객체지향의 특징이면 다형성, 캡슐화, 상속, 추상화인데, 1~3개만 설명한 경우. 또는 용어만 헷갈린 경우.
                                        C: 논리적으로 잘못된 부분이 존재하지만, 질문의 요지에 대해서는 제대로 파악한 경우. 물론 논리가 너무 많이 잘못됐다면 D로 갈수도 있음. 예를 들어 객체지향 특징에서 다형성, 캡슐화, 상속, 추상화에 대해 설명했지만, 그 중 하나 이상에 대해 논리적으로 잘못 설명한 경우.
                                        D: 질문의 요지는 제대로 파악했지만, 논리적으로 모두 틀린 경우. 예를 들어 객체지향에서 다형성, 캡슐화, 상속, 추상화에 대해 설명했지만 모두 틀린 경우.
                                        F: 전혀 다른 대답을했거나, 논리적으로 완전히 틀렸거나, 대답 자체를 안한 경우. 예를 들어 객체지향의 특징에 대해 설명하라 했지만 자바 예외처리에 대해 설명한 경우.
                                        또한 코멘트로도 피드백을 주는데, 최대한 자세하게 코멘트해줘.
                                        그와 동시에 꼬리 질문도 해줘.
                        """)
        );

        answers.forEach(answer -> {
            messages.add(new Message("assistant", answer.getQuestion().getContent()));
            messages.add(new Message("user", answer.getContent()));
        });

        messages.add(new Message("assistant", lastQuestion.getContent()));
        messages.add(new Message("user", answerRequest.answer()));

        GptResponse gptResponse = gptClient.requestToGpt(GptRequest.createGptRequest(messages));

        GptAnswerResponse gptAnswerResponse = gptResponse.toGptAnswerResponse();

        Answer lastAnswer = new Answer(
                lastQuestion, answerRequest.answer(),
                AnswerRank.valueOf(gptAnswerResponse.rank()),
                gptAnswerResponse.feedback()
        );

        // TODO: 마지막 답변 여부 체크
        Question nextQuestion = new Question(
                interview, lastQuestion.getRootQuestion(), gptAnswerResponse.nextQuestion()
        );

        answerRepository.save(lastAnswer);

        if (questions.size() >= MAX_QUESTION_COUNT) {
            return Optional.empty();
        }

        questionRepository.save(nextQuestion);

        return Optional.of(new NextQuestionResponse(nextQuestion.getContent()));
    }
}




