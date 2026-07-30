package com.samhap.kokomen.global.migration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
//
// 리뷰 라운드 1(Finding 1) 반영: V53의 모든 DELETE는 이제 CUTOFF(아래 상수, V53 파일과 동일한 값)
// 이전에 생성된 행만 지운다. interview_type='RESUME_BASED'만으로는 구 플로우/신규 플로우 산물을
// 구별할 수 없으므로(신규 플로우도 같은 Interview 생성자로 같은 값을 쓴다), 이 컷오프가 재실행 시
// 신규 플로우 데이터를 보존하는 유일한 장치다. 아래 두 번째 테스트가 정확히 그 경계를 검증한다.
class ResumeBasedPurgeScriptTest extends BaseTest {

    private static final String SCRIPT = "db/migration/V53__purge_resume_based_interviews.sql";

    // V53 파일에 리터럴로 박아 넣은 컷오프와 동일한 값이어야 한다 -- 두 값이 벌어지면 이 테스트는
    // 실제 파일이 아닌 자기 자신의 가정만 검증하게 된다.
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 15, 0, 0, 0);

    // LocalDateTime#toString()은 초가 0이면 "2026-08-15T00:00"처럼 'T' 구분자·초 생략형을 내놓는데,
    // MySQL의 문자열->DATETIME 암묵 변환은 이 형식을 받지 않는다. SQL 리터럴에 꽂을 때는 항상
    // V53과 동일한 "yyyy-MM-dd HH:mm:ss" 형식으로 명시적으로 포맷한다.
    private static final DateTimeFormatter SQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 퍼지_스크립트는_RESUME_BASED_후손을_전부_지우고_다른_타입은_남긴다() throws Exception {
        // given: RESUME_BASED 트리 1개(면접+좋아요+질문+답변+답변좋아요+답변메모) +
        //        CATEGORY_BASED 트리 1개(대조군) + generated_question 1행(부모 resume_analysis 필요)
        seedResumeBasedTree(CUTOFF.minusDays(1));
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
        seedResumeBasedTree(CUTOFF.minusDays(1));

        executeScript();
        executeScript();   // FK 위반(ERROR 1451) 없이 0행 삭제로 수렴해야 한다

        assertThat(count("interview WHERE interview_type = 'RESUME_BASED'")).isZero();
    }

    // Finding 1 회귀 가드: interview_type='RESUME_BASED'인 행은 신규 이력서 분석 플로우도 그대로
    // 만든다(Interview.java는 무수정, 결정 M4) -- 그래서 컷오프 이후에 생성된 행은 이 스크립트가
    // "구 플로우 잔존물만 지운다"는 전제 자체를 깨뜨리는 산 데이터일 수 있다. 컷오프 이전 트리는
    // 지워지고 컷오프 이후 트리는 통째로 보존되어야만, 이 스크립트를 신규 플로우 서비스 시작 이후에
    // 재실행해도 안전하다는 주장이 성립한다.
    @Test
    void 퍼지_스크립트는_컷오프_이후에_생성된_RESUME_BASED_행은_보존한다() throws Exception {
        // given: 컷오프 이전 트리(삭제 대상) + 컷오프 이후 트리(신규 플로우가 만들었다고 가정 -- 구조는
        // 구 플로우와 동일하다. 유일한 차이가 created_at뿐이라는 것 자체가 이 수정이 지키려는 불변식이다)
        seedResumeBasedTree(CUTOFF.minusDays(1));
        seedResumeBasedTree(CUTOFF.plusDays(1));

        // when
        executeScript();

        // then: 컷오프 이전 트리만 지워지고, 컷오프 이후 트리는 모든 후손이 그대로 남는다
        assertAll(
                () -> assertThat(count("interview WHERE interview_type = 'RESUME_BASED'")).isEqualTo(1),
                () -> assertThat(count("interview WHERE interview_type = 'RESUME_BASED' AND created_at >= '"
                        + CUTOFF.format(SQL_DATETIME) + "'")).isEqualTo(1),
                () -> assertThat(count("generated_question")).isEqualTo(1),
                () -> assertThat(count("generated_question WHERE created_at >= '"
                        + CUTOFF.format(SQL_DATETIME) + "'")).isEqualTo(1),
                () -> assertThat(count("question")).isEqualTo(1),
                () -> assertThat(count("answer")).isEqualTo(1),
                () -> assertThat(count("answer_like")).isEqualTo(1),
                () -> assertThat(count("answer_memo")).isEqualTo(1),
                () -> assertThat(count("interview_like")).isEqualTo(1)
        );
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
    //
    // createdAt은 generated_question과 interview에만 명시적으로 주입한다 -- V53의 WHERE절이 실제로
    // 검사하는 것은 오직 이 둘의 created_at뿐이다(question/answer/좋아요/메모는 조상 interview의
    // created_at으로 판단되고, 자기 자신의 created_at은 삭제 여부와 무관하다). 나머지 후손의
    // created_at은 NOW()로 둬도 이 스크립트의 정확성에 영향을 주지 않는다.
    //
    // NOW() 대신 항상 명시적 LocalDateTime을 요구하는 이유: CUTOFF가 파일에 박힌 고정 리터럴이므로,
    // 이 테스트가 실제로 실행되는 날짜(NOW())가 언젠가 CUTOFF를 지나버리면 "컷오프 이전"이라는 이
    // 테스트의 전제 자체가 조용히 깨진다. 매 호출부에서 CUTOFF 기준 상대값을 넘기면 이 테스트는
    // 실행 시각과 무관하게 항상 같은 의미를 유지한다.
    private void seedResumeBasedTree(LocalDateTime createdAt) {
        long memberId = insertMember();
        long analysisId = insertResumeAnalysis(memberId);
        long generatedQuestionId = insertGeneratedQuestion(analysisId, createdAt);
        long interviewId = insertResumeBasedInterview(memberId, generatedQuestionId, createdAt);
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

    private long insertGeneratedQuestion(long analysisId, LocalDateTime createdAt) {
        return insertAndGetId(
                "INSERT INTO generated_question (analysis_id, content, question_order, created_at) "
                        + "VALUES (?, ?, 1, ?)",
                analysisId, "질문 내용", createdAt);
    }

    private long insertResumeBasedInterview(long memberId, long generatedQuestionId, LocalDateTime createdAt) {
        return insertAndGetId(
                "INSERT INTO interview (created_at, member_id, max_question_count, interview_state, "
                        + "interview_mode, interview_type, generated_question_id) "
                        + "VALUES (?, ?, 3, 'IN_PROGRESS', 'TEXT', 'RESUME_BASED', ?)",
                createdAt, memberId, generatedQuestionId);
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
