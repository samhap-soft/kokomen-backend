package com.samhap.kokomen.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.persistence.StringListJsonConverter;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// V57(시스템 설계 시드 문제 5개)을 실제로 적용해 검증한다.
//
// BaseTest(앱의 Spring 컨텍스트)를 쓰지 않는다. MySQLDatabaseCleaner가 매 테스트 전에 모든 테이블을 TRUNCATE하므로
// 앱 테스트에서는 Flyway 시드가 남지 않는다. 그래서 ResumeBasedPurgeScriptTest처럼 스크래치 스키마를 따로 만들어
// Flyway로 SEED_VERSION까지만 적용하고 그 위에서 단정한다. 뒤의 마이그레이션이 시드 행을 바꾸더라도(INACTIVE 전환 등)
// 이 테스트는 V57이 넣은 내용만 본다. 마이그레이션 번호를 바꾸면 SEED_VERSION도 함께 바꾼다.
//
// 테스트마다 문제 code 5개가 모두 있는지 먼저 단정한다. 시드가 비면 문제별 단정이 아무것도 돌지 않고 통과하기 때문이다.
class SystemDesignSeedScriptTest {

    private static final String SEED_VERSION = "57";
    private static final String SCRATCH_SCHEMA = "kokomen_test_v" + SEED_VERSION + "_scratch";
    private static final String ADMIN_JDBC_URL = "jdbc:mysql://localhost:13306/?serverTimezone=Asia/Seoul";
    private static final String SCRATCH_JDBC_URL =
            "jdbc:mysql://localhost:13306/" + SCRATCH_SCHEMA + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";

    private static final List<String> PROBLEM_CODES = List.of(
            "URL_SHORTENER", "FIRST_COME_COUPON", "ONE_TO_ONE_CHAT", "SEARCH_AUTOCOMPLETE", "IMAGE_UPLOAD");

    // 숨은 요구사항의 답에만 나오는 숫자 표현이다. 공개 문구(제목·설명·난이도 코멘트·평가 포인트·질문)는 화면과
    // L2·L3 system 프롬프트에 그대로 들어가므로, 여기 있는 표현이 공개 문구에 있으면 묻지 않은 수치가 드러난다.
    private static final Map<String, List<String>> HIDDEN_NUMBER_EXPRESSIONS = Map.of(
            "URL_SHORTENER", List.of("10만", "100배", "5년", "7자"),
            "FIRST_COME_COUPON", List.of("10초", "30만", "2번", "5만", "1초", "1분"),
            "ONE_TO_ONE_CHAT", List.of("100만", "10%", "20개", "50바이트", "1초", "3년"),
            "SEARCH_AUTOCOMPLETE", List.of("500만", "4번", "5번", "200만", "6글자", "10개", "7일", "100ms", "1시간"),
            "IMAGE_UPLOAD", List.of("100만", "4%", "10장", "3장", "20MB", "2MB", "60장", "15장", "1초", "5초"));

