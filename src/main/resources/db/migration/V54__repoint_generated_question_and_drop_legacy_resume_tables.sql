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
-- 전제 2: V53이 컷오프('2026-08-15 00:00:00') 이전에 생성된 generated_question(구 플로우,
-- generation_id 부모)을 0행으로 만들었다. V53이 리뷰 라운드 1(Finding 1)에서 컷오프를 갖게 되면서
-- 이 전제의 의미가 "테이블 전체가 0행"에서 좁아졌다 -- 신규 플로우(analysis_id 부모)가 만든 행이나,
-- 배포가 컷오프보다 늦어져 컷오프 이후 생성된 구 플로우 잔존 행은 이 시점에 남아 있을 수 있다.
--   => MODIFY ... NOT NULL 이 이 전제에 대한 비침습적 assert 역할을 한다. analysis_id가 NULL인 행
--      (= 컷오프를 놓친 구 플로우 잔존 행)이 남아 있으면 ERROR 1138 (22004) Invalid use of NULL value
--      로 즉시 죽는다(실측). 신규 플로우 행은 애초에 analysis_id NOT NULL이므로 이 단언과 무관하게
--      항상 통과한다.
--
-- !! 비가역 !! resume_evaluation / resume_question_generation DROP에 역마이그레이션이 없다.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. MDL 대기 상한. lock_wait_timeout 기본값 31536000초(1년)를 짧게 잡는다.
--    ALTER가 메타데이터 락을 못 잡으면 조용히 매달리는 대신 ERROR 1205로 빠르게 실패해야 한다
--    -- 매달리면 MySQL의 MDL 큐가 FIFO이므로 generated_question에 대한 후속 SELECT/INSERT
--    전량이 그 뒤에 쌓여 면접 진행 API가 통째로 멈춘다.
--
--    !! 실패 시 "flyway repair 후 재시도"로는 복구되지 않는다 (리뷰 라운드 1, Finding 2) !!
--    아래 1~4번은 전부 비멱등 DDL이다. MySQL의 DROP CHECK/DROP FOREIGN KEY/DROP INDEX/DROP COLUMN은
--    IF EXISTS를 지원하지 않고, 각 문장은 개별 자동 커밋된다. 즉 예를 들어 2번(DROP FOREIGN KEY)에서
--    ERROR 1205로 실패하면 1번은 이미 커밋된 채로 남고, 그 상태에서 파일을 처음부터 다시 실행하면
--    1번이 즉시 ERROR 3940(이미 없는 CHECK를 다시 지우려는 시도)으로 죽는다 -- "flyway repair 후
--    재실행하면 수렴한다"는 여기서는 거짓이다. 이 방식이 통하는 것은 V53(모든 문장이 조건부 DML이라
--    재실행이 자연히 0행에 수렴)뿐이고, V54는 그렇지 않다.
--
--    실제 복구 절차:
--    (1) information_schema로 마지막으로 성공한 문장을 특정한다.
--        SELECT * FROM information_schema.table_constraints
--         WHERE table_schema=DATABASE() AND table_name='generated_question'
--           AND constraint_name='chk_generated_question_parent';           -- 1번 완료 여부
--        SELECT * FROM information_schema.table_constraints
--         WHERE table_schema=DATABASE() AND table_name='generated_question'
--           AND constraint_name='fk_gq_generation';                       -- 2번 완료 여부
--        SELECT * FROM information_schema.statistics
--         WHERE table_schema=DATABASE() AND table_name='generated_question'
--           AND index_name='idx_generated_question_generation_id';        -- 3번 완료 여부
--        SELECT * FROM information_schema.columns
--         WHERE table_schema=DATABASE() AND table_name='generated_question'
--           AND column_name='generation_id';                              -- 4번 완료 여부
--    (2) 남은 문장만, 이 파일에 쓰인 순서 그대로, 하나씩 손으로 실행해 스키마를 최종 상태로 맞춘다.
--        5번(MODIFY NOT NULL)은 이미 NOT NULL이면 재실행해도 무해하므로 이 순서를 정확히 지켰는지
--        불확실하면 5번부터는 그냥 다시 실행해도 안전하다.
--    (3) 6번(DROP TABLE)은 Finding 3 반영으로 IF EXISTS가 붙어 몇 번을 재실행해도 안전하다.
--    (4) 스키마가 최종 상태(§ 파일 하단 검증 쿼리)와 일치함을 확인한 뒤 `flyway repair`로 실패
--        기록을 제거하고, Flyway가 이 파일을 처음부터 다시 실행하지 않도록 `flyway_schema_history`에
--        V54 성공 행을 운영 표준 절차에 따라 수동으로 기입하거나(체크섬은 이 파일의 현재 체크섬과
--        일치시킨다) `flyway baseline -baselineVersion=54`로 기준선을 이 버전 이상으로 올린다 --
--        이 파일을 있는 그대로 Flyway가 처음부터 재실행하도록 두면 안 된다.
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
-- 5. analysis_id를 NOT NULL로 승격. V53이 컷오프 이전 구 플로우 행을 0개로 만들었으므로(전제 2)
--    깨끗하게 통과한다(실측).
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
--
--    IF EXISTS를 붙인다 (리뷰 라운드 1, Finding 3). 최초 작성 시에는 "테이블이 없으면 그것 자체가
--    조사할 이상 상태"라는 근거로 뺐지만, 이 플랜의 바로 앞 teardown 마이그레이션인 V52는 DROP TABLE
--    9번 전부에 IF EXISTS를 썼고(V36:17도 마찬가지) 이 두 줄만 어겼다 -- 같은 플랜의 같은 종류
--    마이그레이션 사이에 근거 없이 관례가 갈린 것이다. 두 teardown이 불일치할 때는 이미 확립된
--    쪽(IF EXISTS)을 따른다. 부작용도 이득이다: 이 파일이 (2)의 수동 복구 절차를 거쳐 재실행되거나,
--    두 테이블이 이미 손으로 제거된 환경에 적용되는 경우 모두 실패가 아니라 무해한 no-op이 된다.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS resume_question_generation;

DROP TABLE IF EXISTS resume_evaluation;
