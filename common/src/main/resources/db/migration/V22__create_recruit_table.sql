CREATE TABLE affiliate
(
    id    VARCHAR(255) NOT NULL,
    image VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE company
(
    id    VARCHAR(255) NOT NULL,
    name  VARCHAR(255) NOT NULL,
    image VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE recruit
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    affiliate_id  VARCHAR(255) NOT NULL,
    company_id    VARCHAR(255) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    end_date      DATETIME(6),
    deadline_type VARCHAR(50)  NOT NULL,
    career_min    INT,
    career_max    INT,
    url           VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recruit_affiliate FOREIGN KEY (affiliate_id) REFERENCES affiliate (id),
    CONSTRAINT fk_recruit_company FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE TABLE recruit_region
(
    recruit_id BIGINT       NOT NULL,
    region     VARCHAR(50)  NOT NULL,
    CONSTRAINT fk_recruit_region_recruit FOREIGN KEY (recruit_id) REFERENCES recruit (id)
);

CREATE TABLE recruit_employee_type
(
    recruit_id    BIGINT      NOT NULL,
    employee_type VARCHAR(50) NOT NULL,
    CONSTRAINT fk_recruit_employee_type_recruit FOREIGN KEY (recruit_id) REFERENCES recruit (id)
);

CREATE TABLE recruit_education
(
    recruit_id BIGINT      NOT NULL,
    education  VARCHAR(50) NOT NULL,
    CONSTRAINT fk_recruit_education_recruit FOREIGN KEY (recruit_id) REFERENCES recruit (id)
);

CREATE TABLE recruit_employment
(
    recruit_id BIGINT      NOT NULL,
    employment VARCHAR(50) NOT NULL,
    CONSTRAINT fk_recruit_employment_recruit FOREIGN KEY (recruit_id) REFERENCES recruit (id)
);

CREATE INDEX idx_recruit_deadline_type ON recruit (deadline_type);
CREATE INDEX idx_recruit_career_min ON recruit (career_min);
CREATE INDEX idx_recruit_career_max ON recruit (career_max);
CREATE INDEX idx_recruit_region_region ON recruit_region (region);
CREATE INDEX idx_recruit_employee_type_employee_type ON recruit_employee_type (employee_type);
CREATE INDEX idx_recruit_education_education ON recruit_education (education);
CREATE INDEX idx_recruit_employment_employment ON recruit_employment (employment);