    // 공개 문구에 허용한 숫자와 그 위치(PUBLIC_TEXT_SQL의 location). 숨은 수치가 아니다.
    // URL_SHORTENER의 301·302는 HTTP 상태 코드로 6번 질문과 평가 포인트에만, ONE_TO_ONE_CHAT의 1:1은 채팅 종류로
    // 제목과 설명에만 허용한다.
    private static final List<AllowedNumber> ALLOWED_NUMBERS = List.of(
            new AllowedNumber("URL_SHORTENER", Set.of("QUESTION 6", "EVALUATION_POINT"),
                    Pattern.compile("(?<!\\d)30[12](?!\\d)")),
            new AllowedNumber("ONE_TO_ONE_CHAT", Set.of("TITLE", "DESCRIPTION"),
                    Pattern.compile("(?<!\\d)1:1(?!\\d)")));

    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern REQUIREMENT_NAME_FORMAT = Pattern.compile("^[^|:\\r\\n]+$");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    // 지원자가 역질문을 하지 않아도 보는 문구. location은 출처이고 질문에는 순번을 붙인다(QUESTION 6).
    private static final String PUBLIC_TEXT_SQL = """
            SELECT p.code AS problem_code, 'TITLE' AS location, p.title AS content
            FROM system_design_problem p
            UNION ALL
            SELECT p.code, 'DESCRIPTION', p.description
            FROM system_design_problem p
            UNION ALL
            SELECT p.code, 'DIFFICULTY_COMMENT', p.difficulty_comment
            FROM system_design_problem p
            UNION ALL
            SELECT p.code, 'EVALUATION_POINT', e.description
            FROM system_design_evaluation_point e JOIN system_design_problem p ON p.id = e.problem_id
            UNION ALL
            SELECT p.code, CONCAT('QUESTION ', q.question_order), q.content
            FROM system_design_problem_question q JOIN system_design_problem p ON p.id = q.problem_id
            """;

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateScratchSchemaThroughSeedVersion() throws SQLException {
        recreateScratchSchema();
        Flyway.configure()
                .dataSource(SCRATCH_JDBC_URL, "root", "root")
                .target(SEED_VERSION)
                .load()
                .migrate();
        dataSource = new SingleConnectionDataSource(SCRATCH_JDBC_URL, "root", "root", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void dropScratchSchema() throws SQLException {
        // @BeforeAll이 마이그레이션 도중에 실패하면 dataSource가 없다. 원래 실패 원인을 NPE로 덮지 않는다.
        if (dataSource != null) {
            dataSource.destroy();
        }
        try (Connection adminConnection = DriverManager.getConnection(ADMIN_JDBC_URL, "root", "root");
             Statement statement = adminConnection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SCRATCH_SCHEMA);
        }
    }

    private static void recreateScratchSchema() throws SQLException {
        try (Connection adminConnection = DriverManager.getConnection(ADMIN_JDBC_URL, "root", "root");
             Statement statement = adminConnection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SCRATCH_SCHEMA);
            statement.execute(
                    "CREATE DATABASE " + SCRATCH_SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    @Test
    void BASIC_ACTIVE_문제가_다섯_개다() {
        List<Tuple> problems = jdbcTemplate.query(
                "SELECT code, difficulty, state FROM system_design_problem ORDER BY id",
                (rs, rowNum) -> tuple(rs.getString("code"), rs.getString("difficulty"), rs.getString("state")));

        // 엔티티는 두 컬럼을 enum 이름(EnumType.STRING)으로 읽는다. 시작 API는 BASIC·ACTIVE 문제만 찾는다.
        String basic = SystemDesignDifficulty.BASIC.name();
        String active = SystemDesignProblemState.ACTIVE.name();
        assertThat(problems).containsExactly(
                tuple("URL_SHORTENER", basic, active),
                tuple("FIRST_COME_COUPON", basic, active),
                tuple("ONE_TO_ONE_CHAT", basic, active),
                tuple("SEARCH_AUTOCOMPLETE", basic, active),
                tuple("IMAGE_UPLOAD", basic, active));
    }

    // 테스트의 URL 단축기 문제(SystemDesignProblemTestSupport, SystemDesignProblemContextFixture)는 실제로 출제할 시드와 같아야
    // 한다. 프롬프트 정본 파일이 그 픽스처로 조립한 결과라, 둘이 어긋나면 정본이 운영에서 모델이 받는 프롬프트와 달라진다.
    @Test
    void URL_단축기_시드는_테스트_픽스처와_같다() {
        List<String> problem = jdbcTemplate.queryForObject(
                "SELECT title, description, difficulty_comment FROM system_design_problem WHERE code = 'URL_SHORTENER'",
                (rs, rowNum) -> List.of(rs.getString("title"), rs.getString("description"),
                        rs.getString("difficulty_comment")));
        List<Requirement> requirements = jdbcTemplate.query(
                "SELECT r.code, r.name, r.answer FROM system_design_hidden_requirement r "
                        + "JOIN system_design_problem p ON p.id = r.problem_id "
                        + "WHERE p.code = 'URL_SHORTENER' ORDER BY r.display_order",
                (rs, rowNum) -> new Requirement(rs.getString("code"), rs.getString("name"), rs.getString("answer")));
        List<EvaluationPoint> evaluationPoints = jdbcTemplate.query(
                "SELECT e.code, e.description FROM system_design_evaluation_point e "
                        + "JOIN system_design_problem p ON p.id = e.problem_id "
                        + "WHERE p.code = 'URL_SHORTENER' ORDER BY e.display_order",
                (rs, rowNum) -> new EvaluationPoint(rs.getString("code"), rs.getString("description")));

        assertAll(
                () -> assertThat(problem).containsExactly(SystemDesignProblemTestSupport.TITLE,
                        SystemDesignProblemTestSupport.DESCRIPTION, SystemDesignProblemTestSupport.DIFFICULTY_COMMENT),
                () -> assertThat(requirements)
                        .containsExactlyElementsOf(SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS),
                () -> assertThat(evaluationPoints)
                        .containsExactlyElementsOf(SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS)
        );
    }

    @Test
    void 문제마다_숨은_요구사항_5개와_평가_포인트_4개나_5개가_순서대로_있다() {
        Map<String, List<Integer>> requirementOrders = queryByProblem(
                "SELECT p.code AS problem_code, r.display_order FROM system_design_hidden_requirement r "
                        + "JOIN system_design_problem p ON p.id = r.problem_id ORDER BY p.id, r.display_order",
                (rs, rowNum) -> rs.getInt("display_order"));
        Map<String, List<Integer>> pointOrders = queryByProblem(
                "SELECT p.code AS problem_code, e.display_order FROM system_design_evaluation_point e "
                        + "JOIN system_design_problem p ON p.id = e.problem_id ORDER BY p.id, e.display_order",
                (rs, rowNum) -> rs.getInt("display_order"));

        assertThat(requirementOrders).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, orders) -> assertThat(orders).as(problemCode)
                        .containsExactly(1, 2, 3, 4, 5));
        assertThat(pointOrders).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, orders) -> assertThat(orders).as(problemCode)
                        .isIn(List.of(1, 2, 3, 4), List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void 문제마다_기준_질문이_6개이고_1부터_3은_개략_4부터_6은_심화다() {
        Map<String, List<Tuple>> questions = queryByProblem(
                "SELECT p.code AS problem_code, q.question_order, q.stage FROM system_design_problem_question q "
                        + "JOIN system_design_problem p ON p.id = q.problem_id ORDER BY p.id, q.question_order",
                (rs, rowNum) -> tuple(rs.getInt("question_order"), rs.getString("stage")));

        String highLevel = SystemDesignStage.HIGH_LEVEL.name();
        String deepDive = SystemDesignStage.DEEP_DIVE.name();
        assertThat(questions).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, orderAndStages) -> assertThat(orderAndStages).as(problemCode)
                        .containsExactly(
                                tuple(1, highLevel), tuple(2, highLevel), tuple(3, highLevel),
                                tuple(4, deepDive), tuple(5, deepDive), tuple(6, deepDive)));
    }

