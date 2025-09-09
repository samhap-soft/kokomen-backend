-- 소셜로그인 테이블 생성
CREATE TABLE member_social_login
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    member_id  BIGINT       NOT NULL,
    provider   VARCHAR(255) NOT NULL,
    social_id  VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_social_login_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT uk_member_social_login_provider_social_id UNIQUE (provider, social_id)
);

-- 인덱스 생성
CREATE INDEX idx_member_social_login_member_id ON member_social_login (member_id);

-- 기존 member 테이블의 kakao_id 데이터를 member_social_login 테이블로 이전
INSERT INTO member_social_login (created_at, member_id, provider, social_id)
SELECT created_at, id, 'KAKAO', CAST(kakao_id AS CHAR)
FROM member
WHERE kakao_id IS NOT NULL;

-- member 테이블에서 kakao_id 컬럼 제거 및 unique 제약조건 제거
ALTER TABLE member DROP CONSTRAINT uk_member_kakao_id;
ALTER TABLE member DROP COLUMN kakao_id;