ALTER TABLE root_question
    ADD COLUMN state ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE root_question
    ADD COLUMN question_order INT NULL;

ALTER TABLE root_question
    ADD CONSTRAINT uk_root_question_category_question_order UNIQUE (category, question_order);

ALTER TABLE root_question
    ALTER COLUMN state DROP DEFAULT;
