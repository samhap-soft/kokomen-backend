package com.samhap.kokomen.global.migration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

// ResumeBasedPurgeScriptTest — V53(퍼지 DML)의 실행 검증(G5).
//
// !! BaseTest(앱의 Spring 컨텍스트)를 쓰지 않는다 (리뷰 라운드 2 반영) !!
// BaseTest는 컨텍스트 기동 시 Flyway를 항상 최신 버전(V54)까지 완주시킨다 -- 실측: 이 스위트의 다른
// 어떤 테스트든 한 번이라도 컨텍스트를 띄우고 나면 information_schema상 generated_question에
// generation_id 컬럼이 이미 없다(V54:92의 DROP COLUMN 때문이다). 그런데 V53(라운드 2)의 재실행
// 안전성 로직은 정확히 그 컬럼이 아직 존재하는 스키마 상태를 전제한다 -- generation_id로 구/신규
// 플로우를 가르는 판별 자체가 그 컬럼이 있어야만 가능하다. 그래서 이 테스트는 앱의 스키마를 건드리지
// 않고, 별도의 스크래치 스키마를 만들어 Flyway로 V53까지만(target=53) 적용한 뒤 그 위에서
// 시드 + V53 재생 + 단정을 수행한다. V53 파일 자체는 여전히 클래스패스에서 그대로 읽어 재생한다
// (드리프트 방지, 라운드 1에서 확립된 설계를 유지 -- SCRIPT 상수와 executeScript()는 무수정).
class ResumeBasedPurgeScriptTest {

    private static final String SCRIPT = "db/migration/V53__purge_resume_based_interviews.sql";
    private static final String SCRATCH_SCHEMA = "kokomen_test_v53_scratch";
    private static final String ADMIN_JDBC_URL = "jdbc:mysql://localhost:13306/?serverTimezone=Asia/Seoul";
    private static final String SCRATCH_JDBC_URL =
            "jdbc:mysql://localhost:13306/" + SCRATCH_SCHEMA + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";

    // SingleConnectionDataSource를 쓰는 이유: 이 스크래치 스키마 위에서는 V53의 0단계
    // SET SESSION 문장들이 실제로 뒤따르는 문장에 적용되어야 한다(풀링된 커넥션이면 문장마다 다른
    // 커넥션을 빌려 SET SESSION이 새어나갈 수 있다 -- 이 파일 자체의 헤더 주석이 지적하는 위험이다).
    // 단일 커넥션을 고정하면 이 테스트에서는 그 위험이 원천적으로 없다.
    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateScratchSchemaThroughV53() throws SQLException {
        recreateScratchSchema();
        Flyway.configure()
                .dataSource(SCRATCH_JDBC_URL, "root", "root")
                .target("53")
                .load()
                .migrate();
        dataSource = new SingleConnectionDataSource(SCRATCH_JDBC_URL, "root", "root", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void closeScratchSchema() throws SQLException {
        dataSource.destroy();
        try (Connection adminConnection = DriverManager.getConnection(ADMIN_JDBC_URL, "root", "root");
             Statement statement = adminConnection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SCRATCH_SCHEMA);
        }
    }

    @BeforeEach
    void truncateAllSeedableTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : new String[] {
                "answer_like", "answer_memo", "interview_like", "answer", "question", "interview",
                "generated_question", "resume_question_generation", "resume_analysis", "root_question", "member"
        }) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
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
    void 퍼지_스크립트는_RESUME_BASED_후손을_전부_지우고_다른_타입은_남긴다() throws Exception {
        // given: 구 플로우 모양 RESUME_BASED 트리 1개(면접+좋아요+질문+답변+답변좋아요+답변메모) +
        //        CATEGORY_BASED 트리 1개(대조군)
        seedLegacyResumeBasedTree();
        seedCategoryBasedTree();

        // when
        executeScript();

        // then
        assertAll(
                () -> assertThat(count("interview WHERE interview_type = 'RESUME_BASED'")).isZero(),
                () -> assertThat(count("generated_question")).isZero(),
                () -> assertThat(count("interview WHERE interview_type = 'CATEGORY_BASED'")).isEqualTo(1),
                () -> assertThat(count("question")).isEqualTo(1),        // 대조군 것만 남는다
                () -> assertThat(count("answer")).isEqualTo(1),
                () -> assertThat(count("answer_like")).isEqualTo(1),
                () -> assertThat(count("answer_memo")).isEqualTo(1),
                () -> assertThat(count("interview_like")).isEqualTo(1)
        );
    }

    @Test
    void 퍼지_스크립트는_멱등이다() throws Exception {
        seedLegacyResumeBasedTree();

        executeScript();
        executeScript();   // FK 위반(ERROR 1451) 없이 0행 삭제로 수렴해야 한다

        assertThat(count("interview WHERE interview_type = 'RESUME_BASED'")).isZero();
    }

