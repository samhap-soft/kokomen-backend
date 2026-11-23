package com.samhap.kokomen.interview.external.dto.response;

import com.samhap.kokomen.answer.domain.AnswerRank;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 마지막 답변 평가와 리포트(L3) 호출 결과. 검증을 통과하고 엔티티를 되돌린 값만 담는다.
 *
 * @param rank           마지막 답변의 등급
 * @param feedback       마지막 답변의 피드백(앞뒤 공백을 지운 값). 컬럼 한도는 저장할 때 자른다
 * @param totalFeedback  strengths·improvements·learning_direction을 이은 종합 피드백. 비어 있지 않고 2000자 이하다
 * @param metByPointCode 평가 포인트 code → 충족 여부. 문제의 평가 포인트를 빠짐없이 문제 순서로 담는다
 */
public record SystemDesignEndEvaluation(
        AnswerRank rank,
        String feedback,
        String totalFeedback,
        Map<String, Boolean> metByPointCode
) {

    public SystemDesignEndEvaluation {
        metByPointCode = Collections.unmodifiableMap(new LinkedHashMap<>(metByPointCode));
    }
}
