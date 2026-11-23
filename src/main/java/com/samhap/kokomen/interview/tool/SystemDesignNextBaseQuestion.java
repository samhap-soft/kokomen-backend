package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

/**
 * 다음 질문의 바탕이 되는 문제의 기준 질문. LLM은 주제와 의도를 유지한 채 이 질문을 면접자의 설계에 맞게 다듬는다.
 */
public record SystemDesignNextBaseQuestion(
        int questionOrder,
        SystemDesignStage stage,
        String content
) {
}
