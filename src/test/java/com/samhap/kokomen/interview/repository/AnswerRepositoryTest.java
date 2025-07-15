package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AnswerRepositoryTest extends BaseTest {

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
    void 답변이_회원에게_속하면_true를_반환한다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());

        // when
        boolean exists = answerRepository.existsByIdAndQuestionInterviewMemberId(answer.getId(), member.getId());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void 답변이_회원에게_속하지_않으면_false를_반환한다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());

        // when
        boolean exists = answerRepository.existsByIdAndQuestionInterviewMemberId(answer.getId(), otherMember.getId());

        // then
        assertThat(exists).isFalse();
    }

    @Test
    void 질문들로_답변들을_찾는다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());

        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        // when
        List<Answer> answers = answerRepository.findByQuestionIn(List.of(question1, question2));

        // then
        assertThat(answers)
                .extracting(Answer::getId)
                .containsExactlyInAnyOrder(answer1.getId(), answer2.getId());
    }
}
