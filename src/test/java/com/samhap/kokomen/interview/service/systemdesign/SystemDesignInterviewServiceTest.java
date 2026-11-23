package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignEvaluationPointFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignInterviewServiceTest extends BaseTest {

    private static final String PROBLEM_UNAVAILABLE_MESSAGE = "시스템 설계 문제를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
    private static final List<SystemDesignStage> VALID_STAGES = List.of(
            SystemDesignStage.HIGH_LEVEL, SystemDesignStage.HIGH_LEVEL, SystemDesignStage.HIGH_LEVEL,
            SystemDesignStage.DEEP_DIVE, SystemDesignStage.DEEP_DIVE, SystemDesignStage.DEEP_DIVE);

    @Autowired
    private SystemDesignInterviewService systemDesignInterviewService;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignEvaluationPointRepository evaluationPointRepository;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;

    @Test
    void 토큰이_정확히_6개면_시작할_수_있다() {
        // given
        Member member = saveMemberWithTokens(4, 2);
        problemTestSupport.saveProblem();

        // when
        SystemDesignInterviewStartResponse response = systemDesignInterviewService.start(member.getId());

        // then
        assertThat(systemDesignInterviewRepository.findByInterviewId(response.interviewId())).isPresent();
    }

    @Test
    void ACTIVE_BASIC_문제_중_하나로_시작한다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignProblem first = problemTestSupport.saveProblem();
        SystemDesignProblem second = problemTestSupport.saveProblem();

        // when
        SystemDesignInterviewStartResponse response = systemDesignInterviewService.start(member.getId());

        // then
        SystemDesignInterview started = systemDesignInterviewRepository.findByInterviewId(response.interviewId())
                .orElseThrow();
        Interview interview = started.getInterview();
        assertAll(
                () -> assertThat(started.getProblem().getId()).isIn(first.getId(), second.getId()),
                () -> assertThat(started.getStage()).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(started.getCurrentDiagram())
                        .isEqualTo(SystemDesignDiagramFixture.urlShortenerStarter()),
                () -> assertThat(started.getReportScore()).isNull(),
                () -> assertThat(interview.getId()).isEqualTo(response.interviewId()),
                () -> assertThat(interview.getMember().getId()).isEqualTo(member.getId()),
                () -> assertThat(interview.getInterviewType()).isEqualTo(InterviewType.SYSTEM_DESIGN),
                () -> assertThat(interview.getInterviewMode()).isEqualTo(InterviewMode.TEXT),
                () -> assertThat(interview.getInterviewState()).isEqualTo(InterviewState.IN_PROGRESS),
                () -> assertThat(interview.getMaxQuestionCount()).isEqualTo(6),
                () -> assertThat(interview.getRootQuestion()).isNull()
        );
    }

    // 배치가 온전한 문제라 ACTIVE 조건이 빠지면 이 문제로 시작돼 실패한다.
    @Test
    void INACTIVE_문제로는_시작하지_않는다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        saveProblem(SystemDesignProblemState.INACTIVE, VALID_STAGES, 1);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PROBLEM_UNAVAILABLE_MESSAGE);
        assertNoInterviewStarted();
    }

    @Test
    void 문제가_없으면_500이다() {
        // given
        Member member = saveMemberWithTokens(6, 0);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PROBLEM_UNAVAILABLE_MESSAGE);
        assertNoInterviewStarted();
    }

    @Test
    void 기준_질문이_6개가_아닌_문제로는_시작하지_않는다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        saveProblem(SystemDesignProblemState.ACTIVE, VALID_STAGES.subList(0, 5), 1);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PROBLEM_UNAVAILABLE_MESSAGE);
        assertNoInterviewStarted();
    }

    @Test
    void 기준_질문_단계가_틀린_문제로는_시작하지_않는다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        saveProblem(SystemDesignProblemState.ACTIVE, List.of(
                SystemDesignStage.HIGH_LEVEL, SystemDesignStage.HIGH_LEVEL, SystemDesignStage.HIGH_LEVEL,
                SystemDesignStage.HIGH_LEVEL, SystemDesignStage.DEEP_DIVE, SystemDesignStage.DEEP_DIVE), 1);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PROBLEM_UNAVAILABLE_MESSAGE);
        assertNoInterviewStarted();
    }

    // 6개이고 순번마다 단계도 맞지만 6번 대신 7번이 있다.
    @Test
    void 기준_질문_순번이_1부터_6이_아닌_문제로는_시작하지_않는다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignProblem problem = saveProblem(SystemDesignProblemState.ACTIVE, VALID_STAGES.subList(0, 5), 1);
        saveProblemQuestion(problem, 7, SystemDesignStage.DEEP_DIVE);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PROBLEM_UNAVAILABLE_MESSAGE);
        assertNoInterviewStarted();
    }

    @Test
    void 평가_포인트가_없는_문제로는_시작하지_않는다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        saveProblem(SystemDesignProblemState.ACTIVE, VALID_STAGES, 0);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PROBLEM_UNAVAILABLE_MESSAGE);
        assertNoInterviewStarted();
    }

    @Test
    void 시작_다이어그램이_없으면_빈_다이어그램으로_시작한다() {
        // given
        Member member = saveMemberWithTokens(6, 0);
        problemTestSupport.saveProblem(null);

        // when
        SystemDesignInterviewStartResponse response = systemDesignInterviewService.start(member.getId());

        // then
        SystemDesignInterview started = systemDesignInterviewRepository.findByInterviewId(response.interviewId())
                .orElseThrow();
        assertAll(
                () -> assertThat(started.getCurrentDiagram().blocks()).isEmpty(),
                () -> assertThat(started.getCurrentDiagram().connections()).isEmpty()
        );
    }

    @Test
    void 시작해도_토큰을_차감하지_않는다() {
        // given
        Member member = saveMemberWithTokens(10, 3);
        problemTestSupport.saveProblem();

        // when
        systemDesignInterviewService.start(member.getId());

        // then
        assertAll(
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(10),
                () -> assertThat(readTokenCount(member, TokenType.PAID)).isEqualTo(3)
        );
    }

    private Member saveMemberWithTokens(int freeTokenCount, int paidTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(paidTokenCount).build());
        return member;
    }

    // 순번 1부터 stages 순서대로 기준 질문을 두고 평가 포인트를 pointCount개 둔 BASIC 문제
    private SystemDesignProblem saveProblem(SystemDesignProblemState state, List<SystemDesignStage> stages,
                                            int pointCount) {
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(state)
                .build());
        for (int order = 1; order <= stages.size(); order++) {
            saveProblemQuestion(problem, order, stages.get(order - 1));
        }
        for (int order = 1; order <= pointCount; order++) {
            evaluationPointRepository.save(SystemDesignEvaluationPointFixtureBuilder.builder()
                    .problem(problem)
                    .code("POINT_" + order)
                    .displayOrder(order)
                    .build());
        }
        return problem;
    }

    private void saveProblemQuestion(SystemDesignProblem problem, int questionOrder, SystemDesignStage stage) {
        problemQuestionRepository.save(SystemDesignProblemQuestionFixtureBuilder.builder()
                .problem(problem)
                .questionOrder(questionOrder)
                .stage(stage)
                .build());
    }

    private void assertNoInterviewStarted() {
        assertAll(
                () -> assertThat(interviewRepository.count()).isZero(),
                () -> assertThat(systemDesignInterviewRepository.count()).isZero()
        );
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }
}
