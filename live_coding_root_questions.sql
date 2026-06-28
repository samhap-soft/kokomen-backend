-- =============================================================================
-- 라이브 코테(라이브 코딩테스트) 루트 질문 목 데이터
-- =============================================================================
-- - Flyway 마이그레이션이 아니라, 콘솔에서 직접 실행하기 위한 스크립트입니다.
-- - 코딩 문제는 별도 카테고리가 아니라 기존 카테고리(ALGORITHM_DATA_STRUCTURE)에
--   소속되며 question_type = 'CODE' 로 구분됩니다.
-- - question_order 는 NULL 입니다.
--     · 코딩 문제는 순차 출제 대상이 아니라 '라이브 코테 포함' 시 랜덤으로 선택됩니다.
--     · uk_root_question_category_question_order (category, question_order) 유니크 제약은
--       MySQL 에서 다중 NULL 을 허용하므로 기존 일반 질문 order 와 충돌하지 않습니다.
-- - title 에 문제 제목, content 에 마크다운 문제 설명/예시/제약을 담습니다.
-- - 각 INSERT 는 동일 제목의 CODE 문제가 없을 때만 삽입하므로 여러 번 실행해도 안전합니다.
-- =============================================================================

-- 1) Two Sum (Easy) ----------------------------------------------------------
INSERT INTO root_question (created_at, category, state, question_type, title, content, question_order)
SELECT NOW(6), 'ALGORITHM_DATA_STRUCTURE', 'ACTIVE', 'CODE', 'Two Sum (두 수의 합)',
'정수 배열 `nums` 와 정수 `target` 이 주어집니다. 배열에서 두 원소를 더해 `target` 이 되는 경우, 그 **두 원소의 인덱스**를 반환하는 함수를 작성하세요.

- 각 입력에는 정확히 하나의 정답만 존재한다고 가정합니다.
- 같은 원소를 두 번 사용할 수 없습니다.
- 반환하는 두 인덱스의 순서는 무관합니다.

## 예시 1
```
입력: nums = [2, 7, 11, 15], target = 9
출력: [0, 1]
설명: nums[0] + nums[1] = 2 + 7 = 9 이므로 [0, 1] 을 반환합니다.
```

## 예시 2
```
입력: nums = [3, 2, 4], target = 6
출력: [1, 2]
```

## 예시 3
```
입력: nums = [3, 3], target = 6
출력: [0, 1]
```

## 제약 조건
- 2 <= nums.length <= 10^4
- -10^9 <= nums[i] <= 10^9
- -10^9 <= target <= 10^9
- 정답은 유일하게 존재합니다.

## Follow-up
- 시간 복잡도가 O(n^2) 보다 빠른 풀이를 설계할 수 있나요?
- 작성한 풀이의 시간·공간 복잡도를 함께 설명해 주세요.',
NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM root_question WHERE question_type = 'CODE' AND title = 'Two Sum (두 수의 합)'
);

-- 2) Valid Parentheses (Easy) ------------------------------------------------
INSERT INTO root_question (created_at, category, state, question_type, title, content, question_order)
SELECT NOW(6), 'ALGORITHM_DATA_STRUCTURE', 'ACTIVE', 'CODE', 'Valid Parentheses (유효한 괄호)',
'`(`, `)`, `{`, `}`, `[`, `]` 문자로만 이루어진 문자열 `s` 가 주어집니다. 다음 조건을 **모두** 만족하면 유효한 문자열로 판단합니다.

1. 열린 괄호는 같은 종류의 괄호로 닫혀야 합니다.
2. 열린 괄호는 올바른 순서로 닫혀야 합니다.
3. 모든 닫는 괄호는 대응하는 같은 종류의 여는 괄호를 가져야 합니다.

문자열이 유효한지 판별하는 함수를 작성하세요. 빈 문자열은 유효한 것으로 간주합니다.

## 예시 1
```
입력: s = "()"
출력: true
```

## 예시 2
```
입력: s = "()[]{}"
출력: true
```

## 예시 3
```
입력: s = "(]"
출력: false
설명: 여는 괄호 ( 가 다른 종류의 괄호 ] 로 닫혔습니다.
```

## 예시 4
```
입력: s = "([)]"
출력: false
설명: 괄호가 올바른 순서로 닫히지 않았습니다.
```

## 예시 5
```
입력: s = "{[]}"
출력: true
```

## 제약 조건
- 1 <= s.length <= 10^4
- `s` 는 괄호 문자 `()[]{}` 로만 구성됩니다.

## Follow-up
- 어떤 자료구조를 사용했고, 그 자료구조를 선택한 이유는 무엇인가요?
- 입력이 닫는 괄호로 시작하거나 여는 괄호가 끝까지 닫히지 않는 경우 등 엣지 케이스는 어떻게 처리했나요?',
NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM root_question WHERE question_type = 'CODE' AND title = 'Valid Parentheses (유효한 괄호)'
);

-- 3) Longest Substring Without Repeating Characters (Medium) -----------------
INSERT INTO root_question (created_at, category, state, question_type, title, content, question_order)
SELECT NOW(6), 'ALGORITHM_DATA_STRUCTURE', 'ACTIVE', 'CODE',
'Longest Substring Without Repeating Characters (중복 문자 없는 가장 긴 부분 문자열)',
'문자열 `s` 가 주어질 때, **중복되는 문자가 없는 가장 긴 부분 문자열(substring)의 길이**를 반환하는 함수를 작성하세요.

> 부분 문자열(substring)은 연속된 문자들의 나열이며, 순서만 유지하면 되는 부분 수열(subsequence)과는 다릅니다.

## 예시 1
```
입력: s = "abcabcbb"
출력: 3
설명: 가장 긴 부분 문자열은 "abc" 이며 길이는 3 입니다.
```

## 예시 2
```
입력: s = "bbbbb"
출력: 1
설명: 가장 긴 부분 문자열은 "b" 이며 길이는 1 입니다.
```

## 예시 3
```
입력: s = "pwwkew"
출력: 3
설명: 가장 긴 부분 문자열은 "wke" 이며 길이는 3 입니다.
      "pwke" 는 부분 수열이지 부분 문자열이 아니므로 정답이 아닙니다.
```

## 제약 조건
- 0 <= s.length <= 5 * 10^4
- `s` 는 영문 대소문자, 숫자, 기호, 공백으로 구성됩니다.

## Follow-up
- 문자열을 한 번만 순회하는 O(n) 풀이가 가능한가요?
- 윈도우 내에 어떤 문자가 들어 있는지를 어떤 자료구조로 추적했고, 중복을 발견했을 때 윈도우의 시작점을 어떻게 이동시켰나요?',
NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM root_question
    WHERE question_type = 'CODE'
      AND title = 'Longest Substring Without Repeating Characters (중복 문자 없는 가장 긴 부분 문자열)'
);

-- =============================================================================
-- 확인용 조회
-- =============================================================================
SELECT id, category, state, question_type, title, question_order, LEFT(content, 40) AS content_preview
FROM root_question
WHERE question_type = 'CODE'
ORDER BY id;