    @Test
    void 기준_질문이_겨누는_평가_포인트는_실제_code이고_모든_평가_포인트를_덮는다() {
        Map<String, List<String>> pointCodes = readEvaluationPointColumn("code");
        // 엔티티(SystemDesignProblemQuestion)가 이 컬럼을 읽을 때 쓰는 컨버터로 읽는다.
        StringListJsonConverter converter = new StringListJsonConverter();
        Map<String, List<List<String>>> targetsByQuestion = queryByProblem(
                "SELECT p.code AS problem_code, q.target_point_codes FROM system_design_problem_question q "
                        + "JOIN system_design_problem p ON p.id = q.problem_id ORDER BY p.id, q.question_order",
                (rs, rowNum) -> converter.convertToEntityAttribute(rs.getString("target_point_codes")));

        assertThat(targetsByQuestion).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, targets) -> {
                    List<String> problemPointCodes = pointCodes.getOrDefault(problemCode, List.of());
                    assertThat(targets).as(problemCode)
                            .allSatisfy(target -> assertThat(target).isNotEmpty().isSubsetOf(problemPointCodes));
                    assertThat(targets.stream().flatMap(List::stream).distinct()).as(problemCode)
                            .containsExactlyInAnyOrderElementsOf(problemPointCodes);
                });
    }

    @Test
    void 평가_포인트와_숨은_요구사항의_code는_영문_대문자_숫자_밑줄이다() {
        // 숨은 요구사항 code는 역질문 판정 도구의 필드 이름(requirement_{code 소문자})이 되고,
        // 평가 포인트 code는 마지막 채점 도구의 필드 이름(point_{code 소문자})이 된다.
        Map<String, List<String>> requirementCodes = readHiddenRequirementColumn("code");
        Map<String, List<String>> pointCodes = readEvaluationPointColumn("code");

        assertThat(requirementCodes).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, codes) -> assertThat(codes).as(problemCode)
                        .allSatisfy(code -> assertThat(code).matches(CODE_FORMAT)));
        assertThat(pointCodes).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, codes) -> assertThat(codes).as(problemCode)
                        .allSatisfy(code -> assertThat(code).matches(CODE_FORMAT)));
    }

    // 평가 포인트마다 증거 필드(point_{code 소문자}_evidence)가 판정 필드 앞에 붙는다. code가 _EVIDENCE로 끝나면 그 판정 필드가
    // 다른 포인트의 증거 필드와 이름이 같아진다(X_EVIDENCE의 판정 필드 = X의 증거 필드).
    @Test
    void 평가_포인트_code는_EVIDENCE로_끝나지_않는다() {
        Map<String, List<String>> pointCodes = readEvaluationPointColumn("code");

        assertThat(pointCodes).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, codes) -> assertThat(codes).as(problemCode)
                        .allSatisfy(code -> assertThat(code).doesNotEndWith("_EVIDENCE")));
    }

    @Test
    void 시작_다이어그램은_다이어그램_규칙을_통과한다() {
        Map<String, String> starterDiagrams = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT code, starter_diagram FROM system_design_problem ORDER BY id", rs -> {
            starterDiagrams.put(rs.getString("code"), rs.getString("starter_diagram"));
        });

        // SEARCH_AUTOCOMPLETE는 시작 다이어그램이 없는 경로(빈 다이어그램으로 시작)를 쓰려고 비워 두었다.
        assertThat(starterDiagrams).containsOnlyKeys(PROBLEM_CODES)
                .containsEntry("SEARCH_AUTOCOMPLETE", null);
        // 엔티티의 컨버터도 fromJson으로 읽으므로, 규칙을 어긴 시작 다이어그램은 그 문제로 시작하는 면접을 깨뜨린다.
        starterDiagrams.remove("SEARCH_AUTOCOMPLETE");
        assertThat(starterDiagrams).allSatisfy((problemCode, json) ->
                assertThatCode(() -> SystemDesignDiagram.fromJson(json)).as(problemCode)
                        .doesNotThrowAnyException());
    }

    @Test
    void 숨은_요구사항_답은_120자_이하다() {
        // 역질문 답이 폴백하면 물은 요구사항의 답을 공백 하나로 잇는다. 다섯 답을 모두 이어도
        // 역질문 답 컬럼(system_design_requirement_question.answer VARCHAR(1000))에 든다.
        Map<String, List<String>> answers = readHiddenRequirementColumn("answer");

        assertThat(answers).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, problemAnswers) -> assertThat(problemAnswers).as(problemCode)
                        .allSatisfy(answer -> assertThat(answer).hasSizeLessThanOrEqualTo(120)));
    }

    @Test
    void 숨은_요구사항_이름에는_줄바꿈과_구분_기호가_없다() {
        // 이름은 프롬프트의 한 줄 형식(L1 "| 이름: … |", L3 "- 이름: 확인함")에 들어간다. 그 줄의 구분자와 겹치면 안 된다.
        // 판정은 이름이 아니라 code 필드로 하므로 가운뎃점 같은 다른 기호는 받는다.
        Map<String, List<String>> names = readHiddenRequirementColumn("name");

        assertThat(names).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, problemNames) -> assertThat(problemNames).as(problemCode)
                        .allSatisfy(name -> assertThat(name).matches(REQUIREMENT_NAME_FORMAT)));
    }

    @Test
    void 한_문제의_다섯_답은_숫자_토큰을_겹쳐_쓰지_않는다() {
        // 역질문 답의 숫자 검증은 물은 요구사항의 답에 나오는 숫자만 허용한다. 두 답이 같은 숫자를 쓰면
        // 한쪽만 물었을 때 다른 쪽의 수치가 새어 나가도 걸러지지 않는다.
        Map<String, List<String>> answers = readHiddenRequirementColumn("answer");

        assertThat(answers).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, problemAnswers) -> {
                    Map<String, List<String>> answersByToken = new LinkedHashMap<>();
                    for (String answer : problemAnswers) {
                        numberTokensOf(answer).forEach(token ->
                                answersByToken.computeIfAbsent(token, key -> new ArrayList<>()).add(answer));
                    }
                    assertThat(answersByToken).as(problemCode)
                            .allSatisfy((token, answersWithToken) -> assertThat(answersWithToken).as(token).hasSize(1));
                });
    }

    @Test
    void 공개_문구에는_숨은_요구사항의_숫자가_없다() {
        Map<String, List<String>> answers = readHiddenRequirementColumn("answer");
        Map<String, List<PublicText>> publicTexts = queryByProblem(PUBLIC_TEXT_SQL,
                (rs, rowNum) -> new PublicText(rs.getString("location"), rs.getString("content")));

        assertThat(publicTexts).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, texts) -> {
                    List<String> expressions = HIDDEN_NUMBER_EXPRESSIONS.get(problemCode);
                    // 표현을 잘못 옮기면 공개 문구에도 없어서 아래 단정이 헛돈다. 먼저 그 문제의 답에 있는 표현인지 본다.
                    assertThat(expressions).as(problemCode)
                            .allSatisfy(expression -> assertThat(answers.get(problemCode))
                                    .anySatisfy(answer -> assertThat(answer).contains(expression)));
                    assertThat(texts).as(problemCode)
                            .allSatisfy(text -> assertThat(text.content()).doesNotContain(expressions));
                });
    }

    @Test
    void 숨은_요구사항_이름에는_숨은_요구사항의_숫자가_없다() {
        // 마지막 채점(L3) user 메시지는 확인하지 않은 요구사항의 이름도 쓰고("- 이름: 확인하지 않음") 답의 수치는 넣지 않는다.
        // 이름에 수치가 있으면 묻지 않은 수치가 종합 평가에 들어간다. 숫자 자체는 막지 않는다(1인당 발급 한도).
        Map<String, List<String>> names = readHiddenRequirementColumn("name");

        assertThat(names).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, problemNames) -> assertThat(problemNames).as(problemCode)
                        .allSatisfy(name -> assertThat(name)
                                .doesNotContain(HIDDEN_NUMBER_EXPRESSIONS.get(problemCode))));
    }

    @Test
    void 공개_문구에는_숫자를_쓰지_않는다() {
        // 숨은 수치 목록에 없는 새 숫자가 공개 문구에 들어가는 것도 막는다.
        Map<String, List<PublicText>> publicTexts = queryByProblem(PUBLIC_TEXT_SQL,
                (rs, rowNum) -> new PublicText(rs.getString("location"), rs.getString("content")));

        assertThat(publicTexts).containsOnlyKeys(PROBLEM_CODES)
                .allSatisfy((problemCode, texts) -> assertThat(texts).as(problemCode)
                        .allSatisfy(text -> assertThat(withoutAllowedNumbers(problemCode, text))
                                .doesNotContainPattern(DIGIT)));
    }

    private static String withoutAllowedNumbers(String problemCode, PublicText text) {
        String content = text.content();
        for (AllowedNumber allowed : ALLOWED_NUMBERS) {
            if (allowed.problemCode().equals(problemCode) && allowed.locations().contains(text.location())) {
                content = allowed.pattern().matcher(content).replaceAll("");
            }
        }
        return content;
    }

    private static Set<String> numberTokensOf(String text) {
        return NUMBER_TOKEN.matcher(text).results()
                .map(MatchResult::group)
                .collect(Collectors.toSet());
    }

    private Map<String, List<String>> readHiddenRequirementColumn(String column) {
        return queryByProblem(
                "SELECT p.code AS problem_code, r." + column + " AS column_value "
                        + "FROM system_design_hidden_requirement r JOIN system_design_problem p ON p.id = r.problem_id "
                        + "ORDER BY p.id, r.display_order",
                (rs, rowNum) -> rs.getString("column_value"));
    }

    private Map<String, List<String>> readEvaluationPointColumn(String column) {
        return queryByProblem(
                "SELECT p.code AS problem_code, e." + column + " AS column_value "
                        + "FROM system_design_evaluation_point e JOIN system_design_problem p ON p.id = e.problem_id "
                        + "ORDER BY p.id, e.display_order",
                (rs, rowNum) -> rs.getString("column_value"));
    }

    // 쿼리의 problem_code 컬럼(문제 code)별로 행을 모은다. 문제 순서와 문제 안의 행 순서는 쿼리의 정렬을 따른다.
    private <T> Map<String, List<T>> queryByProblem(String sql, RowMapper<T> rowMapper) {
        return jdbcTemplate.query(sql,
                        (rs, rowNum) -> new ProblemRow<>(rs.getString("problem_code"), rowMapper.mapRow(rs, rowNum)))
                .stream()
                .collect(Collectors.groupingBy(ProblemRow::problemCode, LinkedHashMap::new,
                        Collectors.mapping(ProblemRow::value, Collectors.toList())));
    }

    private record ProblemRow<T>(String problemCode, T value) {
    }

    private record PublicText(String location, String content) {
    }

    // problemCode 문제의 locations에 있는 공개 문구에서만 pattern에 맞는 숫자를 허용한다.
    private record AllowedNumber(String problemCode, Set<String> locations, Pattern pattern) {
    }
}
