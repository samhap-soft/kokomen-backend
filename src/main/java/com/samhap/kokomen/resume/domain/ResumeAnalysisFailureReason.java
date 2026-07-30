package com.samhap.kokomen.resume.domain;

public enum ResumeAnalysisFailureReason {

    EVALUATION_LLM,
    OUTPUT_TRUNCATED,
    QUESTION_LLM,
    PERSISTENCE,
    CAPACITY,
    STALE_SWEEP,
    GUEST_LIMIT,
    ;
}
