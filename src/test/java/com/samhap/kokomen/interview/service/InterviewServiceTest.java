package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InterviewServiceTest {

    @Autowired
    private InterviewService interviewService;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void proceedInterview() {
        // given
        Member member = memberRepository.save(new Member("NAK"));
        Interview interview = interviewRepository.save(new Interview(member));
        RootQuestion rootQuestion = rootQuestionRepository.save(new RootQuestion("자바의 특징은 무엇인가요?"));
        Question question1 = questionRepository.save(new Question(interview, rootQuestion, rootQuestion.getContent()));
        Answer answer1 = answerRepository.save(new Answer(question1, "자바는 객체지향 프로그래밍 언어입니다.", AnswerRank.C, "부족합니다."));

        Question question2 = questionRepository.save(new Question(interview, rootQuestion, "객체지향의 특징을 설명해주세요."));
        AnswerRequest answerRequest = new AnswerRequest("절차지향 프로그래밍과 반대되는 개념입니다.");

//        interviewService.feedbackAnswerAndCreateNextQuestion(interview.getId(), question2.getId(), answerRequest, null);
    }
}
