package com.samhap.kokomen.interview.domain.systemdesign;

/**
 * 역질문 답으로 확정한 요구사항 하나. 역질문 행에 JSON 배열로 저장하고(SystemDesignRequirementItemsConverter), 다음 역질문의
 * 확정한 요구사항 문맥과 묻지 않은 숨은 요구사항의 수치 검사에 쓴다. 저장 JSON의 속성 이름은 이 record의 구성 요소 이름이다.
 *
 * @param name   요구사항의 짧은 이름. 1~{@value #MAX_NAME_LENGTH}자
 * @param detail 확정한 내용 한 문장. 1~{@value #MAX_DETAIL_LENGTH}자
 */
public record SystemDesignRequirementItem(
        String name,
        String detail,
        SystemDesignRequirementSource source
) {

    public static final int MAX_NAME_LENGTH = 30;
    public static final int MAX_DETAIL_LENGTH = 150;

    public SystemDesignRequirementItem {
        if (!hasValidLength(name, MAX_NAME_LENGTH) || !hasValidLength(detail, MAX_DETAIL_LENGTH) || source == null) {
            throw new IllegalArgumentException("확정한 요구사항은 이름 1~%d자, 내용 1~%d자, 출처가 있어야 합니다."
                    .formatted(MAX_NAME_LENGTH, MAX_DETAIL_LENGTH));
        }
    }

    // 글자 수는 String.length() 기준이다. 프론트가 JS .length로 세는 단위와 같다.
    public static boolean hasValidLength(String value, int maxLength) {
        return value != null && !value.isEmpty() && value.length() <= maxLength;
    }
}
