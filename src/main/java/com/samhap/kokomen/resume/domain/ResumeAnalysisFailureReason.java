package com.samhap.kokomen.resume.domain;

/**
 * failure_reason 컬럼에 실제로 기록되는 값만 둔다. 게스트 1회 초과는 행을 만들지 않고 요청 단계에서
 * BadRequestException으로 끝나므로 그에 대응하는 상수는 없다.
 */
public enum ResumeAnalysisFailureReason {

    EVALUATION_LLM,
    OUTPUT_TRUNCATED,
    QUESTION_LLM,
    PERSISTENCE,
    CAPACITY,
    STALE_SWEEP,
    ;
}
