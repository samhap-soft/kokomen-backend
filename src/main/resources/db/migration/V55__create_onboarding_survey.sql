-- 온보딩 설문. 회원 1명당 1행이며, 재제출 시 기존 행을 덮어쓴다.
-- 복수 선택 항목(prep_stages, tech_topics, weak_points)은 JSON 배열로 저장한다.
-- (resume_analysis의 JSON 컬럼 + AttributeConverter 패턴과 동일하다)
-- tech_topics는 별도 enum이 아니라 기존 Category enum 이름을 담는다. STACK 타입만 허용하며, 이 검증은 엔티티가 한다.
-- 테이블명은 엔티티명(OnboardingSurvey)의 snake_case와 반드시 일치해야 한다.
-- H2AutoIncrementCleaner(docs 프로파일)가 @Table을 보지 않고 엔티티명으로 테이블명을 추측하기 때문이다.
CREATE TABLE onboarding_survey
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    member_id            BIGINT        NOT NULL,
    career_goal          VARCHAR(30)   NOT NULL,
    prep_stages          JSON          NOT NULL,
    tech_topics          JSON          NOT NULL,
    target_company_type  VARCHAR(30)   NOT NULL,
    interview_experience VARCHAR(30)   NOT NULL,
    weak_points          JSON          NOT NULL,
    goal_description     VARCHAR(1000) NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- 회원 1명당 1행을 DB에서 보장한다. 동시 제출(더블 클릭)은 서비스의 @DistributedLock이 먼저 막고,
    -- 이 제약은 그 락을 우회한 경로에 대한 최종 방어선이다.
    -- leftmost가 member_id라서 아래 FK가 요구하는 인덱스도 이것이 겸한다.
    CONSTRAINT uk_onboarding_survey_member_id UNIQUE (member_id),
    CONSTRAINT fk_onboarding_survey_member FOREIGN KEY (member_id) REFERENCES member (id)
);
