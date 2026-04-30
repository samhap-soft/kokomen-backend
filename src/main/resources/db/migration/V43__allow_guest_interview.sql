ALTER TABLE interview
    MODIFY COLUMN member_id BIGINT NULL;

ALTER TABLE interview
    ADD COLUMN guest_ip VARCHAR(45) NULL;

CREATE INDEX idx_interview_guest_ip ON interview (guest_ip);
