-- ============================================================================
-- M5: recruit 도메인 완전 제거. 크롤링 서비스가 중단되어 코드 참조 0건이 되었다.
--
-- 패키지 밖 참조 0건 검증됨(AwsConfig의 Region은 software.amazon.awssdk.regions.Region으로 무관).
--
-- !! 비가역 !! 역마이그레이션이 없다. 재크롤링도 RecruitmentScheduler가 삭제되어 불가하다.
--
-- DROP 순서는 자식부터다. recruit를 참조하는 inbound FK는 M5가 센 4개가 아니라 5개다
-- (information_schema.key_column_usage 실측):
--   recruit_region.recruit_id        [fk_recruit_region_recruit]
--   recruit_employee_type.recruit_id [fk_recruit_employee_type_recruit]
--   recruit_education.recruit_id     [fk_recruit_education_recruit]
--   recruit_employment.recruit_id    [fk_recruit_employment_recruit]
--   ocr_waiting_list.recruit_id      [fk_ocr_recruit]   <-- V25:7 생성, V26에서 개명
--
-- ocr_waiting_list를 함께 지운다: Java 엔티티·리포지토리·픽스처가 0건인 고아 테이블이고
-- recruit 기업 이미지 OCR 대기열이라는 유일한 용도가 recruit와 함께 사라진다.
-- 남기면 DROP TABLE recruit이 ERROR 3730으로 죽는다.
-- (M5 목록의 집계 누락이며 M5의 결정이 아니다. 변형 B는 이 파일 하단 주석 참조.)
--
-- crawling_request는 FK가 전혀 없는 독립 테이블이지만 recruit 크롤링 전용이므로 같이 지운다.
-- 역시 Java 엔티티 0건(실측).
--
-- affiliate/company에 대한 inbound FK는 fk_recruit_affiliate/fk_recruit_company(V22:34-35)
-- 둘뿐이며 recruit DROP으로 함께 사라진다.
--
-- 기존 마이그레이션 V22·V24·V25·V26은 지우지 않는다. 이 파일이 DROP을 담당한다.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. recruit의 자식 5개. 서로 독립이므로 이 5개 사이의 순서는 무관하다.
-- ---------------------------------------------------------------------------
DROP TABLE recruit_education;
DROP TABLE recruit_employee_type;
DROP TABLE recruit_employment;
DROP TABLE recruit_region;
DROP TABLE ocr_waiting_list;

-- ---------------------------------------------------------------------------
-- 2. recruit. 이 시점에 inbound FK가 0이다.
-- ---------------------------------------------------------------------------
DROP TABLE recruit;

-- ---------------------------------------------------------------------------
-- 3. recruit의 부모 2개. recruit가 사라졌으므로 inbound FK가 0이다.
-- ---------------------------------------------------------------------------
DROP TABLE affiliate;
DROP TABLE company;

-- ---------------------------------------------------------------------------
-- 4. crawling_request. FK 없음, 독립.
-- ---------------------------------------------------------------------------
DROP TABLE crawling_request;
