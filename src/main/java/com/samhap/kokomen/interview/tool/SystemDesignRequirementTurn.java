package com.samhap.kokomen.interview.tool;

/**
 * 이미 저장된 역질문 하나와 그때 돌려준 면접관 답.
 */
public record SystemDesignRequirementTurn(
        String question,
        String answer
) {
}