    // 리뷰 라운드 2, Finding 1 회귀 가드: interview_type='RESUME_BASED'인 행은 신규 이력서 분석
    // 플로우도 그대로 만든다(Interview.java는 무수정, 결정 M4) -- 그래서 이 스크립트가 구 플로우와
    // 신규 플로우를 실제로 구별하는지가 이 수정의 핵심이다. 구 플로우 모양(generation_id 부모)과
    // 신규 플로우 모양(analysis_id 부모, generation_id NULL)을 나란히 심어 전자만 지워지고 후자는
    // 통째로 보존되는지 확인한다 -- 구조가 거의 동일하고 부모 축만 다르다는 것 자체가 이 판별이
    // 반드시 정확해야 하는 이유다.
    @Test
    void 퍼지_스크립트는_신규_플로우_모양의_generated_question과_interview는_보존한다() throws Exception {
        // given
        seedLegacyResumeBasedTree();
        seedNewFlowResumeBasedTree();

        // when
        executeScript();

        // then: 구 플로우 트리만 지워지고, 신규 플로우 트리는 모든 후손이 그대로 남는다
        assertAll(
                () -> assertThat(count("interview WHERE interview_type = 'RESUME_BASED'")).isEqualTo(1),
                () -> assertThat(count("generated_question")).isEqualTo(1),
                () -> assertThat(count("generated_question WHERE analysis_id IS NOT NULL")).isEqualTo(1),
                () -> assertThat(count("generated_question WHERE generation_id IS NOT NULL")).isZero(),
                () -> assertThat(count("question")).isEqualTo(1),
                () -> assertThat(count("answer")).isEqualTo(1),
                () -> assertThat(count("answer_like")).isEqualTo(1),
                () -> assertThat(count("answer_memo")).isEqualTo(1),
                () -> assertThat(count("interview_like")).isEqualTo(1)
        );
    }

    // 이 태스크의 유일한 RESUME_BASED 생성 경로(Interview.java:132-137)는 항상 non-null
    // GeneratedQuestion을 넘기므로 신규 플로우는 이 모양(generated_question_id NULL)을 구조적으로
    // 만들 수 없다 -- V33 시대(resume_based_root_question, generated_question_id 컬럼 자체가
    // 없던 시절)의 잔존물만이 이 모양일 수 있다. V53의 5단계 WHERE에 `OR generated_question_id
    // IS NULL`을 넣은 결정이 실제로 이 행을 지우는지 검증한다.
    @Test
    void 퍼지_스크립트는_generated_question_id가_NULL인_RESUME_BASED_행도_지운다() throws Exception {
        // given
        long memberId = insertMember();
        long interviewId = insertResumeBasedInterviewWithNullGeneratedQuestion(memberId);
        seedInterviewDescendants(memberId, interviewId);

        // when
        executeScript();

        // then
        assertThat(count("interview WHERE interview_type = 'RESUME_BASED'")).isZero();
    }

    // 문장 순서를 그대로 실행한다. 순서가 틀리면 ERROR 1451로 실패한다 -- 그것이 이 테스트의 핵심이다.
    private void executeScript() throws Exception {
        String sql = new String(new ClassPathResource(SCRIPT).getInputStream().readAllBytes(), UTF_8);
        String withoutComments = sql.replaceAll("(?m)^\\s*--.*$", "");
        for (String statement : withoutComments.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }
    }

