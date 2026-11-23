package com.samhap.kokomen.interview.external.dto.response;

import com.samhap.kokomen.answer.domain.AnswerRank;

/**
 * 답변 평가(L2) 호출 결과. 검증을 통과하고 엔티티를 되돌린 값만 담는다.
 *
 * @param rank         가장 최근 답변의 등급
 * @param feedback     가장 최근 답변의 피드백(앞뒤 공백을 지운 값). 컬럼 한도는 저장할 때 자른다
 * @param nextQuestion LLM이 다듬은 다음 질문. 비었거나 질문 컬럼 한도를 넘으면 null이고, 서비스가 기준 질문을 그대로 쓴다
 */
public record SystemDesignAnswerEvaluation(
        AnswerRank rank,
        String feedback,
        String nextQuestion
) {
}
