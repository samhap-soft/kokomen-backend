CREATE TABLE ocr_wating_list
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    recruit_id BIGINT NOT NULL,
    image_url    VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ocr_recruit FOREIGN KEY (recruit_id) REFERENCES recruit (id)
);

CREATE TABLE member_portfolio
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    portfolio_url    VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_member FOREIGN KEY (member_id) REFERENCES member (id)
);
