-- dev 환경 root_question 테이블에 인성 면접(PERSONALITY) 루트 질문을 추가하는 시드 스크립트.
-- 인성 질문은 별도 코드 타입이 아니므로 question_type 은 GENERAL 이며 title 은 사용하지 않는다.
-- (category, question_order) 유니크 제약(uk_root_question_category_question_order)을 회피하기 위해
-- 기존 PERSONALITY 질문의 최대 question_order 다음 번호부터 이어서 부여한다.
-- 한 번만 실행하는 것을 전제로 하며, 재실행 시 동일 내용이 중복 삽입될 수 있으니 주의한다.

SET @base := (SELECT COALESCE(MAX(question_order), 0)
              FROM root_question
              WHERE category = 'PERSONALITY');

INSERT INTO root_question (category, state, question_type, content, question_order, created_at)
VALUES
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '자기소개 한번 해주세요.', @base + 1, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '우리 회사에 지원한 이유가 뭔가요?', @base + 2, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '본인의 강점이 뭔가요?', @base + 3, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '주변 사람들이 본인을 어떻게 평가하던가요?', @base + 4, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '10년 후 본인은 어떤 사람이 되어있을거 같은가요?', @base + 5, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '(공백기가 길 경우) 공백기에는 무엇을 하셨나요?', @base + 6, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '어떤 회사가 좋은 회사라고 생각하시나요?', @base + 7, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '같이 일하고 싶은 동료와 같이 일하기 싫은 동료는 어떤 동료들인가요?', @base + 8, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '개발자가 되고 싶은 이유?', @base + 9, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '프로젝트에 대해서 간략하게 소개 좀 해주실래요?', @base + 10, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '프로젝트를 진행하면서 가장 좋았던 경험과 힘들었던 경험', @base + 11, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '프로젝트를 진행하면서 배운 점', @base + 12, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '협업 툴 사용 경험', @base + 13, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '프로젝트를 하면서 자신의 역할이 무엇이었나요?', @base + 14, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '상사가 부당한 지시를 한다면?', @base + 15, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '본인이 생각한 업무와 다른 업무를 하게 된다면?', @base + 16, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '팀원과 갈등이 생기면 어떻게 해결하는 편인지', @base + 17, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '다른 사람을 설득할 때 사용하는 방법(경험 기반, 수치 자료 이용 등등)', @base + 18, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '조직 문화에 적응하는 나만의 방법이 있는지', @base + 19, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '성격이 외향적인지 내향적인지', @base + 20, NOW(6)),
    ('PERSONALITY', 'ACTIVE', 'GENERAL', '리더의 역할이란 무엇인지', @base + 21, NOW(6));
