package com.samhap.kokomen.global.migration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

// ResumeBasedPurgeScriptTest — V53(퍼지 DML)의 실행 검증(G5).
// Flyway는 컨텍스트 기동 시 1회 실행되고 MySQLDatabaseCleaner가 @BeforeEach에서 TRUNCATE하므로
// 마이그레이션 시점에는 시드 데이터가 존재할 수 없다. 그래서 같은 스크립트를 여기서 재실행한다.
//
// 이 테스트가 도는 스키마는 V54까지 적용된 최종 형상이다(generation_id 부재, analysis_id NOT NULL).
// V53의 문장들은 generation_id를 참조하지 않으므로 그대로 성립하고, 오히려 "최종 스키마에서도
// 삭제 순서가 유효하다"를 함께 보장한다.
class ResumeBasedPurgeScriptTest extends BaseTest {

    private static final String SCRIPT = "db/migration/V53__purge_resume_based_interviews.sql";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 퍼지_스크립트는_RESUME_BASED_후손을_전부_지우고_다른_타입은_남긴다() throws Exception {
        // given: RESUME_BASED 트리 1개(면접+좋아요+질문+답변+답변좋아요+답변메모) +
        //        CATEGORY_BASED 트리 1개(대조군) + generated_question 1행(부모 resume_analysis 필요)
        seedResumeBasedTree();
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
        seedResumeBasedTree();

        executeScript();
        executeScript();   // FK 위반(ERROR 1451) 없이 0행 삭제로 수렴해야 한다

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

    // RESUME_BASED 트리: resume_analysis -> generated_question(analysis_id) ->
    // interview(interview_type='RESUME_BASED', generated_question_id) -> question -> answer ->
    // answer_like / answer_memo / interview_like 각 1행.
    private void seedResumeBasedTree() {
        long memberId = insertMember();
        long analysisId = insertResumeAnalysis(memberId);
        long generatedQuestionId = insertGeneratedQuestion(analysisId);
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

    private long insertResumeAnalysis(long memberId) {
        return insertAndGetId(
                "INSERT INTO resume_analysis (member_id, job_position, job_career, jd_provided, state, created_at) "
                        + "VALUES (?, ?, ?, false, 'COMPLETED', NOW())",
                memberId, "백엔드 개발자", "3년");
    }

    private long insertGeneratedQuestion(long analysisId) {
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
