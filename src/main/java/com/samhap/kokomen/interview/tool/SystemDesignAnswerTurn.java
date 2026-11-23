package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

/**
 * 설계 질문 하나와 그 답변. question은 면접자가 받은 질문 문장으로 저장된 값이다. 1번은 기준 질문이고, 2번부터는 LLM이 다듬은
 * 문장이거나(다듬지 않고 그대로 돌려준 경우 포함) 다음 질문이 폴백돼 그대로 쓴 기준 질문이다.
 */
public record SystemDesignAnswerTurn(
        int questionOrder,
        SystemDesignStage stage,
        String question,
        String answer
) {
}
