package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignEvaluationPointFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignQuestionAndAnswerResponse;
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
    private SystemDesignProceedStateStore stateStore;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignEvaluationPointRepository evaluationPointRepository;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private SystemDesignAnswerRepository answerRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
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

    // 문제가 없어도 토큰 부족(400)을 먼저 알려 프론트가 토큰 구매로 안내하게 한다. 문제 오류(500)는 그 뒤다.
    @Test
    void 토큰이_부족하면_문제가_없어도_토큰_오류를_먼저_준다() {
        // given
        Member member = saveMemberWithTokens(5, 0);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewService.start(member.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertNoInterviewStarted();
    }

    @Test
    void ACTIVE_BASIC_문제_중_하나로_시작한다() {
        // given
        saveOtherMemberInterviews();
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

    @Test
    void 끝난_면접은_현재_질문이_null이고_단계는_DEEP_DIVE다() {
        // given
        saveOtherMemberInterviews();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(InterviewState.FINISHED)
                .totalScore(30)
                .build());
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problemTestSupport.saveProblem())
                        .stage(SystemDesignStage.DEEP_DIVE)
                        .currentDiagram(SystemDesignDiagramFixture.withCache())
                        .reportScore(73)
                        .build());
        for (int order = 1; order <= 6; order++) {
            SystemDesignQuestion question = saveQuestion(systemDesignInterview, order);
            answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                    .question(question)
                    .content(order + "번 답변이에요.")
                    .build());
        }

        // when
        SystemDesignInterviewResponse response = systemDesignInterviewService.findInterview(interview.getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(response.interviewState()).isEqualTo(InterviewState.FINISHED),
                () -> assertThat(response.stage()).isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(response.curQuestionId()).isNull(),
                () -> assertThat(response.curQuestion()).isNull(),
                () -> assertThat(response.curAnswerProceedState()).isNull(),
                () -> assertThat(response.curQuestionCount()).isEqualTo(6),
                () -> assertThat(response.prevQuestionsAndAnswers())
                        .extracting(SystemDesignQuestionAndAnswerResponse::questionId,
                                SystemDesignQuestionAndAnswerResponse::stage,
                                SystemDesignQuestionAndAnswerResponse::answer)
                        .containsExactly(
                                tuple(1, SystemDesignStage.HIGH_LEVEL, "1번 답변이에요."),
                                tuple(2, SystemDesignStage.HIGH_LEVEL, "2번 답변이에요."),
                                tuple(3, SystemDesignStage.HIGH_LEVEL, "3번 답변이에요."),
                                tuple(4, SystemDesignStage.DEEP_DIVE, "4번 답변이에요."),
                                tuple(5, SystemDesignStage.DEEP_DIVE, "5번 답변이에요."),
                                tuple(6, SystemDesignStage.DEEP_DIVE, "6번 답변이에요.")),
                () -> assertThat(response.diagram().blocks())
                        .extracting(SystemDesignDiagramResponse.Block::id)
                        .containsExactly("client", "lb", "api", "cache", "db")
        );
    }

    // 끝난 면접에는 답할 질문이 없다. 마지막 질문에 답이 없는 어긋난 데이터여도 현재 질문과 채점 상태를 내보내지 않는다.
    @Test
    void 끝난_면접은_마지막_질문에_답이_없어도_현재_질문과_채점_상태가_없다() {
        // given
        saveOtherMemberInterviews();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(InterviewState.FINISHED)
                .build());
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problemTestSupport.saveProblem())
                        .stage(SystemDesignStage.DEEP_DIVE)
                        .build());
        for (int order = 1; order <= 6; order++) {
            SystemDesignQuestion question = saveQuestion(systemDesignInterview, order);
            if (order < 6) {
                answerRepository.save(SystemDesignAnswerFixtureBuilder.builder().question(question).build());
            }
        }
        stateStore.markPending(interview.getId(), 6);

        // when
        SystemDesignInterviewResponse response = systemDesignInterviewService.findInterview(interview.getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(response.interviewState()).isEqualTo(InterviewState.FINISHED),
                () -> assertThat(response.curQuestionId()).isNull(),
                () -> assertThat(response.curQuestion()).isNull(),
                () -> assertThat(response.curAnswerProceedState()).isNull(),
                () -> assertThat(response.curQuestionCount()).isEqualTo(5)
        );
    }

    // MySQLDatabaseCleaner가 매번 TRUNCATE해 테이블마다 id가 1부터 다시 시작하면 interview와 system_design_interview의 id,
    // 질문 PK와 순번이 우연히 같아진다. 다른 회원의 면접 기록(시스템 설계 면접 1개와 그 질문 1개, 일반 면접 1개)을 먼저 두어
    // id를 서로 어긋나게 한다. 문제는 INACTIVE라 start가 고르지 않는다.
    private void saveOtherMemberInterviews() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = saveProblem(SystemDesignProblemState.INACTIVE, VALID_STAGES, 1);
        Interview otherInterview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(otherMember)
                .build());
        SystemDesignInterview otherSystemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(otherInterview)
                        .problem(retiredProblem)
                        .stage(SystemDesignStage.HIGH_LEVEL)
                        .build());
        saveQuestion(otherSystemDesignInterview, 1);
        // 테스트가 기본값(OPERATING_SYSTEM, 순번 1)으로 루트 질문을 또 저장해도 (카테고리, 순번) 유니크와 겹치지 않게 한다.
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.NETWORK)
                .build());
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(otherMember)
                .rootQuestion(rootQuestion)
                .build());
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

    private SystemDesignQuestion saveQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        SystemDesignProblemQuestion problemQuestion = problemQuestionRepository.findByProblemAndQuestionOrder(
                systemDesignInterview.getProblem(), questionOrder).orElseThrow();
        return questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .problemQuestion(problemQuestion)
                .content(questionOrder + "번 질문이에요.")
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
