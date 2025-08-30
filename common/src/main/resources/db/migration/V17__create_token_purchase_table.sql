CREATE TABLE token_purchase (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    payment_key VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    total_amount BIGINT NOT NULL,
    order_name VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    count INT NOT NULL,
    unit_price BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE INDEX idx_token_purchase_payment_key ON token_purchase(payment_key);

CREATE TABLE token
(
    id          BIGINT                NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    member_id   BIGINT                NOT NULL,
    type        ENUM('FREE', 'PAID')  NOT NULL,
    token_count INTEGER               NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_token_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT uk_token_member_type UNIQUE (member_id, type)
);

INSERT INTO token (created_at, member_id, type, token_count)
SELECT
    NOW(),
    id,
    'FREE',
    20
FROM member;

INSERT INTO token (created_at, member_id, type, token_count)
SELECT
    NOW(),
    id,
    'PAID',
    0
FROM member;

ALTER TABLE member DROP COLUMN free_token_count;
