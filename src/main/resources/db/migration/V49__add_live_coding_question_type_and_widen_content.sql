-- 라이브 코테 기능: root_question에 질문 타입(GENERAL/CODE)과 코딩 문제 제목 컬럼 추가.
-- 코테 문제는 별도 카테고리가 아니라 기존 카테고리에 소속되며 question_type 으로 구분한다.
-- 마크다운 문제 설명/코드 답변을 담기 위해 content 컬럼들을 확장한다.
-- (CODE 루트 질문 시드 데이터는 이후 별도 마이그레이션에서 추가한다.)

ALTER TABLE root_question
    ADD COLUMN question_type ENUM('GENERAL', 'CODE') NOT NULL DEFAULT 'GENERAL';

ALTER TABLE root_question
    ADD COLUMN title VARCHAR(255) NULL;

ALTER TABLE root_question MODIFY COLUMN content TEXT NOT NULL;
ALTER TABLE question MODIFY COLUMN content TEXT NOT NULL;
ALTER TABLE answer MODIFY COLUMN content VARCHAR(10000) NOT NULL;
