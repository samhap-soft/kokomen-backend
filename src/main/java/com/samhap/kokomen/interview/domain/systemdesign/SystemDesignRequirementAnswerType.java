package com.samhap.kokomen.interview.domain.systemdesign;

/**
 * 역질문 답의 종류. LLM이 낸 값을 서버가 판정(YES인 숨은 요구사항이 있는지)에 맞춰 바로잡은 값이다
 * (SystemDesignRequirementAnswerComposer). 이 값을 저장하기 전에 만든 역질문 행은 NULL이다.
 */
public enum SystemDesignRequirementAnswerType {

    // 물은 숨은 요구사항의 정해 둔 사실만으로 답했다.
    SEED,
    // 숨은 요구사항에 없는 요구사항을 면접관(LLM)이 정해 답했다.
    ASSUMED,
    // 한 질문에 둘 다 답했다.
    MIXED,
    // 요구사항을 묻는 질문이 아니라 정해진 문구로 답했다.
    DECLINED
}
