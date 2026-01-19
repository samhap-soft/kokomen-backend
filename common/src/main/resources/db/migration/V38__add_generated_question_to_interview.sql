-- root_question_id를 nullable로 변경 (이력서 기반 인터뷰용)
ALTER TABLE interview MODIFY COLUMN root_question_id BIGINT NULL;

-- generated_question_id 컬럼 추가
ALTER TABLE interview ADD COLUMN generated_question_id BIGINT NULL;

-- FK 제약조건 추가
ALTER TABLE interview ADD CONSTRAINT fk_interview_generated_question
    FOREIGN KEY (generated_question_id) REFERENCES generated_question(id);

-- 인덱스 추가
CREATE INDEX idx_interview_generated_question_id ON interview(generated_question_id);
