package com.samhap.kokomen.resume.external.dto;

import java.util.List;

/**
 * 이력서 평가 tool 스키마의 provider 공용 사양(카테고리 목록·점수/불릿 경계).
 * Bedrock(Document)과 GPT(Map)는 출력 타입이 달라 렌더링 코드는 각자 갖되, 어긋나면 안 되는 값(카테고리 목록·경계)은
 * 이 한곳에서만 정의해 두 경로가 항상 같은 사양을 참조하도록 한다.
 */
final class ResumeEvaluationSchema {

    static final List<String> CATEGORIES = List.of(
            "technical_skills", "project_experience", "problem_solving", "career_growth", "documentation");
    static final int SCORE_MIN = 0;
    static final int SCORE_MAX = 100;
    static final int BULLET_MIN_ITEMS = 2;
    static final int BULLET_MAX_ITEMS = 6;

    private ResumeEvaluationSchema() {
    }
}
