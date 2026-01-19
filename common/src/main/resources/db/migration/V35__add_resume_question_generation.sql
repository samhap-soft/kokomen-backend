-- 질문 생성 요청 테이블 생성
CREATE TABLE resume_question_generation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    member_resume_id BIGINT,
    member_portfolio_id BIGINT,
    job_career VARCHAR(100),
    question_count INT NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_rqg_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_rqg_resume FOREIGN KEY (member_resume_id) REFERENCES member_resume(id),
    CONSTRAINT fk_rqg_portfolio FOREIGN KEY (member_portfolio_id) REFERENCES member_portfolio(id)
);

CREATE INDEX idx_resume_question_generation_member_id ON resume_question_generation(member_id);

-- 생성된 질문 테이블 생성
CREATE TABLE generated_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    reason VARCHAR(1000),
    question_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_gq_generation FOREIGN KEY (generation_id) REFERENCES resume_question_generation(id)
);

CREATE INDEX idx_generated_question_generation_id ON generated_question(generation_id);
