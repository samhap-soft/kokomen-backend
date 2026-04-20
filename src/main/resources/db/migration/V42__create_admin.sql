CREATE TABLE admin
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_member FOREIGN KEY (member_id) REFERENCES member (id)
);
