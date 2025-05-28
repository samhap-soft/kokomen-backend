package com.samhap.kokomen.global;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Profile("local")
@Component
public class TestDataInitializer {

    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initData() {
        Member member = memberRepository.save(new Member("NAK"));
        Interview interview = interviewRepository.save(new Interview(member));
        RootQuestion rootQuestion = rootQuestionRepository.save(new RootQuestion("자바의 특징은 무엇인가요?"));
        Question question1 = questionRepository.save(new Question(interview, rootQuestion, rootQuestion.getContent()));
        answerRepository.save(new Answer(question1, "자바는 객체지향 프로그래밍 언어입니다.", AnswerRank.C, "부족합니다."));
        questionRepository.save(new Question(interview, rootQuestion, "객체지향의 특징을 설명해주세요."));

        System.out.println("✅ 초기 테스트 데이터 세팅 완료");
    }
}

