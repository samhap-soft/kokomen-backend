ALTER TABLE recruit
    ADD COLUMN content TEXT NULL,
    ADD COLUMN apply_url VARCHAR(500) NULL;

CREATE TABLE crawling_request (
      id BIGINT NOT NULL AUTO_INCREMENT,
      crawled_date DATE NOT NULL,
      request_count INT NOT NULL DEFAULT 0,
      PRIMARY KEY (id)
);
