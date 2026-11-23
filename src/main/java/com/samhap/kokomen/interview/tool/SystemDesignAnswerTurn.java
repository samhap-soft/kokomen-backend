package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

/**
 * 설계 질문 하나와 그 답변. question은 면접자가 받은 질문 문장(1번은 기준 질문, 2번부터는 LLM이 다듬어 저장한 문장)이다.
 */
public record SystemDesignAnswerTurn(
        int questionOrder,
        SystemDesignStage stage,
        String question,
        String answer
) {
}
