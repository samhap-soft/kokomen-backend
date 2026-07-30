-- ============================================================================
-- M2: 과거 이력서 기반 면접 기록 전량 삭제.
--
-- 이 파일은 순수 DML이다. DDL을 한 문장도 섞지 않는다 -- MySQL은 DDL에서 암묵 커밋하므로
-- DDL이 섞이면 이 블록의 원자성이 첫 DDL 지점에서 끊긴다. DDL은 V52·V54가 담당한다.
--
-- !! 비가역 !! 역마이그레이션이 없다. 적용 전 논리 백업과 §7 사전 점검 전량 통과가 필수다.
--
-- 모든 DELETE는 멱등이다(같은 WHERE를 다시 돌리면 0행 삭제). 중간 실패 시
-- flyway repair 후 이 파일을 재실행하면 수렴한다. 안전성 근거를 트랜잭션에 두지 않는 이유는
-- MySQL에서 Flyway가 순수 DML 마이그레이션을 단일 트랜잭션으로 감싸는지 미확인이기 때문이다.
--
-- 삭제 순서는 자식부터다. InnoDB의 FK 검사는 문장 단위 즉시 검사이며 지연(deferred)이 없으므로
-- 순서를 바꾸면 ERROR 1451(Cannot delete or update a parent row)로 죽는다.
--
-- 검증된 연쇄 트리 (information_schema.key_column_usage + 전 마이그레이션 FK 전수 추출, 누락 0):
--     interview ├─ interview_like (interview_id)              [V6]
--               └─ question (interview_id)                    [V1]
--                    └─ answer (question_id)                  [V1]
--                         ├─ answer_like (answer_id)
--                         └─ answer_memo (answer_id)
--     generated_question <- interview.generated_question_id    [V38]
--     (resume_based_root_question[V33]은 V36:17에서 이미 DROP됐다)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 락 범위 축소 및 실패 시점 단축.
--
--    innodb_lock_wait_timeout 기본값은 50초, lock_wait_timeout(MDL) 기본값은 31536000초(1년)다.
--    걸어 두지 않으면 락 대기가 사실상 무한이 되어 뒤따르는 모든 쿼리가 큐에 쌓인다.
--
--    아래 DELETE들은 서브쿼리 소스 테이블(interview / question / answer)의 인덱스 구간에
--    공유 넥스트키 락을 건다(performance_schema.data_locks 실측: 소스 테이블에 S 락 3건,
--    대상 테이블에 X,GAP 2건). READ COMMITTED로 내리면 갭 락이 사라진다
--    (X,GAP -> X,REC_NOT_GAP, S -> S,REC_NOT_GAP).
--
--    !! 확인 필요 !! MySQL 문서에 따르면 SET SESSION TRANSACTION ISOLATION LEVEL은
--    트랜잭션 내에서 허용되지만 "현재 진행 중인 트랜잭션에는 영향을 주지 않는다".
--    Flyway가 이 파일을 트랜잭션으로 감싸면 이 문장은 무효가 된다. 따라서 락 문제의
--    1차 통제 수단은 이 문장이 아니라 "적용 시점에 구 인스턴스 0대"(유지보수 창)다.
--    isolation을 확실히 낮추려면 마이그레이션 전용 배포의 JDBC URL에
--    sessionVariables=transaction_isolation='READ-COMMITTED' 를 붙인다.
-- ---------------------------------------------------------------------------
SET SESSION innodb_lock_wait_timeout = 10;
SET SESSION lock_wait_timeout = 15;
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- ---------------------------------------------------------------------------
-- 1. answer_like / answer_memo — 손자 세대. 서로 독립이므로 이 둘의 순서는 무관하다.
-- ---------------------------------------------------------------------------
DELETE FROM answer_like
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED');

DELETE FROM answer_memo
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 2. answer
-- ---------------------------------------------------------------------------
DELETE FROM answer
WHERE question_id IN (SELECT q.id
                      FROM question q
                               JOIN interview i ON i.id = q.interview_id
                      WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 3. question
-- ---------------------------------------------------------------------------
DELETE FROM question
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 4. interview_like
-- ---------------------------------------------------------------------------
DELETE FROM interview_like
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED');

-- ---------------------------------------------------------------------------
-- 5. interview (RESUME_BASED). interview_type은 VARCHAR(50)이고
--    idx_interview_interview_type(V33)이 있어 등가 조회가 인덱스를 탄다.
--
--    WHERE에 `OR generated_question_id IS NOT NULL`을 붙이지 않는다 -- 그것은 M2가 정의한
--    삭제 범위(interview_type='RESUME_BASED'와 그 후손)를 넘어 다른 타입의 면접을 조용히 지운다.
--    RESUME_BASED가 아닌데 generated_question을 물고 있는 행이 있다면 6단계가 ERROR 1451로
--    죽는 것이 옳다(§7-D 사전 점검 쿼리 (8)이 그것을 미리 알려준다).
-- ---------------------------------------------------------------------------
DELETE FROM interview
WHERE interview_type = 'RESUME_BASED';

-- ---------------------------------------------------------------------------
-- 6. generated_question 전량.
--    WHERE가 없는 것은 의도다 -- 이 테이블의 모든 행은 구 resume_question_generation 플로우의
--    산물이고 그 부모 테이블은 V54에서 DROP된다. 남길 행이 정의상 0이다.
--
--    TRUNCATE를 쓰지 않는다: interview.generated_question_id의 inbound FK 때문에
--    ERROR 1701로 죽고(실측), TRUNCATE는 DDL이라 암묵 커밋도 일으킨다.
--
--    fk_interview_generated_question은 ON DELETE 절이 없어 RESTRICT(delete_rule = NO ACTION,
--    실측)다. 5단계 이후에도 generated_question을 참조하는 interview 행이 남아 있으면
--    이 문장이 ERROR 1451로 죽는다 -- 이것이 "RESUME_BASED 이외의 면접은 generated_question을
--    참조하지 않는다"에 대한 DB 레벨 자동 검증이며, §7-D 사전 점검과 이중 방어를 이룬다.
--
--    이 문장이 V54의 MODIFY analysis_id NOT NULL 을 가능하게 만든다.
-- ---------------------------------------------------------------------------
DELETE FROM generated_question;