    private long count(String fromClause) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + fromClause, Long.class);
    }

    // 구 플로우 모양 RESUME_BASED 트리: resume_question_generation -> generated_question(generation_id) ->
    // interview(interview_type='RESUME_BASED', generated_question_id) -> question -> answer ->
    // answer_like / answer_memo / interview_like 각 1행.
    private void seedLegacyResumeBasedTree() {
        long memberId = insertMember();
        long generationId = insertResumeQuestionGeneration(memberId);
        long generatedQuestionId = insertLegacyGeneratedQuestion(generationId);
        long interviewId = insertResumeBasedInterview(memberId, generatedQuestionId);
        seedInterviewDescendants(memberId, interviewId);
    }

    // 신규 플로우 모양 RESUME_BASED 트리: resume_analysis -> generated_question(analysis_id) ->
    // interview(interview_type='RESUME_BASED', generated_question_id) -> question -> answer ->
    // answer_like / answer_memo / interview_like 각 1행. 구 플로우 트리와 유일한 차이는
    // generated_question의 부모 축(generation_id vs analysis_id)뿐이다.
    private void seedNewFlowResumeBasedTree() {
        long memberId = insertMember();
        long analysisId = insertResumeAnalysis(memberId);
        long generatedQuestionId = insertNewFlowGeneratedQuestion(analysisId);
        long interviewId = insertResumeBasedInterview(memberId, generatedQuestionId);
        seedInterviewDescendants(memberId, interviewId);
    }

    // CATEGORY_BASED 트리(대조군): root_question -> interview(interview_type='CATEGORY_BASED') ->
    // question -> answer -> answer_like / answer_memo / interview_like 각 1행.
    private void seedCategoryBasedTree() {
        long memberId = insertMember();
        long rootQuestionId = insertRootQuestion();
        long interviewId = insertCategoryBasedInterview(memberId, rootQuestionId);
        seedInterviewDescendants(memberId, interviewId);
    }

    private void seedInterviewDescendants(long memberId, long interviewId) {
        long questionId = insertQuestion(interviewId);
        long answerId = insertAnswer(questionId);
        insertAnswerLike(memberId, answerId);
        insertAnswerMemo(answerId);
        insertInterviewLike(memberId, interviewId);
    }

    private long insertMember() {
        return insertAndGetId(
                "INSERT INTO member (created_at, profile_completed) VALUES (NOW(), false)");
    }

    private long insertResumeQuestionGeneration(long memberId) {
        return insertAndGetId(
                "INSERT INTO resume_question_generation (member_id, state, created_at) "
                        + "VALUES (?, 'COMPLETED', NOW())",
                memberId);
    }

    private long insertResumeAnalysis(long memberId) {
        return insertAndGetId(
                "INSERT INTO resume_analysis (member_id, job_position, job_career, jd_provided, state, created_at) "
                        + "VALUES (?, ?, ?, false, 'COMPLETED', NOW())",
                memberId, "백엔드 개발자", "3년");
    }

    // 구 플로우 모양: generation_id만 채우고 analysis_id는 비운다(chk_generated_question_parent의
    // XOR을 그대로 재현한다).
    private long insertLegacyGeneratedQuestion(long generationId) {
        return insertAndGetId(
                "INSERT INTO generated_question (generation_id, content, question_order, created_at) "
                        + "VALUES (?, ?, 1, NOW())",
                generationId, "질문 내용");
    }

    // 신규 플로우 모양: analysis_id만 채우고 generation_id는 비운다.
    private long insertNewFlowGeneratedQuestion(long analysisId) {
        return insertAndGetId(
                "INSERT INTO generated_question (analysis_id, content, question_order, created_at) "
                        + "VALUES (?, ?, 1, NOW())",
                analysisId, "질문 내용");
    }

    private long insertResumeBasedInterview(long memberId, long generatedQuestionId) {
        return insertAndGetId(
                "INSERT INTO interview (created_at, member_id, max_question_count, interview_state, "
                        + "interview_mode, interview_type, generated_question_id) "
                        + "VALUES (NOW(), ?, 3, 'IN_PROGRESS', 'TEXT', 'RESUME_BASED', ?)",
                memberId, generatedQuestionId);
    }

    // V33 시대(resume_based_root_question, generated_question_id 컬럼이 아직 없던 시절)의
    // 잔존 모양을 재현한다.
    private long insertResumeBasedInterviewWithNullGeneratedQuestion(long memberId) {
        return insertAndGetId(
                "INSERT INTO interview (created_at, member_id, max_question_count, interview_state, "
                        + "interview_mode, interview_type, generated_question_id) "
                        + "VALUES (NOW(), ?, 3, 'IN_PROGRESS', 'TEXT', 'RESUME_BASED', NULL)",
                memberId);
    }

    private long insertRootQuestion() {
        return insertAndGetId(
                "INSERT INTO root_question (created_at, content, category, state) "
                        + "VALUES (NOW(), ?, 'JAVA_SPRING', 'ACTIVE')",
                "루트 질문 내용");
    }

    private long insertCategoryBasedInterview(long memberId, long rootQuestionId) {
        return insertAndGetId(
                "INSERT INTO interview (created_at, member_id, root_question_id, max_question_count, "
                        + "interview_state, interview_mode, interview_type) "
                        + "VALUES (NOW(), ?, ?, 3, 'IN_PROGRESS', 'TEXT', 'CATEGORY_BASED')",
                memberId, rootQuestionId);
    }

    private long insertQuestion(long interviewId) {
        return insertAndGetId(
                "INSERT INTO question (created_at, interview_id, content) VALUES (NOW(), ?, ?)",
                interviewId, "질문 내용");
    }

    private long insertAnswer(long questionId) {
        return insertAndGetId(
                "INSERT INTO answer (created_at, question_id, content, answer_rank, like_count) "
                        + "VALUES (NOW(), ?, ?, 'A', 0)",
                questionId, "답변 내용");
    }

    private void insertAnswerLike(long memberId, long answerId) {
        jdbcTemplate.update("INSERT INTO answer_like (member_id, answer_id) VALUES (?, ?)", memberId, answerId);
    }

    private void insertAnswerMemo(long answerId) {
        jdbcTemplate.update(
                "INSERT INTO answer_memo (content, answer_id, answer_memo_visibility, answer_memo_state, "
                        + "created_at) VALUES (?, ?, 'PUBLIC', 'SUBMITTED', NOW())",
                "메모 내용", answerId);
    }

    private void insertInterviewLike(long memberId, long interviewId) {
        jdbcTemplate.update(
                "INSERT INTO interview_like (member_id, interview_id) VALUES (?, ?)", memberId, interviewId);
    }

    private long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
