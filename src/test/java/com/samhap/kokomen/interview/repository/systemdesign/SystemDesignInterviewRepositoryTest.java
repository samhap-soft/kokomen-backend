package com.samhap.kokomen.interview.repository.systemdesign;

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
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignEvaluationResultFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationResult;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemDesignInterviewRepositoryTest extends BaseTest {

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
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignEvaluationPointRepository evaluationPointRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 시스템_설계_면접을_interview와_함께_저장하고_조회한다() {
        // given
        Interview interview = saveInterview();
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview saved = systemDesignInterviewRepository.save(SystemDesignInterview.start(interview,
                problem));

        // when
        Optional<SystemDesignInterview> found = systemDesignInterviewRepository.findByInterviewId(interview.getId());
        Optional<SystemDesignInterview> notFound = systemDesignInterviewRepository.findByInterviewId(
                interview.getId() + 1);

        // then
        assertAll(
                () -> assertThat(found).isPresent(),
                () -> assertThat(found.get().getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.get().getInterview().getId()).isEqualTo(interview.getId()),
                () -> assertThat(found.get().getInterview().getInterviewType()).isEqualTo(InterviewType.SYSTEM_DESIGN),
                () -> assertThat(found.get().getInterview().isInProgress()).isTrue(),
                () -> assertThat(found.get().getProblem().getId()).isEqualTo(problem.getId()),
                () -> assertThat(found.get().getProblem().getTitle()).isEqualTo(SystemDesignProblemTestSupport.TITLE),
                () -> assertThat(found.get().getStage()).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(found.get().getCurrentDiagram())
                        .isEqualTo(SystemDesignDiagramFixture.urlShortenerStarter()),
                () -> assertThat(found.get().getReportScore()).isNull(),
                () -> assertThat(found.get().getCreatedAt()).isNotNull(),
                () -> assertThat(notFound).isEmpty()
        );
    }

    @Test
    void 현재_다이어그램과_답변_다이어그램이_왕복한다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.HIGH_LEVEL);
        SystemDesignQuestion question = saveQuestion(systemDesignInterview, 1);
        SystemDesignDiagram diagram = SystemDesignDiagramFixture.withCache();
        systemDesignInterview.recordAnswer(diagram, null);
        systemDesignInterviewRepository.save(systemDesignInterview);
        SystemDesignAnswer savedAnswer = answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                .question(question)
                .diagram(diagram)
                .build());

        // when
        SystemDesignInterview foundInterview = systemDesignInterviewRepository.findById(systemDesignInterview.getId())
                .orElseThrow();
        SystemDesignAnswer foundAnswer = answerRepository.findById(savedAnswer.getId()).orElseThrow();

        // then
        assertAll(
                () -> assertThat(foundInterview.getCurrentDiagram()).isEqualTo(diagram),
                () -> assertThat(foundAnswer.getDiagram()).isEqualTo(diagram),
                () -> assertThat(foundAnswer.getDiagram().toJson()).isEqualTo(diagram.toJson()),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT JSON_LENGTH(current_diagram, '$.blocks') FROM system_design_interview WHERE id = ?",
                        Integer.class, systemDesignInterview.getId())).isEqualTo(5),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT JSON_LENGTH(diagram, '$.connections') FROM system_design_answer WHERE id = ?",
                        Integer.class, savedAnswer.getId())).isEqualTo(4)
        );
    }

    @Test
    void 단계와_리포트_점수를_바꾸면_저장된다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        systemDesignInterview.completeRequirements();
        systemDesignInterview.recordAnswer(SystemDesignDiagram.empty(), SystemDesignStage.DEEP_DIVE);
        systemDesignInterview.finish(85);

        // when
        systemDesignInterviewRepository.save(systemDesignInterview);
        SystemDesignInterview found = systemDesignInterviewRepository.findById(systemDesignInterview.getId())
                .orElseThrow();

        // then
        assertAll(
                () -> assertThat(found.getStage()).isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(found.getCurrentDiagram()).isEqualTo(SystemDesignDiagram.empty()),
                () -> assertThat(found.getReportScore()).isEqualTo(85)
        );
    }

    @Test
    void 리포트_점수_CHECK_제약이_0에서_100_밖의_값을_막는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.DEEP_DIVE);

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE system_design_interview SET report_score = ? WHERE id = ?",
                101, systemDesignInterview.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_system_design_interview_report_score");
    }

    @Test
    void 같은_면접으로_시스템_설계_면접을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        Interview interview = saveInterview();
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        systemDesignInterviewRepository.save(SystemDesignInterview.start(interview, problem));
        SystemDesignInterview duplicate = SystemDesignInterview.start(interview, problem);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_interview_interview_id");
    }

    @Test
    void 역질문의_물은_요구사항_code_목록이_JSON으로_왕복한다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview otherInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        SystemDesignRequirementQuestion first = requirementQuestionRepository.save(
                SystemDesignRequirementQuestionFixtureBuilder.builder()
                        .systemDesignInterview(systemDesignInterview)
                        .questionOrder(1)
                        .content("하루 생성량과 조회 비율이 어떻게 되나요?")
                        .answer("하루 평균 10만 개가 만들어지고, 조회는 생성의 100배쯤이에요.")
                        .askedRequirementCodes(List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"))
                        .build());
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(otherInterview)
                .build());
        SystemDesignRequirementQuestion second = requirementQuestionRepository.save(
                SystemDesignRequirementQuestionFixtureBuilder.builder()
                        .systemDesignInterview(systemDesignInterview)
                        .questionOrder(2)
                        .content("사용자 인증도 해야 하나요?")
                        .answer("등록되지 않은 내용이에요. 가정하고 진행하세요.")
                        .askedRequirementCodes(List.of())
                        .build());

        // when
        List<SystemDesignRequirementQuestion> found = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        long count = requirementQuestionRepository.countBySystemDesignInterview(systemDesignInterview);

        // then
        assertAll(
                () -> assertThat(found)
                        .extracting(SystemDesignRequirementQuestion::getId, SystemDesignRequirementQuestion::getContent,
                                SystemDesignRequirementQuestion::getAnswer,
                                SystemDesignRequirementQuestion::getAskedRequirementCodes)
                        .containsExactly(
                                tuple(first.getId(), "하루 생성량과 조회 비율이 어떻게 되나요?",
                                        "하루 평균 10만 개가 만들어지고, 조회는 생성의 100배쯤이에요.",
                                        List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO")),
                                tuple(second.getId(), "사용자 인증도 해야 하나요?", "등록되지 않은 내용이에요. 가정하고 진행하세요.",
                                        List.of())),
                () -> assertThat(count).isEqualTo(2),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT JSON_LENGTH(asked_requirement_codes) FROM system_design_requirement_question "
                                + "WHERE id = ?",
                        Integer.class, second.getId())).isZero()
        );
    }

    @Test
    void 면접의_역질문을_순번_순서로_찾는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview otherInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        SystemDesignRequirementQuestion second = saveRequirementQuestion(systemDesignInterview, 2);
        saveRequirementQuestion(otherInterview, 1);
        SystemDesignRequirementQuestion first = saveRequirementQuestion(systemDesignInterview, 1);

        // when
        List<SystemDesignRequirementQuestion> found = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);

        // then
        assertThat(found)
                .extracting(SystemDesignRequirementQuestion::getId, SystemDesignRequirementQuestion::getQuestionOrder)
                .containsExactly(tuple(first.getId(), 1), tuple(second.getId(), 2));
    }

    @Test
    void 같은_면접에_같은_순번의_역질문을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1);
        SystemDesignRequirementQuestion duplicate = SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(1)
                .build();

        // when & then
        assertThatThrownBy(() -> requirementQuestionRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_requirement_question_order");
    }

    @Test
    void 역질문_순번_CHECK_제약이_1에서_5_밖의_값을_막는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.REQUIREMENTS);
        SystemDesignRequirementQuestion question = saveRequirementQuestion(systemDesignInterview, 5);
        String updateOrder = "UPDATE system_design_requirement_question SET question_order = ? WHERE id = ?";

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> jdbcTemplate.update(updateOrder, 6, question.getId()))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("chk_system_design_requirement_question_order"),
                () -> assertThatThrownBy(() -> jdbcTemplate.update(updateOrder, 0, question.getId()))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("chk_system_design_requirement_question_order")
        );
    }

    @Test
    void 면접과_순번으로_질문을_찾는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview otherInterview = saveSystemDesignInterview(SystemDesignStage.HIGH_LEVEL);
        SystemDesignQuestion second = saveQuestion(systemDesignInterview, 2);
        saveQuestion(otherInterview, 1);
        SystemDesignQuestion first = saveQuestion(systemDesignInterview, 1);

        // when
        Optional<SystemDesignQuestion> foundSecond = questionRepository.findBySystemDesignInterviewAndQuestionOrder(
                systemDesignInterview, 2);
        Optional<SystemDesignQuestion> foundThird = questionRepository.findBySystemDesignInterviewAndQuestionOrder(
                systemDesignInterview, 3);
        List<SystemDesignQuestion> questions = questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(
                systemDesignInterview);

        // then
        assertAll(
                () -> assertThat(foundSecond).isPresent(),
                () -> assertThat(foundSecond.get().getId()).isEqualTo(second.getId()),
                () -> assertThat(foundSecond.get().getStage()).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(foundSecond.get().getQuestionOrder()).isEqualTo(2),
                () -> assertThat(foundSecond.get().getContent()).isEqualTo("2번 질문이에요."),
                () -> assertThat(foundSecond.get().getProblemQuestion().getId())
                        .isEqualTo(second.getProblemQuestion().getId()),
                () -> assertThat(foundSecond.get().getCreatedAt()).isNotNull(),
                () -> assertThat(foundThird).isEmpty(),
                () -> assertThat(questions).extracting(SystemDesignQuestion::getId)
                        .containsExactly(first.getId(), second.getId())
        );
    }

    @Test
    void 같은_순번의_질문을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.HIGH_LEVEL);
        SystemDesignQuestion question = saveQuestion(systemDesignInterview, 1);
        SystemDesignQuestion duplicate = SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .problemQuestion(question.getProblemQuestion())
                .build();

        // when & then
        assertThatThrownBy(() -> questionRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_question_order");
    }

    @Test
    void 질문으로_답변을_찾고_있는지_확인한다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.HIGH_LEVEL);
        SystemDesignQuestion first = saveQuestion(systemDesignInterview, 1);
        SystemDesignQuestion second = saveQuestion(systemDesignInterview, 2);
        SystemDesignQuestion unanswered = saveQuestion(systemDesignInterview, 3);
        SystemDesignAnswer firstAnswer = answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                .question(first)
                .content("API 서버를 여러 대 두고 앞에 로드 밸런서를 둡니다.")
                .answerRank(AnswerRank.A)
                .feedback("요청 흐름을 순서대로 잘 설명했어요.")
                .build());
        SystemDesignAnswer secondAnswer = answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                .question(second)
                .answerRank(AnswerRank.F)
                .build());

        // when
        List<SystemDesignAnswer> answers = answerRepository.findByQuestionIn(List.of(first, second, unanswered));
        Optional<SystemDesignAnswer> foundFirst = answerRepository.findByQuestion(first);

        // then
        assertAll(
                () -> assertThat(answers).extracting(SystemDesignAnswer::getId)
                        .containsExactlyInAnyOrder(firstAnswer.getId(), secondAnswer.getId()),
                () -> assertThat(foundFirst).isPresent(),
                () -> assertThat(foundFirst.get().getContent()).isEqualTo("API 서버를 여러 대 두고 앞에 로드 밸런서를 둡니다."),
                () -> assertThat(foundFirst.get().getAnswerRank()).isEqualTo(AnswerRank.A),
                () -> assertThat(foundFirst.get().getFeedback()).isEqualTo("요청 흐름을 순서대로 잘 설명했어요."),
                () -> assertThat(foundFirst.get().getDiagram()).isEqualTo(SystemDesignDiagram.empty()),
                () -> assertThat(foundFirst.get().getCreatedAt()).isNotNull(),
                () -> assertThat(answerRepository.findByQuestion(unanswered)).isEmpty(),
                () -> assertThat(answerRepository.existsByQuestion(first)).isTrue(),
                () -> assertThat(answerRepository.existsByQuestion(unanswered)).isFalse()
        );
    }

    @Test
    void 같은_질문에_답변을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.HIGH_LEVEL);
        SystemDesignQuestion question = saveQuestion(systemDesignInterview, 1);
        answerRepository.save(SystemDesignAnswerFixtureBuilder.builder().question(question).build());
        SystemDesignAnswer duplicate = SystemDesignAnswerFixtureBuilder.builder().question(question).build();

        // when & then
        assertThatThrownBy(() -> answerRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_answer_question_id");
    }

    @Test
    void 답변_등급_컬럼은_다른_enum_컬럼처럼_VARCHAR_20이다() {
        // when
        String answerRankType = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_design_answer' AND COLUMN_NAME = 'answer_rank'
                """, String.class);

        // then
        assertThat(answerRankType).isEqualTo("varchar(20)");
    }

    @Test
    void 평가_포인트_결과를_면접으로_찾는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.DEEP_DIVE);
        SystemDesignInterview otherInterview = saveSystemDesignInterview(SystemDesignStage.DEEP_DIVE);
        List<SystemDesignEvaluationPoint> points = evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(
                systemDesignInterview.getProblem());
        SystemDesignEvaluationResult met = evaluationResultRepository.save(evaluationResult(systemDesignInterview,
                points.get(0), true));
        SystemDesignEvaluationResult notMet = evaluationResultRepository.save(evaluationResult(systemDesignInterview,
                points.get(1), false));
        evaluationResultRepository.save(evaluationResult(otherInterview, points.get(0), true));

        // when
        List<SystemDesignEvaluationResult> results = evaluationResultRepository.findBySystemDesignInterview(
                systemDesignInterview);

        // then
        assertThat(results)
                .extracting(SystemDesignEvaluationResult::getId, result -> result.getEvaluationPoint().getId(),
                        SystemDesignEvaluationResult::isMet)
                .containsExactlyInAnyOrder(
                        tuple(met.getId(), points.get(0).getId(), true),
                        tuple(notMet.getId(), points.get(1).getId(), false));
    }

    @Test
    void 평가_포인트_결과를_같은_포인트에_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(SystemDesignStage.DEEP_DIVE);
        SystemDesignEvaluationPoint point = evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(
                systemDesignInterview.getProblem()).get(0);
        evaluationResultRepository.save(evaluationResult(systemDesignInterview, point, true));
        SystemDesignEvaluationResult duplicate = evaluationResult(systemDesignInterview, point, false);

        // when & then
        assertThatThrownBy(() -> evaluationResultRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_evaluation_result_point");
    }

    private Interview saveInterview() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        return interviewRepository.save(InterviewFixtureBuilder.systemDesign().member(member).build());
    }

    private SystemDesignInterview saveSystemDesignInterview(SystemDesignStage stage) {
        return systemDesignInterviewRepository.save(SystemDesignInterviewFixtureBuilder.builder()
                .interview(saveInterview())
                .problem(problemTestSupport.saveProblem())
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

    private SystemDesignRequirementQuestion saveRequirementQuestion(SystemDesignInterview systemDesignInterview,
                                                                    int questionOrder) {
        return requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .build());
    }

    private static SystemDesignEvaluationResult evaluationResult(SystemDesignInterview systemDesignInterview,
                                                                 SystemDesignEvaluationPoint point, boolean met) {
        return SystemDesignEvaluationResultFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .evaluationPoint(point)
                .met(met)
                .build();
    }
}
