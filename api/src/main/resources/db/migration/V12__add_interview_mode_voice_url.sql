ALTER TABLE interview
    ADD COLUMN interview_mode ENUM('TEXT', 'VOICE') NOT NULL DEFAULT 'TEXT';

ALTER TABLE interview
    ALTER COLUMN interview_mode DROP DEFAULT;
