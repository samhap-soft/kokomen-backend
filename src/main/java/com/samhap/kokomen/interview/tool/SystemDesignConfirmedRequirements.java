package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import java.util.List;

/**
 * 이 면접에서 이전 역질문들로 이미 확정한 요구사항. 역질문 답(L1)의 user 메시지(<confirmed_requirements>)와, 답에 묻지 않은
 * 숨은 요구사항의 수치가 섞였는지 보는 검사(SystemDesignRequirementAnswerComposer)가 함께 쓴다. 답변 채점(L2·L3)과 리포트가 쓰는
 * 확정한 요구사항 목록은 따로 모은다(SystemDesignConfirmedRequirement.collect).
 *
 * @param askedRequirementCodes 이전 역질문에서 YES로 판정한 숨은 요구사항 code(처음 물은 순서, 중복 없음)
 * @param items                 이전 역질문 행들의 확정 항목(물은 순서). 확정 항목을 저장하기 전에 만든 행은 건너뛴다. 저장할 때
 *                              collect와 같은 규칙으로 거른 항목이다(그렇게 거르기 전에 저장한 행의 항목은 거르지 않은 그대로다)
 */
public record SystemDesignConfirmedRequirements(
        List<String> askedRequirementCodes,
        List<SystemDesignRequirementItem> items
) {

    public SystemDesignConfirmedRequirements {
        askedRequirementCodes = List.copyOf(askedRequirementCodes);
        items = List.copyOf(items);
    }

    public static SystemDesignConfirmedRequirements empty() {
        return new SystemDesignConfirmedRequirements(List.of(), List.of());
    }
}
