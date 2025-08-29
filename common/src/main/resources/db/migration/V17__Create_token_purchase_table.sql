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
