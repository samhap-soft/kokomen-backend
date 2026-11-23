package com.samhap.kokomen.interview.external.dto.response;

import java.util.List;

/**
 * 역질문 답(L1) 호출 결과. 판정 필드가 문제마다 달라 도구 입력을 고정 record로 받지 않고 검증한 값만 담는다.
 *
 * @param askedRequirementCodes YES로 판정하고 서버가 검증한 숨은 요구사항 code(문제 순서)
 * @param answer                엔티티를 되돌린 LLM 답 원문. 최종 답은 서비스가 SystemDesignRequirementAnswerComposer로 정한다
 */
public record SystemDesignRequirementAnswerResult(
        List<String> askedRequirementCodes,
        String answer
) {

    public SystemDesignRequirementAnswerResult {
        askedRequirementCodes = List.copyOf(askedRequirementCodes);
    }
}
