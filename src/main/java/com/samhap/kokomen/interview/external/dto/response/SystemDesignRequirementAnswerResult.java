package com.samhap.kokomen.interview.external.dto.response;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import java.util.List;
import java.util.Objects;

/**
 * 역질문 답(L1) 호출 결과. 판정 필드가 문제마다 달라 도구 입력을 고정 record로 받지 않고, SystemDesignRequirementAnswerComposer가
 * 검증하고 바로잡은 값만 담는다. 서비스는 이 값을 그대로 저장하고 응답한다.
 *
 * @param askedRequirementCodes YES로 판정하고 서버가 검증한 숨은 요구사항 code(문제 순서)
 * @param answerType            판정에 맞춰 바로잡은 답의 종류
 * @param answer                면접자에게 줄 최종 답(시드 답 폴백과 요구사항 질문이 아닐 때의 정해진 문구를 반영함)
 * @param requirementItems      이번 답으로 확정한 요구사항(검사를 통과한 0~3개)
 */
public record SystemDesignRequirementAnswerResult(
        List<String> askedRequirementCodes,
        SystemDesignRequirementAnswerType answerType,
        String answer,
        List<SystemDesignRequirementItem> requirementItems
) {

    public SystemDesignRequirementAnswerResult {
        askedRequirementCodes = List.copyOf(askedRequirementCodes);
        Objects.requireNonNull(answerType, "answerType");
        Objects.requireNonNull(answer, "answer");
        requirementItems = List.copyOf(requirementItems);
    }
}
