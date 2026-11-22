package com.samhap.kokomen.interview.repository.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignEvaluationPointFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignHiddenRequirementFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignHiddenRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemDesignProblemRepositoryTest extends BaseTest {

    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    @Autowired
    private SystemDesignEvaluationPointRepository evaluationPointRepository;
    @Autowired
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 난이도와_상태로_문제를_찾는다() {
        // given
        SystemDesignProblem first = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        SystemDesignProblem second = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());

        // when
        List<SystemDesignProblem> problems = problemRepository.findByDifficultyAndState(SystemDesignDifficulty.BASIC,
                SystemDesignProblemState.ACTIVE);

        // then
        assertThat(problems).extracting(SystemDesignProblem::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void INACTIVE_문제는_찾지_않는다() {
        // given
        SystemDesignProblem active = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.ACTIVE)
                .build());
        SystemDesignProblem inactive = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());

        // when
        List<SystemDesignProblem> activeProblems = problemRepository.findByDifficultyAndState(
                SystemDesignDifficulty.BASIC, SystemDesignProblemState.ACTIVE);
        List<SystemDesignProblem> inactiveProblems = problemRepository.findByDifficultyAndState(
                SystemDesignDifficulty.BASIC, SystemDesignProblemState.INACTIVE);

        // then
        assertAll(
                () -> assertThat(activeProblems).extracting(SystemDesignProblem::getId).containsExactly(active.getId()),
                () -> assertThat(inactiveProblems).extracting(SystemDesignProblem::getId)
                        .containsExactly(inactive.getId())
        );
    }

    @Test
    void 시작_다이어그램_JSON이_왕복한다() {
        // given
        SystemDesignDiagram starterDiagram = SystemDesignDiagramFixture.urlShortenerStarter();
        SystemDesignProblem saved = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .code("URL_SHORTENER")
                .difficulty(SystemDesignDifficulty.BASIC)
                .state(SystemDesignProblemState.ACTIVE)
                .title("URL 단축기를 설계해 보세요")
                .description("짧은 링크를 만들어 주는 서비스예요.")
                .difficultyComment("리다이렉트 경로를 빠르게 하는 방법을 설명하면 좋아요.")
                .starterDiagram(starterDiagram)
                .build());

        // when
        SystemDesignProblem found = problemRepository.findById(saved.getId()).orElseThrow();

        // then
        assertAll(
                () -> assertThat(found.getCode()).isEqualTo("URL_SHORTENER"),
                () -> assertThat(found.getDifficulty()).isEqualTo(SystemDesignDifficulty.BASIC),
                () -> assertThat(found.getState()).isEqualTo(SystemDesignProblemState.ACTIVE),
                () -> assertThat(found.getTitle()).isEqualTo("URL 단축기를 설계해 보세요"),
                () -> assertThat(found.getDescription()).isEqualTo("짧은 링크를 만들어 주는 서비스예요."),
                () -> assertThat(found.getDifficultyComment()).isEqualTo("리다이렉트 경로를 빠르게 하는 방법을 설명하면 좋아요."),
                () -> assertThat(found.getCreatedAt()).isNotNull(),
                () -> assertThat(found.getStarterDiagram()).isEqualTo(starterDiagram),
                () -> assertThat(found.startingDiagram()).isEqualTo(starterDiagram)
        );
    }

    @Test
    void 시작_다이어그램이_없으면_빈_다이어그램으로_시작한다() {
        // given
        SystemDesignProblem saved = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .starterDiagram(null)
                .build());

        // when
        SystemDesignProblem found = problemRepository.findById(saved.getId()).orElseThrow();

        // then
        assertAll(
                () -> assertThat(found.getStarterDiagram()).isNull(),
                () -> assertThat(found.startingDiagram()).isEqualTo(SystemDesignDiagram.empty()),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM system_design_problem WHERE id = ? AND starter_diagram IS NULL",
                        Integer.class, saved.getId())).isEqualTo(1)
        );
    }

    @Test
    void 요구사항과_평가_포인트를_표시_순서대로_찾는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        SystemDesignProblem otherProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        hiddenRequirementRepository.saveAll(List.of(
                requirement(problem, 3, "RETENTION_PERIOD", "보관 기간", "만든 날부터 5년 동안 열려야 해요."),
                requirement(problem, 1, "DAILY_CREATION_VOLUME", "하루 생성량", "하루 평균 10만 개 정도예요."),
                requirement(otherProblem, 1, "DAILY_CREATION_VOLUME", "하루 생성량", "하루 평균 10만 개 정도예요."),
                requirement(problem, 2, "READ_WRITE_RATIO", "조회와 생성 비율", "조회는 생성의 100배쯤이에요.")));
        evaluationPointRepository.saveAll(List.of(
                point(problem, 2, "KEY_GENERATION_COLLISION", "키 충돌을 막는 방법을 설명했다"),
                point(otherProblem, 1, "READ_PATH_CACHE", "리다이렉트 경로에 캐시를 두었다"),
                point(problem, 1, "READ_PATH_CACHE", "리다이렉트 경로에 캐시를 두었다")));

        // when
        List<SystemDesignHiddenRequirement> requirements =
                hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(problem);
        List<SystemDesignEvaluationPoint> points =
                evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem);

        // then
        assertAll(
                () -> assertThat(requirements)
                        .extracting(SystemDesignHiddenRequirement::getDisplayOrder,
                                SystemDesignHiddenRequirement::getCode, SystemDesignHiddenRequirement::getName,
                                SystemDesignHiddenRequirement::getAnswer)
                        .containsExactly(
                                tuple(1, "DAILY_CREATION_VOLUME", "하루 생성량", "하루 평균 10만 개 정도예요."),
                                tuple(2, "READ_WRITE_RATIO", "조회와 생성 비율", "조회는 생성의 100배쯤이에요."),
                                tuple(3, "RETENTION_PERIOD", "보관 기간", "만든 날부터 5년 동안 열려야 해요.")),
                () -> assertThat(points)
                        .extracting(SystemDesignEvaluationPoint::getDisplayOrder, SystemDesignEvaluationPoint::getCode,
                                SystemDesignEvaluationPoint::getDescription)
                        .containsExactly(
                                tuple(1, "READ_PATH_CACHE", "리다이렉트 경로에 캐시를 두었다"),
                                tuple(2, "KEY_GENERATION_COLLISION", "키 충돌을 막는 방법을 설명했다"))
        );
    }

    @Test
    void 기준_질문을_순번으로_찾고_없는_순번은_비어_있다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        SystemDesignProblem otherProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        problemQuestionRepository.saveAll(List.of(
                question(problem, 2, SystemDesignStage.HIGH_LEVEL, "단축 키는 어떤 방식으로 만드나요?"),
                question(problem, 4, SystemDesignStage.DEEP_DIVE, "리다이렉트 요청이 크게 늘면 어디가 병목이 되나요?"),
                question(otherProblem, 1, SystemDesignStage.HIGH_LEVEL, "다른 문제의 첫 질문이에요."),
                question(problem, 1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요.")));

        // when
        List<SystemDesignProblemQuestion> questions = problemQuestionRepository.findByProblemOrderByQuestionOrderAsc(
                problem);
        Optional<SystemDesignProblemQuestion> fourth =
                problemQuestionRepository.findByProblemAndQuestionOrder(problem, 4);
        Optional<SystemDesignProblemQuestion> third =
                problemQuestionRepository.findByProblemAndQuestionOrder(problem, 3);

        // then
        assertAll(
                () -> assertThat(questions)
                        .extracting(SystemDesignProblemQuestion::getQuestionOrder,
                                SystemDesignProblemQuestion::getStage, SystemDesignProblemQuestion::getContent)
                        .containsExactly(
                                tuple(1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요."),
                                tuple(2, SystemDesignStage.HIGH_LEVEL, "단축 키는 어떤 방식으로 만드나요?"),
                                tuple(4, SystemDesignStage.DEEP_DIVE, "리다이렉트 요청이 크게 늘면 어디가 병목이 되나요?")),
                () -> assertThat(fourth).get()
                        .extracting(SystemDesignProblemQuestion::getStage, SystemDesignProblemQuestion::getContent)
                        .containsExactly(SystemDesignStage.DEEP_DIVE, "리다이렉트 요청이 크게 늘면 어디가 병목이 되나요?"),
                () -> assertThat(third).isEmpty()
        );
    }

    @Test
    void 기준_질문의_겨누는_평가_포인트_code가_JSON으로_왕복한다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        SystemDesignProblemQuestion saved = problemQuestionRepository.save(
                SystemDesignProblemQuestionFixtureBuilder.builder()
                        .problem(problem)
                        .targetPointCodes(List.of("READ_PATH_CACHE", "STORAGE_ESTIMATION"))
                        .build());

        // when
        SystemDesignProblemQuestion found = problemQuestionRepository.findById(saved.getId()).orElseThrow();

        // then
        assertAll(
                () -> assertThat(found.getTargetPointCodes()).containsExactly("READ_PATH_CACHE", "STORAGE_ESTIMATION"),
                () -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT JSON_LENGTH(target_point_codes) FROM system_design_problem_question WHERE id = ?",
                        Integer.class, saved.getId())).isEqualTo(2)
        );
    }

    @Test
    void 문제가_다르면_요구사항_평가_포인트_기준_질문의_code와_순서가_겹쳐도_저장된다() {
        // given
        SystemDesignProblem first = problemTestSupport.saveProblem();

        // when
        SystemDesignProblem second = problemTestSupport.saveProblem();

        // then
        assertAll(
                () -> assertThat(hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(first)).hasSize(5),
                () -> assertThat(hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(second))
                        .extracting(SystemDesignHiddenRequirement::getCode)
                        .containsExactly("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO", "RETENTION_PERIOD",
                                "SHORT_KEY_LENGTH", "CLICK_STATISTICS"),
                () -> assertThat(evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(first)).hasSize(5),
                () -> assertThat(evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(second)).hasSize(5),
                () -> assertThat(problemQuestionRepository.findByProblemOrderByQuestionOrderAsc(first)).hasSize(6),
                () -> assertThat(problemQuestionRepository.findByProblemOrderByQuestionOrderAsc(second))
                        .extracting(SystemDesignProblemQuestion::getQuestionOrder,
                                SystemDesignProblemQuestion::getStage)
                        .containsExactly(
                                tuple(1, SystemDesignStage.HIGH_LEVEL), tuple(2, SystemDesignStage.HIGH_LEVEL),
                                tuple(3, SystemDesignStage.HIGH_LEVEL), tuple(4, SystemDesignStage.DEEP_DIVE),
                                tuple(5, SystemDesignStage.DEEP_DIVE), tuple(6, SystemDesignStage.DEEP_DIVE))
        );
    }

    @Test
    void 같은_code의_문제를_두_번_저장하면_유니크_제약이_막는다() {
        // given
        problemRepository.save(SystemDesignProblemFixtureBuilder.builder().code("URL_SHORTENER").build());
        SystemDesignProblem duplicate = SystemDesignProblemFixtureBuilder.builder().code("URL_SHORTENER").build();

        // when & then
        assertThatThrownBy(() -> problemRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_problem_code");
    }

    @Test
    void 같은_문제에_같은_code의_요구사항을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        hiddenRequirementRepository.save(requirement(problem, 1, "DAILY_CREATION_VOLUME", "하루 생성량", "10만 개예요."));
        SystemDesignHiddenRequirement duplicate = requirement(problem, 2, "DAILY_CREATION_VOLUME", "생성량", "10만 개예요.");

        // when & then
        assertThatThrownBy(() -> hiddenRequirementRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_hidden_requirement_code");
    }

    @Test
    void 같은_문제에_같은_이름의_요구사항을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        hiddenRequirementRepository.save(requirement(problem, 1, "DAILY_CREATION_VOLUME", "하루 생성량", "10만 개예요."));
        SystemDesignHiddenRequirement duplicate = requirement(problem, 2, "DAILY_VOLUME", "하루 생성량", "10만 개예요.");

        // when & then
        assertThatThrownBy(() -> hiddenRequirementRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_hidden_requirement_name");
    }

    @Test
    void 같은_문제에_같은_표시_순서의_요구사항을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        hiddenRequirementRepository.save(requirement(problem, 1, "DAILY_CREATION_VOLUME", "하루 생성량", "10만 개예요."));
        SystemDesignHiddenRequirement duplicate = requirement(problem, 1, "READ_WRITE_RATIO", "조회와 생성 비율", "100배예요.");

        // when & then
        assertThatThrownBy(() -> hiddenRequirementRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_hidden_requirement_order");
    }

    @Test
    void 같은_문제에_같은_code의_평가_포인트를_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        evaluationPointRepository.save(point(problem, 1, "READ_PATH_CACHE", "캐시를 두었다"));
        SystemDesignEvaluationPoint duplicate = point(problem, 2, "READ_PATH_CACHE", "캐시를 두었다");

        // when & then
        assertThatThrownBy(() -> evaluationPointRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_evaluation_point_code");
    }

    @Test
    void 같은_문제에_같은_표시_순서의_평가_포인트를_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        evaluationPointRepository.save(point(problem, 1, "READ_PATH_CACHE", "캐시를 두었다"));
        SystemDesignEvaluationPoint duplicate = point(problem, 1, "STORAGE_ESTIMATION", "저장 용량을 추정했다");

        // when & then
        assertThatThrownBy(() -> evaluationPointRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_evaluation_point_order");
    }

    @Test
    void 같은_문제에_같은_순번의_기준_질문을_두_번_저장하면_유니크_제약이_막는다() {
        // given
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder().build());
        problemQuestionRepository.save(question(problem, 1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요."));
        SystemDesignProblemQuestion duplicate = question(problem, 1, SystemDesignStage.HIGH_LEVEL, "다시 그려 주세요.");

        // when & then
        assertThatThrownBy(() -> problemQuestionRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_system_design_problem_question_order");
    }

    private static SystemDesignHiddenRequirement requirement(SystemDesignProblem problem, int displayOrder, String code,
                                                             String name, String answer) {
        return SystemDesignHiddenRequirementFixtureBuilder.builder()
                .problem(problem)
                .displayOrder(displayOrder)
                .code(code)
                .name(name)
                .answer(answer)
                .build();
    }

    private static SystemDesignEvaluationPoint point(SystemDesignProblem problem, int displayOrder, String code,
                                                     String description) {
        return SystemDesignEvaluationPointFixtureBuilder.builder()
                .problem(problem)
                .displayOrder(displayOrder)
                .code(code)
                .description(description)
                .build();
    }

    private static SystemDesignProblemQuestion question(SystemDesignProblem problem, int questionOrder,
                                                        SystemDesignStage stage, String content) {
        return SystemDesignProblemQuestionFixtureBuilder.builder()
                .problem(problem)
                .questionOrder(questionOrder)
                .stage(stage)
                .content(content)
                .build();
    }
}
