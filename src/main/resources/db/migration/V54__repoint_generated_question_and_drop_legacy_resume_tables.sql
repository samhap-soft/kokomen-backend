-- ============================================================================
-- M3: generated_question을 단일 부모(resume_analysis) 구조로 전환.
-- M1(teardown side): 구 테이블 2개 DROP.
--
-- 전제 (반드시 확인): V51이 이미 아래를 만들어 두었다.
--     analysis_id BIGINT NULL / idx_generated_question_analysis_id
--     fk_generated_question_analysis / chk_generated_question_parent
--     그리고 generation_id를 NULL 허용으로 완화했다.
--   => 이 파일은 컬럼·인덱스·FK를 다시 만들지 않는다. 다시 만들면 각각
--      ERROR 1060(Duplicate column) / 1061(Duplicate key) / 1826(Duplicate foreign key)로 죽는다(실측).
--   => 남은 일은 (a) 구 부모 제거 (b) XOR CHECK 제거 (c) analysis_id NOT NULL 승격뿐이다.
--
-- 전제 2: V53이 generated_question을 0행으로 만들었다.
--   => MODIFY ... NOT NULL 이 이 전제에 대한 비침습적 assert 역할을 한다. 잔존 행이 있으면
--      ERROR 1138 (22004) Invalid use of NULL value 로 즉시 죽는다(실측).
--
-- !! 비가역 !! resume_evaluation / resume_question_generation DROP에 역마이그레이션이 없다.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. MDL 대기 상한. lock_wait_timeout 기본값 31536000초(1년)를 짧게 잡는다.
--    ALTER가 메타데이터 락을 못 잡으면 조용히 매달리는 대신 ERROR 1205로 빠르게 실패해야 한다
--    -- 매달리면 MySQL의 MDL 큐가 FIFO이므로 generated_question에 대한 후속 SELECT/INSERT
--    전량이 그 뒤에 쌓여 면접 진행 API가 통째로 멈춘다.
--    실패 시 flyway_schema_history에 success=0이 남으므로 flyway repair 후 재시도한다.
-- ---------------------------------------------------------------------------
SET SESSION lock_wait_timeout = 15;
SET SESSION innodb_lock_wait_timeout = 10;

-- ---------------------------------------------------------------------------
-- 1. XOR CHECK를 가장 먼저 지운다. 이 문장 없이 4번을 시도하면 죽는다(실측 8.4.5):
--      ERROR 3959 (HY000): Check constraint 'chk_generated_question_parent' uses column
--                          'generation_id', hence column cannot be dropped or renamed.
--    M3의 "XOR CHECK 불필요"가 최종 스키마에서 실현되는 지점이다.
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question DROP CHECK chk_generated_question_parent;

-- ---------------------------------------------------------------------------
-- 2. 구 부모 FK 분리. FK -> 인덱스 -> 컬럼 순서를 지킨다.
--    InnoDB는 FK가 참조하는 인덱스·컬럼의 선삭제를 거부한다(errno 150).
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question DROP FOREIGN KEY fk_gq_generation;

-- ---------------------------------------------------------------------------
-- 3. generation_id 위의 인덱스. 단일 컬럼 인덱스이므로 DROP COLUMN이 자동으로 함께 없애지만,
--    암묵 동작에 의존하지 않고 명시적으로 지운다.
--    (information_schema.statistics 실측: PRIMARY / idx_..._generation_id / idx_..._analysis_id)
-- ---------------------------------------------------------------------------
DROP INDEX idx_generated_question_generation_id ON generated_question;

-- ---------------------------------------------------------------------------
-- 4. 구 부모 컬럼 제거.
--    ALTER를 한 문장으로 합치지 않는다 -- 8.4.5에서는 합쳐도 동작하지만(실측) 실패 지점을
--    문장 단위로 특정할 수 있어야 부분 적용 복구가 쉽다.
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question DROP COLUMN generation_id;

-- ---------------------------------------------------------------------------
-- 5. analysis_id를 NOT NULL로 승격. V53이 0행으로 만들었으므로 깨끗하게 통과한다(실측).
--    ADD COLUMN ... NOT NULL 을 쓰지 않는 이유는 여기서 컬럼을 새로 만들지 않기 때문이며,
--    설령 새로 만든다 해도 비어 있지 않은 테이블에서는 경고도 에러도 없이 모든 행을 0으로
--    채워 FK 추가가 ERROR 1452로 늦게 죽는다(실측, sql_mode에 STRICT_TRANS_TABLES 포함).
--
--    ON DELETE CASCADE는 V51에서도 걸지 않았다 -- interview.generated_question_id가 이 행을
--    참조하므로 삭제는 항상 명시적·검증적이어야 한다.
-- ---------------------------------------------------------------------------
ALTER TABLE generated_question MODIFY COLUMN analysis_id BIGINT NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. 구 테이블 DROP.
--    resume_question_generation: inbound FK는 fk_gq_generation 1개뿐이었고 2단계에서 제거됐다.
--    resume_evaluation: inbound FK 0건(information_schema 실측). 바로 DROP된다.
--    IF EXISTS를 쓰지 않는다 -- 테이블이 없다면 그것 자체가 조사해야 할 이상 상태다.
-- ---------------------------------------------------------------------------
DROP TABLE resume_question_generation;

DROP TABLE resume_evaluation;
