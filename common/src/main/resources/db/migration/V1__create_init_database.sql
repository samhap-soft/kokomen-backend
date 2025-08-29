CREATE TABLE root_question
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    category   ENUM('ALGORITHM', 'DATABASE', 'DATA_STRUCTURE', 'NETWORK', 'OPERATING_SYSTEM') NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE member
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    kakao_id   BIGINT       NOT NULL,
    nickname   VARCHAR(255) NOT NULL,
    score      INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_kakao_id UNIQUE (kakao_id)
);

CREATE TABLE interview
(
    id                 BIGINT  NOT NULL AUTO_INCREMENT,
    created_at         DATETIME(6) NOT NULL,
    member_id          BIGINT  NOT NULL,
    root_question_id   BIGINT  NOT NULL,
    max_question_count INTEGER NOT NULL,
    total_score        INTEGER NULL,
    total_feedback     VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_interview_root_question FOREIGN KEY (root_question_id) REFERENCES root_question (id)
);

CREATE TABLE question
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    created_at   DATETIME(6) NOT NULL,
    interview_id BIGINT        NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_interview FOREIGN KEY (interview_id) REFERENCES interview (id)
);

CREATE TABLE answer
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6) NOT NULL,
    question_id BIGINT        NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    feedback    VARCHAR(2000) NOT NULL,
    answer_rank ENUM('A', 'B', 'C', 'D', 'F') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES question (id)
);
