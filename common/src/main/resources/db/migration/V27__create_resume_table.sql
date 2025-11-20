CREATE TABLE member_resume
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    resume_url    VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE INDEX idx_member_resume_member_id ON member_resume(member_id);

CREATE INDEX idx_member_portfolio_member_id ON member_portfolio(member_id);
