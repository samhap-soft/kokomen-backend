-- ============================================================================
-- M2: 과거 이력서 기반 면접 기록 전량 삭제.
--
-- 이 파일은 순수 DML이다. DDL을 한 문장도 섞지 않는다 -- MySQL은 DDL에서 암묵 커밋하므로
-- DDL이 섞이면 이 블록의 원자성이 첫 DDL 지점에서 끊긴다. DDL은 V52·V54가 담당한다.
--
-- !! 비가역 !! 역마이그레이션이 없다. 적용 전 논리 백업과 §7 사전 점검 전량 통과가 필수다.
--
-- !! 재실행 안전성의 범위 (리뷰 라운드 1, Finding 1 반영) !!
-- 결정 M4에 따라 신규 이력서 분석 플로우도 Interview(Member, GeneratedQuestion, Integer, InterviewMode)
-- 생성자를 그대로 써서 interview_type='RESUME_BASED'인 행을 만든다(Interview.java는 이 태스크에서
-- 바이트 단위로 무수정 -- Interview.java:132-137). 즉 interview_type = 'RESUME_BASED' 만으로는
-- "구 질문생성 플로우가 만든 행"과 "신규 이력서 분석 플로우가 만든 행"을 구별할 수 없다.
--
-- 원래 버전(컷오프 없음)은 "이 파일을 언제 다시 돌려도 구 플로우 잔존물만 지운다"고 주장했지만,
-- 그 주장은 신규 플로우가 존재하지 않던 시점에만 성립했다. 신규 플로우가 서비스를 시작한 뒤
-- 이 파일이 재실행되면(예: 운영자가 flyway_schema_history를 손으로 되돌리고 재적용하거나, 복구 절차를
-- 오해해 이 스크립트를 raw SQL로 직접 재실행하는 경우) 컷오프 없는 WHERE절은 신규 플로우가 방금 만든
-- 살아있는 면접·질문까지 조용히 지운다 -- interview_type 값만 보면 구분이 안 되기 때문이다.
--
-- 그래서 모든 DELETE에 고정 컷오프 시점(아래 CUTOFF)을 리터럴로 박아 넣었다. NOW()를 쓰지 않는 이유가
-- 핵심이다: NOW()는 재실행 시점마다 갱신되어 "지금 이전에 생성된 건 전부 구 데이터"라는, 방금 고친
-- 그 잘못된 가정을 매 재실행마다 다시 만들어낸다. 리터럴 상수는 재실행 시점과 무관하게 항상 같은
-- 절대 시점을 가리키므로, 이 시점 이후에 created_at을 가진 행은 이 파일이 몇 번이든, 언제 재실행되든
-- 항상 보존된다 -- "재실행이 신규 플로우 데이터를 파괴할 수 없다"가 이 값의 존재 이유다.
--
--   CUTOFF = '2026-08-15 00:00:00' (이 리전 서버 타임존, Asia/Seoul 기준)
--
-- 이 값을 고르는 원칙: 짧을수록 안전하다. 컷오프는 (a) 이 배포가 실제로 각 환경에 반영되는 시점보다는
-- 뒤여야 하고(그래야 구 플로우가 실제로 만들어 둔 마지막 행까지 첫 실행에서 잡힌다) (b) 신규 플로우가
-- 실제 트래픽을 받기 시작하는 시점보다는 한참 앞이어야 한다(그래야 미래의 재실행이 안전하다). (b)의
-- 위반이 (a)의 위반보다 훨씬 위험하다 -- (a)를 놓치면 구 데이터 일부가 안 지워진 채 남을 뿐이지만
-- (b)를 놓치면 신규 데이터가 지워진다. 그래서 짧은 쪽(이 커밋의 리뷰·머지·배포에 필요한 정도의 여유,
-- 약 2주)으로 잡았다. 신규 이력서 분석 질문생성 플로우는 이 태스크 시점에 아직 착수되지 않은 별도
-- 태스크(9개 남음)의 산물이므로 2주 안에 실제 트래픽을 받을 일은 없다고 판단했다. 배포가 이 값보다
-- 늦어지면 그 사이에 생성된 "진짜 구 플로우" 잔존 데이터가 첫 실행에서 빠질 수 있다 -- 파괴적 문제가
-- 아니라 완전성 문제이며, 필요하면 이 리터럴을 배포 시점 이후로 올리고 재적용하면 회복된다.
--
-- 모든 DELETE는 멱등이다(같은 WHERE를 다시 돌리면 0행 삭제). 중간 실패 시
-- flyway repair 후 이 파일을 재실행하면 수렴한다. 안전성 근거를 트랜잭션에 두지 않는 이유는
-- MySQL에서 Flyway가 순수 DML 마이그레이션을 단일 트랜잭션으로 감싸는지 미확인이기 때문이다.
--
-- 컷오프를 세션 변수(SET @cutoff = ...)로 한 번만 선언하지 않고 매 문장에 리터럴로 반복해 넣은 것도
-- 의도다 -- 세션 변수는 이 파일의 모든 문장이 "같은 커넥션"에서 실행된다는 전제에 의존한다. Flyway의
-- 정상 실행 경로는 그 전제를 지키지만(파일 하나당 커넥션 하나), 풀링된 JdbcTemplate으로 문장을 하나씩
-- 재생하는 도구(예: 이 파일을 검증하는 테스트)는 문장마다 다른 커넥션을 빌려올 수 있어 세션 변수가
-- 중간에 사라질 수 있다. 리터럴 반복은 그 가정 자체를 없애 재생 방식과 무관하게 항상 같은 값으로 동작한다.
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
--    컷오프는 답변/좋아요/메모 자신의 created_at이 아니라 그 조상 interview의 created_at으로 건다 --
--    "이 면접이 구 플로우 산물인가"가 삭제 여부를 결정하는 유일한 기준이기 때문이다.
-- ---------------------------------------------------------------------------
DELETE FROM answer_like
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED'
                      AND i.created_at < '2026-08-15 00:00:00');

