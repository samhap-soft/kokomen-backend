package com.samhap.kokomen.answer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.answer.service.event.AnswerLikedEvent;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AnswerFacadeServiceTest extends BaseTest {

    @Autowired
    private AnswerFacadeService answerFacadeService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private TestAnswerLikedEventListener testAnswerLikedEventListener;

    @Test
    void 답변에_좋아요를_누르면_최신_좋아요_수로_이벤트가_발행된다() {
        // given
        testAnswerLikedEventListener.clear();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());
        MemberAuth memberAuth = new MemberAuth(member.getId());
        Long beforeLikeCount = answer.getLikeCount();

        // when
        answerFacadeService.likeAnswer(answer.getId(), memberAuth);

        // then
        Answer updatedAnswer = answerRepository.findById(answer.getId()).get();
        assertThat(updatedAnswer.getLikeCount()).isEqualTo(beforeLikeCount + 1);
        // 이벤트 리스너로 발행된 이벤트의 likeCount 값도 검증
        List<AnswerLikedEvent> events = testAnswerLikedEventListener.getEvents();
        assertThat(events).hasSize(1);
        AnswerLikedEvent event = events.get(0);
        assertThat(event.likeCount()).isEqualTo(updatedAnswer.getLikeCount());
    }
}
