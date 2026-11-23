package com.samhap.kokomen.interview.external;

/**
 * 시스템 설계 면접의 LLM 호출 종류. 사용량·실패 로그에서 호출을 구분한다.
 */
public enum SystemDesignCallType {

    REQUIREMENT_ANSWER,
    ANSWER,
    END,
}
