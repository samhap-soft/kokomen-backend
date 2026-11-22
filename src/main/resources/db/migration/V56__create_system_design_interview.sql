-- 시스템 설계 면접.
-- interview 행(interview_type = 'SYSTEM_DESIGN')이 부모이고, 시스템 설계에만 있는 데이터를 아래 9개 테이블에 둔다.
-- interview_type은 VARCHAR(50)이라 새 값에 DDL이 필요 없다(V33).
-- 테이블명은 엔티티 클래스명의 snake_case와 같아야 하고, 모든 테이블에 id 컬럼이 있어야 한다.
-- docs 프로파일의 H2AutoIncrementCleaner가 엔티티명으로 테이블을 추측해 ALTER TABLE ... ALTER COLUMN ID를 실행한다.
-- enum 성격의 컬럼은 V51·V55처럼 VARCHAR로 둔다.

-- 문제(시드). 면접 기록이 문제·요구사항·평가 포인트·기준 질문 행을 참조하므로 한 번 넣은 행의 내용은 고치지 않는다.
-- 내용을 바꿀 때는 새 code로 문제를 넣고 이전 문제의 state를 INACTIVE로 바꾼다.
CREATE TABLE system_design_problem
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    code               VARCHAR(50)   NOT NULL,
    difficulty         VARCHAR(20)   NOT NULL,
    state              VARCHAR(20)   NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    description        VARCHAR(1000) NOT NULL,
    difficulty_comment VARCHAR(500)  NOT NULL,
    -- 없으면 NULL이고, 면접은 빈 다이어그램으로 시작한다.
    starter_diagram    JSON          NULL,
    created_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_problem_code UNIQUE (code),
    -- 시작 API가 난이도별 ACTIVE 문제를 찾는다.
    KEY idx_system_design_problem_difficulty_state (difficulty, state)
);

-- 숨은 요구사항. code는 역질문 판정 툴 필드명(requirement_ + code 소문자)이 되고, 역질문 행은 물은 요구사항을 code로 기록한다.
-- name은 리포트(missed_requirements)와 프롬프트에 보이는 이름이라 문제 안에서 겹치지 않게 한다.
CREATE TABLE system_design_hidden_requirement
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    problem_id    BIGINT       NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    answer        VARCHAR(500) NOT NULL,
    display_order INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- leftmost가 problem_id라서 FK가 요구하는 인덱스도 이것이 겸한다.
    CONSTRAINT uk_system_design_hidden_requirement_code UNIQUE (problem_id, code),
    CONSTRAINT uk_system_design_hidden_requirement_name UNIQUE (problem_id, name),
    CONSTRAINT uk_system_design_hidden_requirement_order UNIQUE (problem_id, display_order),
    CONSTRAINT fk_system_design_hidden_requirement_problem
        FOREIGN KEY (problem_id) REFERENCES system_design_problem (id)
);

-- 평가 포인트. 마지막 답변 채점이 포인트마다 MET/NOT_MET을 판정한다. code는 툴 필드명(point_ + code 소문자)이 된다.
CREATE TABLE system_design_evaluation_point
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    problem_id    BIGINT       NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    description   VARCHAR(300) NOT NULL,
    display_order INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_evaluation_point_code UNIQUE (problem_id, code),
    CONSTRAINT uk_system_design_evaluation_point_order UNIQUE (problem_id, display_order),
    CONSTRAINT fk_system_design_evaluation_point_problem
        FOREIGN KEY (problem_id) REFERENCES system_design_problem (id)
);

-- 기준 질문. question_order 1~3은 HIGH_LEVEL, 4~6은 DEEP_DIVE다.
-- target_point_codes는 이 질문이 겨누는 평가 포인트 code의 JSON 배열이다(시드 검증용, 실행 중에는 읽지 않는다).
CREATE TABLE system_design_problem_question
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    problem_id         BIGINT       NOT NULL,
    stage              VARCHAR(20)  NOT NULL,
    question_order     INT          NOT NULL,
    content            VARCHAR(500) NOT NULL,
    target_point_codes JSON         NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_problem_question_order UNIQUE (problem_id, question_order),
    CONSTRAINT fk_system_design_problem_question_problem
        FOREIGN KEY (problem_id) REFERENCES system_design_problem (id)
);

