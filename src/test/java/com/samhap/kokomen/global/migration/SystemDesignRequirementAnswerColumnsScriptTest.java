package com.samhap.kokomen.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// V58(요구사항 역질문의 answer_type·requirement_items 컬럼 추가)을 실제로 적용해 검증한다.
//
// BaseTest(앱의 Spring 컨텍스트)를 쓰지 않는다. 앱 컨텍스트는 Flyway를 늘 최신 버전까지 적용하므로 V58 전의 행을 둘 수 없다.
// SystemDesignSeedScriptTest처럼 스크래치 스키마를 따로 만들어 V57까지 적용하고, 배포 전 모양의 역질문 행을 넣은 뒤 V58을 적용한다.
// V58은 이 테이블에 컬럼만 더하므로 부모 행(회원·면접·시스템 설계 면접)은 필요 없다. 부모 테이블의 NOT NULL 컬럼은 여러 버전에 걸쳐
// 바뀌어 와서, 그 행을 손으로 맞추는 대신 행을 넣는 동안만 FK 검사를 끈다. V58을 적용하는 Flyway 연결은 FK 검사가 켜져 있다.
class SystemDesignRequirementAnswerColumnsScriptTest {

    private static final String BEFORE_VERSION = "57";
    private static final String TARGET_VERSION = "58";
    private static final String SCRATCH_SCHEMA = "kokomen_test_v" + TARGET_VERSION + "_scratch";
    private static final String ADMIN_JDBC_URL = "jdbc:mysql://localhost:13306/?serverTimezone=Asia/Seoul";
    private static final String SCRATCH_JDBC_URL =
            "jdbc:mysql://localhost:13306/" + SCRATCH_SCHEMA + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
    private static final String LEGACY_ROWS_SQL = """
            INSERT INTO system_design_requirement_question
                (id, system_design_interview_id, question_order, content, answer, asked_requirement_codes, created_at)
            VALUES (101, 9001, 1, '하루에 몇 개나 만들어지나요?', '새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.',
                    '["DAILY_CREATION_VOLUME"]', '2026-11-27 10:00:00.123456'),
                   (102, 9001, 2, '로그인해야 만들 수 있나요?', '배포 전에 저장한 면접관 답이에요.', '[]',
                    '2026-11-27 10:01:00.000000')
            """;

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateScratchSchemaWithLegacyRows() throws SQLException {
        recreateScratchSchema();
        migrate(BEFORE_VERSION);
        dataSource = new SingleConnectionDataSource(SCRATCH_JDBC_URL, "root", "root", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        insertWithoutForeignKeyChecks(LEGACY_ROWS_SQL);
        migrate(TARGET_VERSION);
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

    private static void migrate(String targetVersion) {
        Flyway.configure()
                .dataSource(SCRATCH_JDBC_URL, "root", "root")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private static void insertWithoutForeignKeyChecks(String sql) {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.update(sql);
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void 배포_전에_저장한_역질문_행은_그대로_남고_새_컬럼은_NULL이다() {
        assertThat(jdbcTemplate.query("""
                        SELECT id, question_order, content, answer, JSON_LENGTH(asked_requirement_codes) AS code_count,
                               JSON_UNQUOTE(JSON_EXTRACT(asked_requirement_codes, '$[0]')) AS first_code,
                               DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at,
                               answer_type, requirement_items
                        FROM system_design_requirement_question WHERE id IN (101, 102) ORDER BY id""",
                (rs, rowNum) -> tuple(rs.getLong("id"), rs.getInt("question_order"), rs.getString("content"),
                        rs.getString("answer"), rs.getInt("code_count"), rs.getString("first_code"),
                        rs.getString("created_at"), rs.getString("answer_type"), rs.getString("requirement_items"))))
                .containsExactly(
                        tuple(101L, 1, "하루에 몇 개나 만들어지나요?", "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.", 1,
                                "DAILY_CREATION_VOLUME", "2026-11-27 10:00:00.123456", null, null),
                        tuple(102L, 2, "로그인해야 만들 수 있나요?", "배포 전에 저장한 면접관 답이에요.", 0, null,
                                "2026-11-27 10:01:00.000000", null, null));
    }

    // 엔티티가 answer_type을 enum 이름(최대 8자)으로, requirement_items를 JSON 배열로 쓴다. 둘 다 NULL을 받아야 배포 전 행이 남는다.
    @Test
    void 새_컬럼은_NULL을_허용하는_VARCHAR_20과_JSON이고_기본값이_없다() {
        Map<String, Object> answerType = column("answer_type");
        Map<String, Object> requirementItems = column("requirement_items");

        assertAll(
                () -> assertThat(answerType)
                        .containsEntry("DATA_TYPE", "varchar")
                        .containsEntry("CHARACTER_MAXIMUM_LENGTH", 20L)
                        .containsEntry("IS_NULLABLE", "YES")
                        .containsEntry("COLUMN_DEFAULT", null),
                () -> assertThat(requirementItems)
                        .containsEntry("DATA_TYPE", "json")
                        .containsEntry("IS_NULLABLE", "YES")
                        .containsEntry("COLUMN_DEFAULT", null)
        );
    }

    @Test
    void 새_컬럼에_답의_종류와_확정_항목_배열을_저장할_수_있다() {
        // given
        insertWithoutForeignKeyChecks("""
                INSERT INTO system_design_requirement_question (id, system_design_interview_id, question_order,
                    content, answer, asked_requirement_codes, answer_type, requirement_items, created_at)
                VALUES (201, 9002, 1, '커스텀 별칭도 지원하나요?', '커스텀 별칭은 이번 범위에서 빼도 돼요.', '[]', 'ASSUMED',
                        '[{"name": "커스텀 별칭", "detail": "커스텀 별칭은 이번 범위에서 뺀다.", "source": "ASSUMED"}]',
                        NOW(6))
                """);

        // when
        Tuple saved = jdbcTemplate.queryForObject("""
                        SELECT answer_type, JSON_LENGTH(requirement_items) AS item_count,
                               JSON_UNQUOTE(JSON_EXTRACT(requirement_items, '$[0].source')) AS first_source
                        FROM system_design_requirement_question WHERE id = 201""",
                (rs, rowNum) -> tuple(rs.getString("answer_type"), rs.getInt("item_count"),
                        rs.getString("first_source")));

        // then
        assertThat(saved).isEqualTo(tuple("ASSUMED", 1, "ASSUMED"));
    }

    @Test
    void V58이_마지막으로_적용한_버전이다() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo(TARGET_VERSION);
    }

    private Map<String, Object> column(String columnName) {
        return jdbcTemplate.queryForMap("""
                SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'system_design_requirement_question' AND COLUMN_NAME = ?""",
                SCRATCH_SCHEMA, columnName);
    }
}
