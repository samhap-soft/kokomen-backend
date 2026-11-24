package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static com.samhap.kokomen.answer.domain.AnswerRank.C;
import static com.samhap.kokomen.answer.domain.AnswerRank.D;
import static com.samhap.kokomen.answer.domain.AnswerRank.F;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage.DEEP_DIVE;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage.HIGH_LEVEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignEvaluationPointFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignEvaluationResultFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignHiddenRequirementFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignReportScore;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationResultRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignEvaluationPointResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignFeedbackResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignReportResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignTopicResponse;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignReportServiceTest extends BaseTest {

    // 뒤섞어 저장하는 면접의 답변 등급(질문 순번 순서)
    private static final List<AnswerRank> SHUFFLED_RANKS = List.of(A, B, C, D, F, B);

    @Autowired
    private SystemDesignReportService reportService;
    @Autowired
    private SystemDesignInterviewTestSupport interviewTestSupport;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    @Autowired
    private SystemDesignEvaluationPointRepository evaluationPointRepository;
    @Autowired
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private SystemDesignAnswerRepository answerRepository;
    @Autowired
    private SystemDesignEvaluationResultRepository evaluationResultRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 끝난_면접의_리포트는_저장된_값을_조립한다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().score(150).build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of(
                List.of("DAILY_CREATION_VOLUME"),
                List.of("READ_WRITE_RATIO", "RETENTION_PERIOD"),
                List.of()));
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();
        Interview interview = systemDesignInterview.getInterview();

        // when
        SystemDesignReportResponse report = reportService.findReport(interview.getId(), member.getId());

        // then
        assertAll(
                () -> assertThat(report.interviewId()).isEqualTo(interview.getId()),
                () -> assertThat(report.topic()).isEqualTo(new SystemDesignTopicResponse(
                        SystemDesignProblemTestSupport.TITLE, SystemDesignProblemTestSupport.DESCRIPTION)),
                () -> assertThat(report.difficulty()).isEqualTo(SystemDesignDifficulty.BASIC),
                () -> assertThat(report.totalScore()).isEqualTo(65),
                () -> assertThat(report.totalFeedback()).isEqualTo(interview.getTotalFeedback()),
                () -> assertThat(report.difficultyComment())
                        .isEqualTo(SystemDesignProblemTestSupport.DIFFICULTY_COMMENT),
                () -> assertThat(report.evaluationPoints())
                        .extracting(SystemDesignEvaluationPointResponse::description,
                                SystemDesignEvaluationPointResponse::isMet)
                        .containsExactly(
                                tuple(urlShortenerPointDescription(0), true),
                                tuple(urlShortenerPointDescription(1), true),
                                tuple(urlShortenerPointDescription(2), false),
                                tuple(urlShortenerPointDescription(3), true),
                                tuple(urlShortenerPointDescription(4), false)),
                () -> assertThat(report.missedRequirements()).containsExactly("단축 키 길이", "클릭 통계"),
                () -> assertThat(report.requirementQuestions())
                        .extracting(SystemDesignRequirementQuestionResponse::question,
                                SystemDesignRequirementQuestionResponse::answer)
                        .containsExactly(
                                tuple("하루 생성량에 대해 알려 주세요.", "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요."),
                                tuple("조회와 생성 비율, 보관 기간에 대해 알려 주세요.",
                                        "단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요. "
                                                + "단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요."),
                                tuple("로그인한 사용자만 단축 URL을 만들 수 있나요?", "등록되지 않은 내용이에요. 가정하고 진행하세요.")),
                () -> assertThat(report.feedbacks())
                        .extracting(SystemDesignFeedbackResponse::questionId, SystemDesignFeedbackResponse::stage,
                                SystemDesignFeedbackResponse::answerRank)
                        .containsExactly(
                                tuple(1, HIGH_LEVEL, B),
                                tuple(2, HIGH_LEVEL, B),
                                tuple(3, HIGH_LEVEL, C),
                                tuple(4, DEEP_DIVE, A),
                                tuple(5, DEEP_DIVE, B),
                                tuple(6, DEEP_DIVE, C)),
                () -> assertThat(report.feedbacks().get(0).diagram())
                        .isEqualTo(SystemDesignDiagramResponse.from(SystemDesignDiagramFixture.urlShortenerStarter())),
                () -> assertThat(report.feedbacks().get(5).diagram())
                        .isEqualTo(SystemDesignDiagramResponse.from(SystemDesignDiagramFixture.withCache())),
                () -> assertThat(report.finalDiagram())
                        .isEqualTo(SystemDesignDiagramResponse.from(SystemDesignDiagramFixture.withCache())),
                () -> assertThat(report.userCurScore()).isEqualTo(150),
                () -> assertThat(report.userPrevScore()).isEqualTo(100)
        );
    }

    // 다른 회원의 면접은 단축 키 길이와 클릭 통계를 물었다. 그 역질문이 섞이면 두 요구사항이 놓친 요구사항에서 빠진다.
    @Test
    void 묻지_않은_요구사항만_놓친_요구사항으로_보여_준다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of(
                List.of("RETENTION_PERIOD"),
                List.of("DAILY_CREATION_VOLUME", "RETENTION_PERIOD"),
                List.of()));
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertThat(report.missedRequirements()).containsExactly("조회와 생성 비율", "단축 키 길이", "클릭 통계");
    }

    @Test
    void 역질문을_하지_않았으면_모든_요구사항이_놓친_요구사항이다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of());
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(report.requirementQuestions()).isEmpty(),
                () -> assertThat(report.missedRequirements())
                        .containsExactly("하루 생성량", "조회와 생성 비율", "보관 기간", "단축 키 길이", "클릭 통계")
        );
    }

    @Test
    void 모든_요구사항을_물었으면_놓친_요구사항이_없다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of(
                List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"),
                List.of("RETENTION_PERIOD", "SHORT_KEY_LENGTH", "CLICK_STATISTICS")));
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(report.requirementQuestions()).hasSize(2),
                () -> assertThat(report.missedRequirements()).isEmpty()
        );
    }

    // 운영에서는 여러 면접이 같은 문제(같은 평가 포인트 행)를 푼다. 판정과 역질문은 그 면접의 것만 읽어야 한다.
    @Test
    void 같은_문제를_푼_다른_회원의_판정과_역질문은_리포트에_섞이지_않는다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member,
                List.of(List.of("DAILY_CREATION_VOLUME")));
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("같은 문제를 푼 회원").build());
        SystemDesignInterview otherInterview = interviewTestSupport.saveFinishedInterview(otherMember,
                systemDesignInterview.getProblem(),
                List.of(List.of("READ_WRITE_RATIO", "RETENTION_PERIOD", "SHORT_KEY_LENGTH", "CLICK_STATISTICS")),
                List.of(A, A, A, A, A, A), List.of(false, false, true, false, true));

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());
        SystemDesignReportResponse otherReport = reportService.findReport(otherInterview.getInterview().getId(),
                otherMember.getId());

        // then
        assertAll(
                () -> assertThat(report.evaluationPoints())
                        .extracting(SystemDesignEvaluationPointResponse::isMet)
                        .containsExactly(true, true, false, true, false),
                () -> assertThat(report.missedRequirements())
                        .containsExactly("조회와 생성 비율", "보관 기간", "단축 키 길이", "클릭 통계"),
                () -> assertThat(report.feedbacks())
                        .extracting(SystemDesignFeedbackResponse::answerRank)
                        .containsExactly(B, B, C, A, B, C),
                () -> assertThat(otherReport.evaluationPoints())
                        .extracting(SystemDesignEvaluationPointResponse::isMet)
                        .containsExactly(false, false, true, false, true),
                () -> assertThat(otherReport.missedRequirements()).containsExactly("하루 생성량"),
                () -> assertThat(otherReport.feedbacks())
                        .extracting(SystemDesignFeedbackResponse::answerRank)
                        .containsExactly(A, A, A, A, A, A)
        );
    }

    @Test
    void 평가_포인트와_피드백은_정해진_순서다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveShuffledFinishedInterview(member);
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(report.evaluationPoints())
                        .extracting(SystemDesignEvaluationPointResponse::description,
                                SystemDesignEvaluationPointResponse::isMet)
                        .containsExactly(
                                tuple("1번 평가 포인트", true),
                                tuple("2번 평가 포인트", false),
                                tuple("3번 평가 포인트", false),
                                tuple("4번 평가 포인트", true),
                                tuple("5번 평가 포인트", true)),
                () -> assertThat(report.feedbacks())
                        .extracting(SystemDesignFeedbackResponse::questionId, SystemDesignFeedbackResponse::stage,
                                SystemDesignFeedbackResponse::question, SystemDesignFeedbackResponse::answer,
                                SystemDesignFeedbackResponse::answerRank, SystemDesignFeedbackResponse::answerFeedback)
                        .containsExactly(
                                tuple(1, HIGH_LEVEL, "1번 질문이에요.", "1번 답변이에요.", A, "1번 피드백이에요."),
                                tuple(2, HIGH_LEVEL, "2번 질문이에요.", "2번 답변이에요.", B, "2번 피드백이에요."),
                                tuple(3, HIGH_LEVEL, "3번 질문이에요.", "3번 답변이에요.", C, "3번 피드백이에요."),
                                tuple(4, DEEP_DIVE, "4번 질문이에요.", "4번 답변이에요.", D, "4번 피드백이에요."),
                                tuple(5, DEEP_DIVE, "5번 질문이에요.", "5번 답변이에요.", F, "5번 피드백이에요."),
                                tuple(6, DEEP_DIVE, "6번 질문이에요.", "6번 답변이에요.", B, "6번 피드백이에요."))
        );
    }

    @Test
    void 역질문은_물은_순서이고_놓친_요구사항은_문제의_순서다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveShuffledFinishedInterview(member);
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(report.requirementQuestions())
                        .extracting(SystemDesignRequirementQuestionResponse::question,
                                SystemDesignRequirementQuestionResponse::answer)
                        .containsExactly(
                                tuple("1번 역질문이에요.", "1번 역질문의 답이에요."),
                                tuple("2번 역질문이에요.", "2번 역질문의 답이에요."),
                                tuple("3번 역질문이에요.", "3번 역질문의 답이에요.")),
                () -> assertThat(report.missedRequirements()).containsExactly("2번 요구사항", "4번 요구사항", "5번 요구사항")
        );
    }

    @Test
    void 이전_점수는_지금_점수에서_이번_면접_점수를_뺀_값이다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().score(100).build());
        // 등급 점수 합 20 + 10 + 0 + 0 + 0 + 0 = 30
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member,
                List.of(List.of("DAILY_CREATION_VOLUME")), List.of(A, B, C, C, C, C));
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertAll(
                () -> assertThat(report.userCurScore()).isEqualTo(100),
                () -> assertThat(report.userPrevScore()).isEqualTo(70)
        );
    }

    // 면접(문제 포함)·평가 포인트·판정·숨은 요구사항·역질문·질문·답변을 한 번씩 읽고 면접자를 한 번 읽는다. 목록 원소마다 다시 읽으면
    // (지연 로딩, 파생 쿼리, findById 모두) 수가 늘어난다.
    @Test
    void 리포트는_시스템_설계_테이블을_한_번씩만_읽는다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of(
                List.of("DAILY_CREATION_VOLUME"),
                List.of("READ_WRITE_RATIO", "RETENTION_PERIOD"),
                List.of()));
        Runnable findReport = () -> reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // when
        long systemDesignSelectCount = countSystemDesignSelects(findReport);
        long statementCount = countStatements(findReport);

        // then
        assertAll(
                () -> assertThat(systemDesignSelectCount).isEqualTo(7),
                () -> assertThat(statementCount).isEqualTo(8)
        );
    }

    // 끝난 면접은 평가 포인트마다 판정이 있다. 빠진 판정을 충족하지 않음으로 지어내지 않고 실패한다.
    @Test
    void 평가_포인트_판정이_빠졌으면_리포트를_만들지_않는다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of());
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();
        evaluationResultRepository.delete(
                evaluationResultRepository.findBySystemDesignInterview(systemDesignInterview).get(0));

        // when & then
        assertThatThrownBy(() -> reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평가 포인트 판정이 없습니다");
    }

    // 면접 조회와 같이 답이 저장된 질문만 보여 준다.
    @Test
    void 끝난_면접의_마지막_질문에_답이_없으면_답이_있는_질문만_피드백으로_준다() {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of());
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();
        SystemDesignQuestion lastQuestion = questionRepository
                .findBySystemDesignInterviewAndQuestionOrder(systemDesignInterview, 6).orElseThrow();
        answerRepository.delete(answerRepository.findByQuestion(lastQuestion).orElseThrow());

        // when
        SystemDesignReportResponse report = reportService.findReport(systemDesignInterview.getInterview().getId(),
                member.getId());

        // then
        assertThat(report.feedbacks())
                .extracting(SystemDesignFeedbackResponse::questionId)
                .containsExactly(1, 2, 3, 4, 5);
    }

    private static String urlShortenerPointDescription(int index) {
        List<EvaluationPoint> points = SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS;
        return points.get(index).description();
    }

    /**
     * 표시 순서와 다른 순서로 저장해 id 순서가 표시 순서와 어긋난 끝난 면접. 문제의 숨은 요구사항·평가 포인트는 3·1·5·2·4번, 역질문은
     * 2·3·1번, 질문은 4·1·6·2·5·3번, 답변은 6·3·1·5·2·4번, 판정은 2·5·1·4·3번 순서로 저장한다.
     * 평가 포인트는 1·4·5번을 충족했고, 역질문 1번은 3번 요구사항을, 3번은 1번 요구사항을 물었다(2번은 아무것도 묻지 않았다).
     */
    private SystemDesignInterview saveShuffledFinishedInterview(Member member) {
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        Map<Integer, SystemDesignEvaluationPoint> pointsByOrder = new HashMap<>();
        for (int order : List.of(3, 1, 5, 2, 4)) {
            hiddenRequirementRepository.save(SystemDesignHiddenRequirementFixtureBuilder.builder()
                    .problem(problem)
                    .code("REQUIREMENT_" + order)
                    .name(order + "번 요구사항")
                    .displayOrder(order)
                    .build());
            pointsByOrder.put(order, evaluationPointRepository.save(SystemDesignEvaluationPointFixtureBuilder.builder()
                    .problem(problem)
                    .code("POINT_" + order)
                    .description(order + "번 평가 포인트")
                    .displayOrder(order)
                    .build()));
        }
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(InterviewState.FINISHED)
                .totalScore(SHUFFLED_RANKS.stream().mapToInt(AnswerRank::getScore).sum())
                .totalFeedback("고르게 설계했어요.")
                .finishedAt(LocalDateTime.now())
                .build());
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problem)
                        .stage(SystemDesignStage.DEEP_DIVE)
                        .reportScore(SystemDesignReportScore.calculate(3, 5, SHUFFLED_RANKS))
                        .build());
        Map<Integer, List<String>> askedCodesByOrder = Map.of(
                1, List.of("REQUIREMENT_3"), 2, List.of(), 3, List.of("REQUIREMENT_1"));
        for (int order : List.of(2, 3, 1)) {
            requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                    .systemDesignInterview(systemDesignInterview)
                    .questionOrder(order)
                    .content(order + "번 역질문이에요.")
                    .answer(order + "번 역질문의 답이에요.")
                    .askedRequirementCodes(askedCodesByOrder.get(order))
                    .build());
        }
        Map<Integer, SystemDesignQuestion> questionsByOrder = new HashMap<>();
        for (int order : List.of(4, 1, 6, 2, 5, 3)) {
            SystemDesignProblemQuestion problemQuestion = problemQuestionRepository.save(
                    SystemDesignProblemQuestionFixtureBuilder.builder()
                            .problem(problem)
                            .questionOrder(order)
                            .stage(SystemDesignStage.ofQuestionOrder(order))
                            .build());
            questionsByOrder.put(order, questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                    .systemDesignInterview(systemDesignInterview)
                    .problemQuestion(problemQuestion)
                    .content(order + "번 질문이에요.")
                    .build()));
        }
        for (int order : List.of(6, 3, 1, 5, 2, 4)) {
            answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                    .question(questionsByOrder.get(order))
                    .content(order + "번 답변이에요.")
                    .answerRank(SHUFFLED_RANKS.get(order - 1))
                    .feedback(order + "번 피드백이에요.")
                    .build());
        }
        for (int order : List.of(2, 5, 1, 4, 3)) {
            evaluationResultRepository.save(SystemDesignEvaluationResultFixtureBuilder.builder()
                    .systemDesignInterview(systemDesignInterview)
                    .evaluationPoint(pointsByOrder.get(order))
                    .met(order == 1 || order == 4 || order == 5)
                    .build());
        }
        return systemDesignInterview;
    }

    // 시스템 설계 테이블을 읽은 SELECT 수. JPQL은 쿼리 문자열로, 파생 쿼리는 SQL 문자열로, 연관 지연 로딩은 엔티티 fetch로 센다.
    // 통계는 이 테스트 동안에만 켜고 끈다. 운영·테스트 설정은 바꾸지 않는다.
    private long countSystemDesignSelects(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            action.run();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        long queryCount = Arrays.stream(statistics.getQueries())
                .filter(query -> query.contains("SystemDesign") || query.contains("system_design"))
                .mapToLong(query -> statistics.getQueryStatistics(query).getExecutionCount())
                .sum();
        long fetchCount = Arrays.stream(statistics.getEntityNames())
                .filter(entityName -> entityName.contains("SystemDesign"))
                .mapToLong(entityName -> statistics.getEntityStatistics(entityName).getFetchCount())
                .sum();
        statistics.clear();
        return queryCount + fetchCount;
    }

    // action이 준비한 JDBC 문장 수. findById(em.find)처럼 쿼리 통계에 남지 않는 조회도 센다. 통계는 SessionFactory 전체 값이라 그사이
    // 다른 스레드(5분마다 도는 스케줄러 등)가 낸 문장이 섞일 수 있어 두 번 재고 작은 값을 쓴다. 섞이면 수가 늘기만 한다.
    private long countStatements(Runnable action) {
        return Math.min(countStatementsOnce(action), countStatementsOnce(action));
    }

    private long countStatementsOnce(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            action.run();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        long statementCount = statistics.getPrepareStatementCount();
        statistics.clear();
        return statementCount;
    }
}
