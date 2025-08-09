ALTER TABLE interview
    ADD COLUMN interview_mode ENUM('TEXT', 'VOICE') NOT NULL DEFAULT 'TEXT';

ALTER TABLE interview
    ALTER COLUMN interview_mode DROP DEFAULT;

ALTER TABLE root_question
    ADD COLUMN voice_url VARCHAR(1000) NOT NULL DEFAULT 'not yet';

ALTER TABLE root_question
    ALTER COLUMN voice_url DROP DEFAULT;