-- 면접과 1:1. interview 행이 소유자·면접 상태·종료 시각·등급 합 점수를, 이 행이 단계·현재 다이어그램·리포트 점수를 갖는다.
-- interview_id를 공유 PK로 쓰지 않고 별도 id를 두는 이유는 위의 H2AutoIncrementCleaner 제약이다.
CREATE TABLE system_design_interview
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    interview_id    BIGINT      NOT NULL,
    problem_id      BIGINT      NOT NULL,
    stage           VARCHAR(20) NOT NULL,
    current_diagram JSON        NOT NULL,
    report_score    INT         NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_interview_interview_id UNIQUE (interview_id),
    KEY idx_system_design_interview_problem_id (problem_id),
    CONSTRAINT fk_system_design_interview_interview
        FOREIGN KEY (interview_id) REFERENCES interview (id),
    CONSTRAINT fk_system_design_interview_problem
        FOREIGN KEY (problem_id) REFERENCES system_design_problem (id),
    CONSTRAINT chk_system_design_interview_report_score
        CHECK (report_score IS NULL OR report_score BETWEEN 0 AND 100)
);

-- 요구사항 역질문. 면접당 5개까지이고 서비스가 진행 락 안에서 센다.
-- question_order는 면접 안 순번(1~5)이다. (면접, 순번) 유니크와 순번 CHECK가 락 없이도 6번째 역질문을 DB에서 막고,
-- 유니크의 leftmost가 system_design_interview_id라서 FK가 요구하는 인덱스도 이것이 겸한다.
-- asked_requirement_codes는 LLM이 YES로 판정하고 서버가 검증한 숨은 요구사항 code의 JSON 배열이다(문제 순서, missed_requirements 계산).
CREATE TABLE system_design_requirement_question
(
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    system_design_interview_id BIGINT        NOT NULL,
    question_order             INT           NOT NULL,
    content                    VARCHAR(200)  NOT NULL,
    answer                     VARCHAR(1000) NOT NULL,
    asked_requirement_codes    JSON          NOT NULL,
    created_at                 DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_requirement_question_order UNIQUE (system_design_interview_id, question_order),
    CONSTRAINT fk_system_design_requirement_question_interview
        FOREIGN KEY (system_design_interview_id) REFERENCES system_design_interview (id),
    CONSTRAINT chk_system_design_requirement_question_order
        CHECK (question_order BETWEEN 1 AND 5)
);

-- 설계 질문. 출제할 때 한 행씩 넣는다. content는 실제로 낸 문장(기준 질문 그대로이거나 LLM이 다듬은 문장)이다.
-- question_order는 API의 question_id로 쓴다. (면접, 순번) 유니크가 같은 순번 질문의 중복 생성을 DB에서 막는다.
CREATE TABLE system_design_question
(
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    system_design_interview_id BIGINT       NOT NULL,
    problem_question_id        BIGINT       NOT NULL,
    stage                      VARCHAR(20)  NOT NULL,
    question_order             INT          NOT NULL,
    content                    VARCHAR(500) NOT NULL,
    created_at                 DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_question_order UNIQUE (system_design_interview_id, question_order),
    KEY idx_system_design_question_problem_question_id (problem_question_id),
    CONSTRAINT fk_system_design_question_interview
        FOREIGN KEY (system_design_interview_id) REFERENCES system_design_interview (id),
    CONSTRAINT fk_system_design_question_problem_question
        FOREIGN KEY (problem_question_id) REFERENCES system_design_problem_question (id)
);

-- 설계 답변. 질문당 답변 1개를 DB가 강제한다. diagram은 이 답변과 함께 낸 다이어그램의 정규화 JSON이다.
CREATE TABLE system_design_answer
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    question_id BIGINT        NOT NULL,
    content     VARCHAR(3000) NOT NULL,
    diagram     JSON          NOT NULL,
    answer_rank VARCHAR(20)   NOT NULL,
    feedback    VARCHAR(2000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_answer_question_id UNIQUE (question_id),
    CONSTRAINT fk_system_design_answer_question
        FOREIGN KEY (question_id) REFERENCES system_design_question (id)
);

-- 평가 포인트 판정 결과. 마지막 답변을 저장할 때 포인트마다 한 행씩 넣는다.
CREATE TABLE system_design_evaluation_result
(
    id                         BIGINT      NOT NULL AUTO_INCREMENT,
    system_design_interview_id BIGINT      NOT NULL,
    evaluation_point_id        BIGINT      NOT NULL,
    met                        BOOLEAN     NOT NULL,
    created_at                 DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_system_design_evaluation_result_point UNIQUE (system_design_interview_id, evaluation_point_id),
    KEY idx_system_design_evaluation_result_point_id (evaluation_point_id),
    CONSTRAINT fk_system_design_evaluation_result_interview
        FOREIGN KEY (system_design_interview_id) REFERENCES system_design_interview (id),
    CONSTRAINT fk_system_design_evaluation_result_point
        FOREIGN KEY (evaluation_point_id) REFERENCES system_design_evaluation_point (id)
);
