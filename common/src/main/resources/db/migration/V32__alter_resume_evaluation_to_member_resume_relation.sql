-- 기존 TEXT 컬럼 삭제
ALTER TABLE resume_evaluation DROP COLUMN resume;
ALTER TABLE resume_evaluation DROP COLUMN portfolio;

-- 새 외래 키 컬럼 추가
ALTER TABLE resume_evaluation ADD COLUMN member_resume_id BIGINT;
ALTER TABLE resume_evaluation ADD COLUMN member_portfolio_id BIGINT;

-- 외래 키 제약조건 추가
ALTER TABLE resume_evaluation
    ADD CONSTRAINT fk_resume_evaluation_member_resume
    FOREIGN KEY (member_resume_id) REFERENCES member_resume(id);

ALTER TABLE resume_evaluation
    ADD CONSTRAINT fk_resume_evaluation_member_portfolio
    FOREIGN KEY (member_portfolio_id) REFERENCES member_portfolio(id);
