-- ============================================================================
-- M2: 과거 이력서 기반 면접 기록 전량 삭제.
--
-- 이 파일은 순수 DML이다. DDL을 한 문장도 섞지 않는다 -- MySQL은 DDL에서 암묵 커밋하므로
-- DDL이 섞이면 이 블록의 원자성이 첫 DDL 지점에서 끊긴다. DDL은 V52·V54가 담당한다.
--
-- !! 비가역 !! 역마이그레이션이 없다. 적용 전 논리 백업과 §7 사전 점검 전량 통과가 필수다.
--
-- !! 재실행 안전성 -- 데이터 "모양"으로 구분한다 (리뷰 라운드 2, Finding 1 재작업) !!
-- 결정 M4에 따라 신규 이력서 분석 플로우도 Interview(Member, GeneratedQuestion, Integer, InterviewMode)
-- 생성자를 그대로 써서 interview_type='RESUME_BASED'인 행을 만든다(Interview.java는 이 태스크에서
-- 바이트 단위로 무수정 -- Interview.java:132-137). 즉 interview_type = 'RESUME_BASED' 만으로는
-- "구 질문생성 플로우가 만든 행"과 "신규 이력서 분석 플로우가 만든 행"을 구별할 수 없다.
--
-- 라운드 1은 이것을 고정 캘린더 컷오프(created_at < 리터럴)로 풀었다. 라운드 2는 그 컷오프를 데이터
-- 모양 판별로 대체한다. V51이 만든 XOR 제약(chk_generated_question_parent, generation_id와
-- analysis_id 중 정확히 하나만 NOT NULL)이 generated_question 행마다 "구 플로우 부모인가 신규
-- 플로우 부모인가"를 이미 배타적으로 못박아 뒀으므로, 그 자체가 배포 일정과 무관한 영구적 판별자다.
-- 캘린더 컷오프는 값을 하나 정해 파일에 박아 넣어야 했고(배포가 늦어지면 갱신이 필요한, 라운드 1이
-- 인정한 유지보수 부담이었다) generation_id 판별은 그런 값이 필요 없다 -- 재실행이 언제, 몇 번이든
-- 항상 안전하다.
--
-- 판별 조건: generated_question.generation_id IS NOT NULL 인 행이 구 플로우가 만든 행이다.
-- interview는 자신의 generated_question_id가 그런 행을 가리킬 때만(또는 아래 NULL 예외 참고)
-- 삭제 대상이다.
--
-- !! 이 판별은 V53이 V54보다 먼저 실행된다는 전제에서만 성립한다 (Flyway가 보장, out-of-order: false).
-- V54:92가 generation_id 컬럼 자체를 DROP COLUMN으로 지운다. V54가 이미 적용된 스키마에서 이 파일을
-- 재실행하면(예: 운영자가 raw SQL로 직접 재실행) 아래 모든 문장이 "Unknown column 'generation_id'"로
-- 즉시 죽는다 -- 이것은 의도된 동작이다. 조용히 전체 삭제로 후퇴하는 것보다 시끄럽게 죽는 것이 훨씬
-- 안전하므로 이 실패를 "고치려" 하지 않는다. V53은 V54 이전에만 의미가 있는 파일이고, 그 경계를
-- 넘어서면 스스로 실행을 거부해야 맞다.
--
-- generated_question_id가 NULL인 RESUME_BASED interview도 삭제 대상에 포함한다 -- 이 태스크의
-- 유일한 RESUME_BASED 생성 경로(Interview.java:132-137)는 이 코드베이스의 모든 호출부에서 지금
-- 항상 non-null GeneratedQuestion을 넘긴다(호출부 전수 확인). 이 생성자 자체에는 null을 막는
-- 가드가 없으므로 "구조적으로 불가능"은 과장이다 -- 정확한 사실은 "오늘 기준 이 모양을 만드는
-- 호출부가 없다"는 것뿐이다. 반면 V33은 generated_question_id 컬럼이 존재하기도 전에
-- resume_based_root_question(V33, V36:17에서 DROP)이라는 별도 테이블로 이력서 기반 질문을
-- 저장하던 시기가 있었다 -- 그 시기의 잔존 행이라면 interview_type='RESUME_BASED'이면서
-- generated_question_id가 NULL일 수 있다. 이런 행을 남겨두면 Interview.getDisplayQuestion()의
-- 무방비 역참조(generatedQuestion.getContent(), Interview.java:202-207)가 면접 목록 조회에서
-- NPE를 낸다 -- 그러니 이 모양은 "지금 이 코드로는 신규 플로우가 만들 수 없는, 구 플로우 잔존물로
-- 봐야 하는" 행으로 판단하고 삭제 대상에 포함한다.
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
--    공유 넥스트키 락을 건다. 괄호 안 수치(performance_schema.data_locks 실측: 소스 테이블에
--    S 락 3건, 대상 테이블에 X,GAP 2건)는 라운드 1의 서술어 모양(generated_question을 거치지
--    않는 단순 JOIN)을 대상으로 측정했다 -- 라운드 2에서 모든 서술어에 generated_question을
--    향한 중첩 IN 서브쿼리가 추가되면서 그 테이블에 대한 락 획득이 하나 더 늘었을 가능성이 있고,
--    이 수치는 그 변경 이후 재측정되지 않았다. READ COMMITTED가 갭 락을 없애는 방향
--    (X,GAP -> X,REC_NOT_GAP, S -> S,REC_NOT_GAP)은 서술어 모양이 바뀌어도 성립하는 일반
--    원칙이지만, 정확한 락 개수를 다시 인용하려면 현재 서술어로 재실측해야 한다.
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
--    구 플로우 판별은 답변/좋아요/메모 자신이 아니라 조상 interview의 모양(generated_question_id가
--    가리키는 generated_question의 generation_id, 또는 그 컬럼 자체가 NULL)으로 건다.
-- ---------------------------------------------------------------------------
DELETE FROM answer_like
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED'
                      AND (i.generated_question_id IS NULL
                           OR i.generated_question_id IN
                              (SELECT gq.id FROM generated_question gq WHERE gq.generation_id IS NOT NULL)));

