-- Interview 테이블에서 이력서 기반 면접 관련 컬럼 제거
-- (ResumeQuestionGeneration으로 이동됨)

-- 외래 키 제약조건 제거
ALTER TABLE interview DROP FOREIGN KEY fk_interview_member_resume;
ALTER TABLE interview DROP FOREIGN KEY fk_interview_member_portfolio;

-- 컬럼 제거
ALTER TABLE interview DROP COLUMN member_resume_id;
ALTER TABLE interview DROP COLUMN member_portfolio_id;
ALTER TABLE interview DROP COLUMN job_career;

-- interview_state ENUM에서 질문 생성 관련 상태 제거 (IN_PROGRESS, FINISHED만 유지)
ALTER TABLE interview MODIFY COLUMN interview_state ENUM('IN_PROGRESS', 'FINISHED') NOT NULL;

-- resume_based_root_question 테이블 제거 (generated_question으로 대체됨)
DROP TABLE IF EXISTS resume_based_root_question;
