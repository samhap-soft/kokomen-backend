ALTER TABLE interview
    ADD COLUMN finished_at DATETIME;

UPDATE interview
SET finished_at = created_at
WHERE interview_state = 'FINISHED';
