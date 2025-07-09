package com.samhap.kokomen.global;

import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
@Slf4j
@Profile("local")
//@Component
public class TestDataInitializer {

    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initData() {
        Member member = memberRepository.save(new Member(1L, "NAK"));
        RootQuestion rootQuestion = rootQuestionRepository.save(new RootQuestion(Category.OPERATING_SYSTEM, "자바의 특징은 무엇인가요?"));
        Interview interview = interviewRepository.save(new Interview(member, rootQuestion, 3));
        Question question1 = questionRepository.save(new Question(interview, rootQuestion.getContent()));
        answerRepository.save(new Answer(question1, "자바는 객체지향 프로그래밍 언어입니다.", AnswerRank.C, "부족합니다."));
        questionRepository.save(new Question(interview, "객체지향의 특징을 설명해주세요."));

        log.info("✅ 초기 테스트 데이터 세팅 완료");
    }
}
