-- answer.content 폭 복원 (V46 역적용): VARCHAR(10000) -> VARCHAR(2000)
-- 주의: V46 확장은 모든 답변에 적용됐으므로 dev 적용 전 2000자 초과 답변 존재 여부를 점검할 것.
--   SELECT COUNT(*) FROM answer WHERE CHAR_LENGTH(content) > 2000;  (V47 적용 후 실행)
ALTER TABLE answer
    MODIFY COLUMN content VARCHAR(2000) NOT NULL;
