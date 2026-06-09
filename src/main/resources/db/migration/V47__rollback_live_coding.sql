-- 라이브 코테 기능 롤백 (V45 역적용). roll-forward 전략: V45는 히스토리에 그대로 둔다.
-- 삭제 순서는 FK 체인(자식 -> 부모)을 따른다:
--   answer_like/answer_memo -> answer -> question -> interview_like -> interview -> root_question
-- 모든 DELETE는 interview_type='LIVE_CODING' / category='LIVE_CODING' 조건이라
-- 데이터가 없는 DB(prod/CI)에서는 no-op 이다.

DELETE FROM answer_like
WHERE answer_id IN (
    SELECT a.id
    FROM answer a
             JOIN question q ON a.question_id = q.id
             JOIN interview i ON q.interview_id = i.id
    WHERE i.interview_type = 'LIVE_CODING'
);

DELETE FROM answer_memo
WHERE answer_id IN (
    SELECT a.id
    FROM answer a
             JOIN question q ON a.question_id = q.id
             JOIN interview i ON q.interview_id = i.id
    WHERE i.interview_type = 'LIVE_CODING'
);

DELETE FROM answer
WHERE question_id IN (
    SELECT q.id
    FROM question q
             JOIN interview i ON q.interview_id = i.id
    WHERE i.interview_type = 'LIVE_CODING'
);

DELETE FROM question
WHERE interview_id IN (
    SELECT id FROM interview WHERE interview_type = 'LIVE_CODING'
);

DELETE FROM interview_like
WHERE interview_id IN (
    SELECT id FROM interview WHERE interview_type = 'LIVE_CODING'
);

DELETE FROM interview
WHERE interview_type = 'LIVE_CODING';

DELETE FROM root_question
WHERE category = 'LIVE_CODING';

-- V45 이전(V16 최종) category ENUM 으로 복원 (LIVE_CODING 제거)
ALTER TABLE root_question
    MODIFY COLUMN category ENUM(
        'ALGORITHM_DATA_STRUCTURE',
        'DATABASE',
        'NETWORK',
        'OPERATING_SYSTEM',
        'JAVA_SPRING',
        'INFRA',
        'FRONTEND',
        'REACT',
        'JAVASCRIPT_TYPESCRIPT'
    ) NOT NULL;
