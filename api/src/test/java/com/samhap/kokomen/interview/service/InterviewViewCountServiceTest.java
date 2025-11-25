package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doThrow;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewViewCountServiceTest extends BaseTest {

    @Autowired
    private InterviewViewCountService interviewViewCountService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void 다른_사용자의_인터뷰_최종_결과를_조회하면_조회수가_1_증가한다() {
        // given
        Member interviewee = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(interviewee).rootQuestion(rootQuestion).viewCount(0L).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.")
                        .answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D)
                        .feedback("부족합니다.").build());
        Question question3 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F)
                        .feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);

        // when
        Long viewCount = interviewViewCountService.incrementViewCount(interview, new MemberAuth(otherMember.getId()),
                new ClientIp("1.1.1.1"));

        // then

        assertAll(
                () -> assertThat(viewCount).isEqualTo(1L)
        );
    }

    @Test
    void 다른_사용자의_인터뷰_최종_결과를_연속으로_조회해도_조회수는_1만_증가한다() {
        // given
        Member interviewee = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(interviewee).rootQuestion(rootQuestion).viewCount(0L).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.")
                        .answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D)
                        .feedback("부족합니다.").build());
        Question question3 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F)
                        .feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);

        // when
        interviewViewCountService.incrementViewCount(interview, new MemberAuth(otherMember.getId()),
                new ClientIp("1.1.1.1"));
        interviewViewCountService.incrementViewCount(interview, new MemberAuth(otherMember.getId()),
                new ClientIp("1.1.1.1"));
        Long viewCount = interviewViewCountService.incrementViewCount(interview, new MemberAuth(otherMember.getId()),
                new ClientIp("1.1.1.1"));

        // then
        assertThat(viewCount).isEqualTo(1L);
    }

    @Test
    void 자신의_인터뷰_최종_결과를_조회하면_조회수는_증가하지_않는다() {
        // given
        Member interviewee = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(interviewee).rootQuestion(rootQuestion).viewCount(0L).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.")
                        .answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D)
                        .feedback("부족합니다.").build());
        Question question3 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F)
                        .feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);

        interviewViewCountService.incrementViewCount(interview, new MemberAuth(otherMember.getId()),
                new ClientIp("123.123.123.123"));

        // when
        Long viewCount = interviewViewCountService.incrementViewCount(interview, new MemberAuth(interviewee.getId()),
                new ClientIp("1.1.1.1"));

        // then
        assertThat(viewCount).isEqualTo(1L);
    }

    @Test
    void 여러명이_동시에_인터뷰_최종_결과를_조회하면_정확하게_사람_수만큼_조회수가_증가한다() throws InterruptedException {
        // given
        Member interviewee = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(0L).build());

        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(interviewee).rootQuestion(rootQuestion).viewCount(0L).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.")
                        .answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D)
                        .feedback("부족합니다.").build());
        Question question3 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F)
                        .feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        // when
        for (int i = 1; i <= 10; i++) {
            ClientIp clientIp = new ClientIp("%d.%d.%d.%d".formatted(i, i, i, i));
            executorService.execute(
                    () -> interviewViewCountService.incrementViewCount(interview, MemberAuth.notAuthenticated(),
                            clientIp));
        }

        executorService.shutdown();
        executorService.awaitTermination(3, TimeUnit.SECONDS);

        // then
        Long viewCount = Long.valueOf((String) redisTemplate.opsForValue()
                .get(interviewViewCountService.createInterviewViewCountKey(interview)));
        assertThat(viewCount).isEqualTo(10L);
    }

    @Test
    void 레디스에서_예외가_발생하더라도_실패하지_않고_DB에서_조회수를_가져온다() {
        // given
        doThrow(new IllegalStateException("강제 예외")).when(redisTemplate).opsForValue();
        Member interviewee = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(interviewee).rootQuestion(rootQuestion).viewCount(0L).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.")
                        .answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D)
                        .feedback("부족합니다.").build());
        Question question3 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F)
                        .feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);

        // when
        Long viewCount = interviewViewCountService.incrementViewCount(interview, new MemberAuth(otherMember.getId()),
                new ClientIp("1.1.1.1"));

        // then
        assertThat(viewCount).isEqualTo(0L);
    }

    @Test
    void 인터뷰_조회수_조회_시_레디스에서_예외가_발생하더라도_실패하지_않고_DB에서_조회수를_가져온다() {
        // given
        doThrow(new IllegalStateException("강제 예외")).when(redisTemplate).opsForValue();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).likeCount(1L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        // when
        Long viewCount = interviewViewCountService.findViewCount(interview1);

        // then
        assertThat(viewCount).isEqualTo(0L);
    }
}