DELETE FROM answer_memo
WHERE answer_id IN (SELECT a.id
                    FROM answer a
                             JOIN question q ON q.id = a.question_id
                             JOIN interview i ON i.id = q.interview_id
                    WHERE i.interview_type = 'RESUME_BASED'
                      AND (i.generated_question_id IS NULL
                           OR i.generated_question_id IN
                              (SELECT gq.id FROM generated_question gq WHERE gq.generation_id IS NOT NULL)));

-- ---------------------------------------------------------------------------
-- 2. answer
-- ---------------------------------------------------------------------------
DELETE FROM answer
WHERE question_id IN (SELECT q.id
                      FROM question q
                               JOIN interview i ON i.id = q.interview_id
                      WHERE i.interview_type = 'RESUME_BASED'
                        AND (i.generated_question_id IS NULL
                             OR i.generated_question_id IN
                                (SELECT gq.id FROM generated_question gq WHERE gq.generation_id IS NOT NULL)));

-- ---------------------------------------------------------------------------
-- 3. question
-- ---------------------------------------------------------------------------
DELETE FROM question
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED'
                         AND (i.generated_question_id IS NULL
                              OR i.generated_question_id IN
                                 (SELECT gq.id FROM generated_question gq WHERE gq.generation_id IS NOT NULL)));

-- ---------------------------------------------------------------------------
-- 4. interview_like
-- ---------------------------------------------------------------------------
DELETE FROM interview_like
WHERE interview_id IN (SELECT i.id
                       FROM interview i
                       WHERE i.interview_type = 'RESUME_BASED'
                         AND (i.generated_question_id IS NULL
                              OR i.generated_question_id IN
                                 (SELECT gq.id FROM generated_question gq WHERE gq.generation_id IS NOT NULL)));

-- ---------------------------------------------------------------------------
-- 5. interview (RESUME_BASED, 구 플로우 모양만). interview_type은 VARCHAR(50)이고
--    idx_interview_interview_type(V33)이 있어 등가 조회가 인덱스를 탄다.
--
--    WHERE에 `OR generated_question_id IS NOT NULL`(다른 타입까지 훑는 형태)을 붙이지 않는다 --
--    그것은 M2가 정의한 삭제 범위(interview_type='RESUME_BASED'와 그 후손)를 넘어 다른 타입의
--    면접을 조용히 지운다.
--
--    generated_question_id IS NULL 도 삭제 대상이다 -- 파일 선두 주석 참조(V33 시대의
--    resume_based_root_question 잔존 가능성). generated_question_id가 NOT NULL이면 그 부모의
--    generation_id로 구/신규를 가른다 -- 신규 플로우 부모(analysis_id)를 가리키는 행은 이 IN에
--    걸리지 않으므로 몇 번을 재실행해도 항상 보존된다.
-- ---------------------------------------------------------------------------
DELETE FROM interview
WHERE interview_type = 'RESUME_BASED'
  AND (generated_question_id IS NULL
       OR generated_question_id IN
          (SELECT gq.id FROM generated_question gq WHERE gq.generation_id IS NOT NULL));

-- ---------------------------------------------------------------------------
-- 6. generated_question. generation_id가 있는(= 구 플로우 부모) 행만 -- 전체 삭제(WHERE 없음)였던
--    최초 버전은 신규 플로우(analysis_id 부모) 행까지 지웠다(라운드 1, Finding 1). generation_id는
--    V51의 chk_generated_question_parent가 "정확히 하나만 NOT NULL"을 강제하므로, 이 조건 하나로
--    신규 플로우 행(analysis_id NOT NULL, generation_id NULL)은 항상 배제된다. interview 경유로
--    이 판별을 대신할 수 없는 이유: 이 테이블의 행은 interview 없이도 존재할 수 있다(이력서 분석
--    1건이 후보 질문 5~7개를 만들고 사용자가 그중 1개로만 면접을 시작하면, 나머지는 영원히
--    interview와 연결되지 않는다). 그래서 이 테이블 자신의 generation_id로 직접 판단한다.
--
--    TRUNCATE를 쓰지 않는다: interview.generated_question_id의 inbound FK 때문에
--    ERROR 1701로 죽고(실측), TRUNCATE는 DDL이라 암묵 커밋도 일으킨다.
--
--    fk_interview_generated_question은 ON DELETE 절이 없어 RESTRICT(delete_rule = NO ACTION,
--    실측)다. 5단계 이후에도 구 플로우 모양의 generated_question을 참조하는 interview 행이 남아
--    있으면 이 문장이 ERROR 1451로 죽는다 -- "구 플로우 모양의 RESUME_BASED 면접은 5단계에서 전부
--    지워졌어야 한다"에 대한 DB 레벨 자동 검증이며, §7-D 사전 점검과 이중 방어를 이룬다.
--
--    이 문장이 V54의 MODIFY analysis_id NOT NULL 을 가능하게 만든다 -- 이 문장 이후 analysis_id가
--    NULL인 행(= generation_id가 NOT NULL이었던 구 플로우 행)은 전부 제거되므로, 남는 행은 전부
--    analysis_id NOT NULL이다.
-- ---------------------------------------------------------------------------
DELETE FROM generated_question WHERE generation_id IS NOT NULL;
