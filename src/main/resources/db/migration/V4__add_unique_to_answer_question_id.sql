ALTER TABLE answer
    ADD CONSTRAINT uq_answer_question_id UNIQUE (question_id);
