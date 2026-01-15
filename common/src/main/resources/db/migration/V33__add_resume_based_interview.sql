-- Interview 테이블에 이력서 기반 면접을 위한 컬럼 추가
-- interview_state ENUM에 PENDING 추가 (질문 생성 후, 면접 시작 전 상태)
ALTER TABLE interview MODIFY COLUMN interview_state ENUM('PENDING', 'IN_PROGRESS', 'FINISHED') NOT NULL;

ALTER TABLE interview ADD COLUMN interview_type VARCHAR(50) NOT NULL DEFAULT 'CATEGORY_BASED';
ALTER TABLE interview ADD COLUMN member_resume_id BIGINT NULL;
ALTER TABLE interview ADD COLUMN member_portfolio_id BIGINT NULL;
ALTER TABLE interview ADD COLUMN job_career VARCHAR(100) NULL;

-- 외래 키 제약조건 추가
ALTER TABLE interview ADD CONSTRAINT fk_interview_member_resume
    FOREIGN KEY (member_resume_id) REFERENCES member_resume(id);
ALTER TABLE interview ADD CONSTRAINT fk_interview_member_portfolio
    FOREIGN KEY (member_portfolio_id) REFERENCES member_portfolio(id);

-- root_question_id를 nullable로 변경 (이력서 기반 면접에서는 사용하지 않음)
ALTER TABLE interview MODIFY COLUMN root_question_id BIGINT NULL;

-- 이력서 기반 루트 질문 테이블 생성
CREATE TABLE resume_based_root_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    reason VARCHAR(1000) NULL,
    question_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_resume_based_root_question_interview
        FOREIGN KEY (interview_id) REFERENCES interview(id)
);

-- 인덱스 추가
CREATE INDEX idx_resume_based_root_question_interview_id ON resume_based_root_question(interview_id);
CREATE INDEX idx_interview_interview_type ON interview(interview_type);
