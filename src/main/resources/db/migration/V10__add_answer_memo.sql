-- AnswerMemo 테이블 생성
CREATE TABLE answer_memo
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    content                VARCHAR(5000) NOT NULL,
    answer_id              BIGINT        NOT NULL,
    answer_memo_visibility ENUM('PUBLIC', 'PRIVATE', 'FRIENDS') NOT NULL,
    answer_memo_state      ENUM('TEMP', 'SUBMITTED') NOT NULL,
    created_at             DATETIME(6) NOT NULL
);

-- Foreign Key 추가
ALTER TABLE answer_memo
    ADD CONSTRAINT fk_answer_memo_answer
        FOREIGN KEY (answer_id) REFERENCES answer (id);

-- Unique Key 추가
ALTER TABLE answer_memo
    ADD CONSTRAINT uk_answer_memo_answer_answer_memo_state
        UNIQUE (answer_id, answer_memo_state);
