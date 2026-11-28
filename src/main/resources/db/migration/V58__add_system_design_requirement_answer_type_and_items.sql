-- 요구사항 역질문 답의 종류(answer_type)와 그 답으로 확정한 요구사항(requirement_items).
-- answer_type은 SystemDesignRequirementAnswerType의 이름(SEED, ASSUMED, MIXED, DECLINED)이다. enum 성격이라 V56처럼 VARCHAR로 둔다.
-- requirement_items는 [{"name": ..., "detail": ..., "source": "SEED" 또는 "ASSUMED"}] 모양의 JSON 배열이다(0~3개, 답에 적은 순서).
-- 이 버전 전에 저장한 행은 둘 다 NULL이고, 새로 저장하는 행은 둘 다 값이 있다(확정 항목이 없으면 빈 배열).
-- 기본값 없는 NULL 컬럼을 끝에 더하기만 하므로 기존 행의 값은 바뀌지 않는다.
ALTER TABLE system_design_requirement_question
    ADD COLUMN answer_type       VARCHAR(20) NULL,
    ADD COLUMN requirement_items JSON        NULL;
