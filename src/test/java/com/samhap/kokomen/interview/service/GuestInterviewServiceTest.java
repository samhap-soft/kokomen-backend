package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.core.InterviewService;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GuestInterviewServiceTest extends BaseTest {

    private static final String GUEST_IP = "11.22.33.44";
    private static final String OTHER_IP = "55.66.77.88";

    @Autowired
    private InterviewStartFacadeService interviewStartFacadeService;
    @Autowired
    private InterviewService interviewService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private RedisService redisService;

    @Test
    void 비회원_면접을_시작하면_TEXT_모드_3문항으로_저장되고_Redis_락이_설정된다() {
        // given
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("질문1").build());
        ClientIp clientIp = new ClientIp(GUEST_IP);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startGuestInterview(clientIp);

        // then
        assertThat(response).isInstanceOf(InterviewStartTextModeResponse.class);

        Interview saved = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(saved.getMember()).isNull();
        assertThat(saved.getGuestIp()).isEqualTo(GUEST_IP);
        assertThat(saved.getInterviewMode()).isEqualTo(InterviewMode.TEXT);
        assertThat(saved.getMaxQuestionCount()).isEqualTo(InterviewStartFacadeService.GUEST_INTERVIEW_MAX_QUESTION_COUNT);
        assertThat(saved.getInterviewState()).isEqualTo(InterviewState.IN_PROGRESS);

        String lockKey = InterviewStartFacadeService.createGuestInterviewStartedLockKey(clientIp);
        assertThat(redisService.get(lockKey, String.class)).isPresent();
    }

    @Test
    void 같은_IP로_비회원_면접을_두_번_시작하면_예외가_발생한다() {
        // given
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("질문1").build());
        ClientIp clientIp = new ClientIp(GUEST_IP);
        interviewStartFacadeService.startGuestInterview(clientIp);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startGuestInterview(clientIp))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("비회원 면접은 1회만 가능합니다.");
    }

    @Test
    void 다른_IP는_독립적으로_비회원_면접을_시작할_수_있다() {
        // given
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("질문1").build());
        interviewStartFacadeService.startGuestInterview(new ClientIp(GUEST_IP));

        // when
        InterviewStartResponse response = interviewStartFacadeService.startGuestInterview(new ClientIp(OTHER_IP));

        // then
        Interview saved = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(saved.getGuestIp()).isEqualTo(OTHER_IP);
    }

    @Test
    void 같은_IP의_비회원은_자신의_인터뷰에_접근할_수_있다() {
        // given
        Interview interview = saveGuestInterview(GUEST_IP);

        // when & then - no exception
        interviewService.validateGuestInterviewee(interview.getId(), new ClientIp(GUEST_IP));
    }

    @Test
    void 다른_IP가_비회원_인터뷰에_접근하면_예외가_발생한다() {
        // given
        Interview interview = saveGuestInterview(GUEST_IP);

        // when & then
        assertThatThrownBy(
                () -> interviewService.validateGuestInterviewee(interview.getId(), new ClientIp(OTHER_IP)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 회원이_비회원_인터뷰에_접근하면_예외가_발생한다() {
        // given
        Interview interview = saveGuestInterview(GUEST_IP);

        // when & then
        assertThatThrownBy(() -> interviewService.validateInterviewee(interview.getId(), 999L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 비회원이_종료된_본인_면접_결과를_조회하면_닉네임_없이_응답한다() {
        // given
        Interview interview = saveFinishedGuestInterview(GUEST_IP);

        // when
        InterviewResultResponse result = interviewService.findMyInterviewResult(
                interview.getId(), MemberAuth.notAuthenticated(), new ClientIp(GUEST_IP));

        // then
        assertThat(result.intervieweeNickname()).isNull();
        assertThat(result.userCurScore()).isNull();
        assertThat(result.userPrevScore()).isNull();
        assertThat(result.totalScore()).isEqualTo(interview.getTotalScore());
        assertThat(result.totalFeedback()).isEqualTo(interview.getTotalFeedback());
    }

    @Test
    void 비회원이_다른_IP로_결과를_조회하면_예외가_발생한다() {
        // given
        Interview interview = saveFinishedGuestInterview(GUEST_IP);

        // when & then
        assertThatThrownBy(() -> interviewService.findMyInterviewResult(
                interview.getId(), MemberAuth.notAuthenticated(), new ClientIp(OTHER_IP)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 비회원_면접은_타인_결과_공개_대상에서_제외된다() {
        // given
        Interview interview = saveFinishedGuestInterview(GUEST_IP);

        // when & then
        assertThatThrownBy(() -> interviewService.findOtherMemberInterviewResult(
                interview.getId(), MemberAuth.notAuthenticated(), new ClientIp(OTHER_IP)))
                .isInstanceOf(BadRequestException.class);
    }

    private Interview saveGuestInterview(String guestIp) {
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        return interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(null)
                .rootQuestion(rootQuestion)
                .guestIp(guestIp)
                .interviewMode(InterviewMode.TEXT)
                .maxQuestionCount(InterviewStartFacadeService.GUEST_INTERVIEW_MAX_QUESTION_COUNT)
                .build());
    }

    private Interview saveFinishedGuestInterview(String guestIp) {
        Interview interview = saveGuestInterview(guestIp);
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question).answerRank(AnswerRank.A).build());
        interview.evaluate("총 피드백", 30);
        return interviewRepository.save(interview);
    }
}
