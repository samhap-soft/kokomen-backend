-- token_purchase 테이블에 결제 방법 관련 컬럼 추가
ALTER TABLE token_purchase 
    ADD COLUMN payment_method VARCHAR(255) NOT NULL DEFAULT 'Unknown',
    ADD COLUMN easy_pay_provider VARCHAR(255) NULL;

-- 기존 데이터의 기본값 설정이 완료된 후 DEFAULT 제거 (다음 마이그레이션에서)
-- 새로 생성되는 데이터는 실제 값이 들어가야 하므로