DELETE FROM answer_memo
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED'
                      AND i.created_at < '2026-08-15 00:00:00');

-- ---------------------------------------------------------------------------
-- 2. answer
-- ---------------------------------------------------------------------------
DELETE FROM answer
WHERE question_id IN (SELECT q.id
                      FROM question q
                               JOIN interview i ON i.id = q.interview_id
                      WHERE i.interview_type = 'RESUME_BASED'
                        AND i.created_at < '2026-08-15 00:00:00');

-- ---------------------------------------------------------------------------
-- 3. question
-- ---------------------------------------------------------------------------
DELETE FROM question
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED'
                         AND i.created_at < '2026-08-15 00:00:00');

-- ---------------------------------------------------------------------------
-- 4. interview_like
-- ---------------------------------------------------------------------------
DELETE FROM interview_like
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED'
                         AND i.created_at < '2026-08-15 00:00:00');

-- ---------------------------------------------------------------------------
-- 5. interview (RESUME_BASED, 컷오프 이전 생성분만). interview_type은 VARCHAR(50)이고
--    idx_interview_interview_type(V33)이 있어 등가 조회가 인덱스를 탄다.
--
--    WHERE에 `OR generated_question_id IS NOT NULL`을 붙이지 않는다 -- 그것은 M2가 정의한
--    삭제 범위(interview_type='RESUME_BASED'와 그 후손)를 넘어 다른 타입의 면접을 조용히 지운다.
--
--    created_at < '2026-08-15 00:00:00' 컷오프가 신규 플로우 보호의 핵심이다 -- interview_type만으로는
--    신규/구 플로우를 구별할 수 없으므로(파일 선두 주석 참조), 이 컷오프 이후에 생성된 RESUME_BASED
--    행은 이 문장이 몇 번을 재실행되든 항상 살아남는다.
-- ---------------------------------------------------------------------------
DELETE FROM interview
WHERE interview_type = 'RESUME_BASED'
  AND created_at < '2026-08-15 00:00:00';

-- ---------------------------------------------------------------------------
-- 6. generated_question. 컷오프 이전 생성분만 -- 전체 삭제(WHERE 없음)였던 원래 버전은 신규
--    플로우(analysis_id 부모)가 만든 행까지 지웠다(Finding 1). interview 경유로 컷오프를 걸 수 없는
--    이유: 이 테이블의 행은 interview 없이도 존재할 수 있다(이력서 분석 1건이 후보 질문 5~7개를
--    만들고 사용자가 그중 1개로만 면접을 시작하면, 나머지는 영원히 interview와 연결되지 않는다).
--    그래서 이 테이블 자신의 created_at으로 직접 판단한다.
--
--    TRUNCATE를 쓰지 않는다: interview.generated_question_id의 inbound FK 때문에
--    ERROR 1701로 죽고(실측), TRUNCATE는 DDL이라 암묵 커밋도 일으킨다.
--
--    fk_interview_generated_question은 ON DELETE 절이 없어 RESTRICT(delete_rule = NO ACTION,
--    실측)다. 5단계 이후에도 이 컷오프 이전 generated_question을 참조하는 interview 행이 남아 있으면
--    이 문장이 ERROR 1451로 죽는다 -- "컷오프 이전 RESUME_BASED 면접은 5단계에서 전부 지워졌어야
--    한다"에 대한 DB 레벨 자동 검증이며, §7-D 사전 점검과 이중 방어를 이룬다. (컷오프 이후 생성된
--    generated_question은 그 자신이 이 문장의 WHERE를 통과하지 않으므로 이 검증 대상이 아니다.)
--
--    이 문장이 V54의 MODIFY analysis_id NOT NULL 을 가능하게 만든다 -- 단, 그 전제(0행)의 의미가
--    이제 좁아졌다: "컷오프 이전에 생성된 구 플로우(generation_id 부모) 행이 0개"이지, "테이블 전체가
--    0행"이 아니다. 배포가 컷오프보다 늦어져 구 플로우 행이 created_at >= 컷오프로 남아 있으면 이
--    문장은 그 행을 지우지 못하고, V54의 MODIFY는 그 행에서 ERROR 1138(analysis_id가 NULL)로 죽는다 --
--    이것도 침묵이 아니라 실패이므로 받아들일 수 있는 결과다(파일 선두 주석의 (a) 위반 시나리오).
-- ---------------------------------------------------------------------------
DELETE FROM generated_question WHERE created_at < '2026-08-15 00:00:00';
