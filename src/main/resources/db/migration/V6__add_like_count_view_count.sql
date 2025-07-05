CREATE TABLE interview_like
(
    id           BIGINT NOT NULL AUTO_INCREMENT,
    member_id    BIGINT NOT NULL,
    interview_id BIGINT NOT NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP (6),
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_like_interview_member UNIQUE (interview_id, member_id),
    CONSTRAINT fk_interview_like_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_interview_like_interview FOREIGN KEY (interview_id) REFERENCES interview (id)
);

CREATE TABLE answer_like
(
    id         BIGINT NOT NULL AUTO_INCREMENT,
    member_id  BIGINT NOT NULL,
    answer_id  BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP (6),
    PRIMARY KEY (id),
    CONSTRAINT uk_answer_like_answer_member UNIQUE (answer_id, member_id),
    CONSTRAINT fk_answer_like_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_answer_like_answer FOREIGN KEY (answer_id) REFERENCES answer (id)
);


ALTER TABLE interview
    ADD COLUMN like_count INT NOT NULL DEFAULT 0;

ALTER TABLE interview
    ADD COLUMN view_count INT NOT NULL DEFAULT 0;

ALTER TABLE answer
    ADD COLUMN like_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_interview_like_count ON interview (like_count);
CREATE INDEX idx_interview_view_count ON interview (view_count);
CREATE INDEX idx_answer_like_count ON answer (like_count);
