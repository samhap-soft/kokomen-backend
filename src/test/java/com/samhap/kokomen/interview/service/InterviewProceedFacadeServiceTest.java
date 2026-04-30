package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewProceedFacadeServiceTest extends BaseTest {

    @Autowired
    private InterviewProceedFacadeService interviewProceedFacadeService;
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
    @Autowired
    private RedisService redisService;

    @Test
    void 진행중인_인터뷰_진행_상황을_폴링으로_조회할_때_현재_질문_id가_아니라면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(AnswerRank.B).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(AnswerRank.A).build());
        questionRepository.save(QuestionFixtureBuilder.builder().build());

        String interviewProceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interview.getId(),
                question2.getId());
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.findInterviewProceedState(interview.getId(), question1.getId(),
                InterviewMode.TEXT,
                new MemberAuth(member.getId()), new ClientIp("0.0.0.0")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("현재 질문이 아닙니다. 현재 질문 id: 2");
    }

    @Test
    void 완료된_인터뷰_진행_상황을_폴링으로_조회할_때_현재_질문_id가_아니라면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(AnswerRank.B).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(AnswerRank.A).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question3).answerRank(AnswerRank.A).build());

        String interviewProceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interview.getId(),
                question3.getId());
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.findInterviewProceedState(interview.getId(), question2.getId(),
                InterviewMode.TEXT,
                new MemberAuth(member.getId()), new ClientIp("0.0.0.0")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("현재 질문이 아닙니다. 현재 질문 id: 3");
    }

    @Test
    void 인터뷰_진행_상황을_폴링으로_조회할_때_레디스에_LLM_진행_상태가_없고_답변이_없는_질문으로_요청이_오면_FAILED를_응답한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(AnswerRank.B).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());

        // when
        InterviewProceedStateResponse interviewProceedState =
                interviewProceedFacadeService.findInterviewProceedState(interview.getId(), question2.getId(),
                        InterviewMode.TEXT, new MemberAuth(member.getId()), new ClientIp("0.0.0.0"));

        // then
        assertThat(interviewProceedState.proceedState()).isEqualTo(InterviewProceedState.LLM_FAILED);
    }
}
