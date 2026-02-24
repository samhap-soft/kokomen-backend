ALTER TABLE interview
    ADD CONSTRAINT chk_interview_like_count_non_negative CHECK (like_count >= 0);

ALTER TABLE answer
    ADD CONSTRAINT chk_answer_like_count_non_negative CHECK (like_count >= 0);
