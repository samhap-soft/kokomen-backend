package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
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

    @Test
    void 루트질문과_랭크로_상위_답변들을_인터뷰_좋아요순으로_조회한다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("자바의 특징은?").questionOrder(10).build());
        
        // A 랭크 답변들 (인터뷰 좋아요 수: 10, 5, 1)
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().nickname("김철수").kakaoId(1L).build());
        Interview interview1 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member1).rootQuestion(rootQuestion).likeCount(10L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answerA1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).content("답변1").answerRank(AnswerRank.A).build());

        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().nickname("이영희").kakaoId(2L).build());
        Interview interview2 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion).likeCount(5L).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview2).build());
        Answer answerA2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).content("답변2").answerRank(AnswerRank.A).build());

        Member member3 = memberRepository.save(MemberFixtureBuilder.builder().nickname("박민수").kakaoId(3L).build());
        Interview interview3 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion).likeCount(1L).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview3).build());
        Answer answerA3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).content("답변3").answerRank(AnswerRank.A).build());

        // B 랭크 답변 (인터뷰 좋아요 수: 3)
        Member member4 = memberRepository.save(MemberFixtureBuilder.builder().nickname("정수진").kakaoId(4L).build());
        Interview interview4 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member4).rootQuestion(rootQuestion).likeCount(3L).build());
        Question question4 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview4).build());
        Answer answerB1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question4).content("답변4").answerRank(AnswerRank.B).build());

        // when - A 랭크 답변 2개 조회
        List<Answer> aRankAnswers = answerRepository.findTopAnswersByRootQuestionAndRank(rootQuestion.getId(), AnswerRank.A, -1L, 2);

        // then - 인터뷰 좋아요 수가 높은 순으로 정렬되어 반환
        assertThat(aRankAnswers).hasSize(2);
        assertThat(aRankAnswers.get(0).getId()).isEqualTo(answerA1.getId()); // 좋아요 10개
        assertThat(aRankAnswers.get(1).getId()).isEqualTo(answerA2.getId()); // 좋아요 5개
    }

    @Test
    void 특정_인터뷰를_제외하고_답변을_조회한다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("자바의 특징은?").questionOrder(10).build());
        
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().nickname("김철수").kakaoId(5L).build());
        Interview interview1 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member1).rootQuestion(rootQuestion).likeCount(10L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).content("답변1").answerRank(AnswerRank.A).build());

        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().nickname("이영희").kakaoId(6L).build());
        Interview interview2 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion).likeCount(5L).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview2).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).content("답변2").answerRank(AnswerRank.A).build());

        // when - interview1을 제외하고 조회
        List<Answer> answers = answerRepository.findTopAnswersByRootQuestionAndRank(rootQuestion.getId(), AnswerRank.A, interview1.getId(), 10);

        // then - interview1의 답변은 제외되고 interview2의 답변만 반환
        assertThat(answers).hasSize(1);
        assertThat(answers.get(0).getId()).isEqualTo(answer2.getId());
    }

    @Test
    void 다른_루트질문의_답변은_조회되지_않는다() {
        // given
        RootQuestion rootQuestion1 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("자바의 특징은?").questionOrder(1).build());
        RootQuestion rootQuestion2 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("스프링의 특징은?").questionOrder(2).build());
        
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().nickname("김철수").kakaoId(1L).build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().nickname("이영희").kakaoId(2L).build());
        
        // rootQuestion1에 대한 답변
        Interview interview1 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member1).rootQuestion(rootQuestion1).likeCount(10L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).content("자바답변").answerRank(AnswerRank.A).build());

        // rootQuestion2에 대한 답변
        Interview interview2 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion2).likeCount(5L).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview2).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).content("스프링답변").answerRank(AnswerRank.A).build());

        // when - rootQuestion2에 대한 답변만 조회
        List<Answer> answers = answerRepository.findTopAnswersByRootQuestionAndRank(rootQuestion2.getId(), AnswerRank.A, -1L, 10);

        // then - rootQuestion2의 답변만 반환
        assertThat(answers).hasSize(1);
        assertThat(answers.get(0).getId()).isEqualTo(answer2.getId());
        assertThat(answers.get(0).getContent()).isEqualTo("스프링답변");
    }
}
