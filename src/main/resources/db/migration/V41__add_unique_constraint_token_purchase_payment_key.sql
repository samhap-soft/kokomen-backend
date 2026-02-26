ALTER TABLE token_purchase
    DROP INDEX idx_token_purchase_payment_key,
    ADD CONSTRAINT uk_token_purchase_payment_key UNIQUE (payment_key);
