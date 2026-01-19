-- AI가 자율적으로 질문 개수를 결정하도록 변경
-- question_count 컬럼 제거
ALTER TABLE resume_question_generation DROP COLUMN question_count;
