-- 이력서 통합 분석(평가 + 질문 생성) 테이블
-- member_id NULL = 게스트. guest_token으로 소유를 식별하고, 회원가입 시 claim으로 member_id를 채운다.
-- 지표는 flat 컬럼이며, JD 미제공 시 jd_fit_* 3개는 NULL로 남는다(미산출과 0점을 DTO 경계에서 구분한다).
CREATE TABLE resume_analysis
(
    id                              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id                       BIGINT       NULL,
    guest_token                     CHAR(36)     NULL,
    guest_ip                        VARCHAR(45)  NULL,
    guest_lock_value                CHAR(36)     NULL,
    member_resume_id                BIGINT       NULL,
    member_portfolio_id             BIGINT       NULL,
    job_position                    VARCHAR(500) NOT NULL,
    job_description                 TEXT         NULL,
    job_career                      VARCHAR(100) NOT NULL,
    jd_provided                     BOOLEAN      NOT NULL,
    state                           VARCHAR(30)  NOT NULL,
    failure_reason                  VARCHAR(30)  NULL,
    problem_solving_score           INT          NULL,
    problem_solving_reason          JSON         NULL,
    problem_solving_improvements    JSON         NULL,
    project_experience_score        INT          NULL,
    project_experience_reason       JSON         NULL,
    project_experience_improvements JSON         NULL,
    technical_skills_score          INT          NULL,
    technical_skills_reason          JSON        NULL,
    technical_skills_improvements   JSON         NULL,
    soft_skills_score               INT          NULL,
    soft_skills_reason              JSON         NULL,
    soft_skills_improvements        JSON         NULL,
    jd_fit_score                    INT          NULL,
    jd_fit_reason                   JSON         NULL,
    jd_fit_improvements             JSON         NULL,
    total_score                     INT          NULL,
    total_feedback                  TEXT         NULL,
    billing_required                BOOLEAN      NOT NULL DEFAULT FALSE,
    charged_token_count             SMALLINT     NOT NULL DEFAULT 0,
    token_charge_failed             BOOLEAN      NOT NULL DEFAULT FALSE,
    question_retry_count            INT          NOT NULL DEFAULT 0,
    evaluation_completed_at         DATETIME(6)  NULL,
    question_started_at             DATETIME(6)  NULL,
    completed_at                    DATETIME(6)  NULL,
    created_at                      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- (member_id, created_at): 회원 목록 조회 + 진행 중 1건 검사 + 첫 사용 판정 + claim 한도 검사를 모두 커버한다.
    -- leftmost가 member_id라서 아래 member FK가 요구하는 인덱스도 이것이 겸한다.
    KEY idx_resume_analysis_member_id_created_at (member_id, created_at),
    -- 잔류 PENDING 회수: WHERE state = 'PENDING' AND created_at < ?
    KEY idx_resume_analysis_state_created_at (state, created_at),
    -- 잔류 질문 단계 회수: WHERE state = 'EVALUATION_COMPLETED' AND question_started_at < ?
    KEY idx_resume_analysis_state_question_started_at (state, question_started_at),
    CONSTRAINT uk_resume_analysis_guest_token UNIQUE (guest_token),
    CONSTRAINT fk_resume_analysis_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_resume_analysis_member_resume FOREIGN KEY (member_resume_id) REFERENCES member_resume (id),
    CONSTRAINT fk_resume_analysis_member_portfolio FOREIGN KEY (member_portfolio_id) REFERENCES member_portfolio (id),
    -- XOR가 아니라 OR. claim은 member_id만 채우고 guest_token을 지우지 않으므로 claim 후에는 둘 다 존재한다.
    CONSTRAINT chk_resume_analysis_owner CHECK (member_id IS NOT NULL OR guest_token IS NOT NULL),
    CONSTRAINT chk_resume_analysis_scores CHECK (
        (problem_solving_score    IS NULL OR problem_solving_score    BETWEEN 0 AND 100) AND
        (project_experience_score IS NULL OR project_experience_score BETWEEN 0 AND 100) AND
        (technical_skills_score   IS NULL OR technical_skills_score   BETWEEN 0 AND 100) AND
        (soft_skills_score        IS NULL OR soft_skills_score        BETWEEN 0 AND 100) AND
        (jd_fit_score             IS NULL OR jd_fit_score             BETWEEN 0 AND 100))
);

-- 추출 원문. 폴링이 부모 행을 고빈도로 읽으므로 LONGTEXT를 부모에서 분리한다.
-- 질문 재생성(무과금)이 재추출·S3 재다운로드 없이 동작하는 근거이며, 게스트의 유일한 원문 보관처다.
-- id 컬럼은 H2AutoIncrementCleaner(docs 프로파일)가 ALTER TABLE ... ALTER COLUMN ID를 요구하므로 필수다.
CREATE TABLE resume_analysis_source_text
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    analysis_id       BIGINT      NOT NULL,
    resume_content    LONGTEXT    NOT NULL,
    portfolio_content LONGTEXT    NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rast_analysis_id UNIQUE (analysis_id),
    CONSTRAINT fk_rast_analysis FOREIGN KEY (analysis_id) REFERENCES resume_analysis (id) ON DELETE CASCADE
);

-- 질문은 기존 generated_question을 재사용한다.
-- interview.generated_question_id FK(V38)와 Interview(Member, GeneratedQuestion, ...) 생성자를 그대로 쓰기 위한
-- 결정이며, interview 테이블·Interview 엔티티·InterviewType·getDisplayQuestion()은 0바이트 수정한다.
ALTER TABLE generated_question MODIFY COLUMN generation_id BIGINT NULL;
ALTER TABLE generated_question ADD COLUMN analysis_id BIGINT NULL;

-- FK 추가 전에 인덱스를 먼저 만들어 MySQL의 자동 인덱스 생성을 피한다.
CREATE INDEX idx_generated_question_analysis_id ON generated_question (analysis_id);

ALTER TABLE generated_question
    ADD CONSTRAINT fk_generated_question_analysis FOREIGN KEY (analysis_id) REFERENCES resume_analysis (id);

-- 부모는 정확히 하나(구 플로우 = generation_id, 신규 = analysis_id).
-- 기존 행은 전부 generation_id NOT NULL / analysis_id NULL 이므로 무중단 통과한다.
ALTER TABLE generated_question
    ADD CONSTRAINT chk_generated_question_parent
        CHECK ((generation_id IS NULL) <> (analysis_id IS NULL));
