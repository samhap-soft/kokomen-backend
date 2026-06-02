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
    'JAVASCRIPT_TYPESCRIPT',
    'LIVE_CODING'
    ) NOT NULL;

INSERT INTO root_question (category, state, content, question_order, created_at)
VALUES
    ('LIVE_CODING', 'ACTIVE',
     '정수 배열 nums와 정수 target이 주어집니다. 두 원소를 더해 target이 되는 경우 그 두 원소의 인덱스를 반환하는 함수를 작성하세요. 같은 원소를 두 번 사용할 수 없으며 정답은 유일하다고 가정합니다. 작성한 풀이의 시간 복잡도와 공간 복잡도를 함께 설명해 주세요.',
     1, NOW(6)),
    ('LIVE_CODING', 'ACTIVE',
     '소괄호, 중괄호, 대괄호로만 이루어진 문자열이 주어집니다. 모든 괄호가 올바른 종류와 순서로 닫히는지 판별하는 함수를 작성하세요. 빈 문자열은 유효한 것으로 간주합니다. 작성한 풀이의 자료구조 선택 이유와 시간 복잡도를 설명해 주세요.',
     2, NOW(6));
