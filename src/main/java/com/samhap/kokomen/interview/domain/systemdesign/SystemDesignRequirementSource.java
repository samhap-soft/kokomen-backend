package com.samhap.kokomen.interview.domain.systemdesign;

/**
 * 확정한 요구사항의 출처. SEED는 숨은 요구사항의 정해 둔 사실이고, ASSUMED는 숨은 요구사항에 없어 면접관(LLM)이 정한 값이다.
 */
public enum SystemDesignRequirementSource {

    SEED,
    ASSUMED
}
