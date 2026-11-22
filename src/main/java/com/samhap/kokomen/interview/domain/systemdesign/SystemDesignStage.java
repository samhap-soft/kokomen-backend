package com.samhap.kokomen.interview.domain.systemdesign;

public enum SystemDesignStage {
    REQUIREMENTS,
    HIGH_LEVEL,
    DEEP_DIVE,
    ;

    private static final int FIRST_QUESTION_ORDER = 1;

    /**
     * 설계 질문 순번의 단계. 1~{@link SystemDesignInterview#HIGH_LEVEL_QUESTION_COUNT}는 개략 설계, 그 뒤
     * {@link SystemDesignInterview#MAX_QUESTION_COUNT}까지는 심화다. 다음 질문의 단계는 {@code ofQuestionOrder(questionOrder + 1)}이다.
     */
    public static SystemDesignStage ofQuestionOrder(int questionOrder) {
        if (questionOrder >= FIRST_QUESTION_ORDER && questionOrder <= SystemDesignInterview.HIGH_LEVEL_QUESTION_COUNT) {
            return HIGH_LEVEL;
        }
        if (questionOrder > SystemDesignInterview.HIGH_LEVEL_QUESTION_COUNT
                && questionOrder <= SystemDesignInterview.MAX_QUESTION_COUNT) {
            return DEEP_DIVE;
        }
        throw new IllegalArgumentException(
                "질문 순번은 %d~%d이어야 합니다: %d".formatted(FIRST_QUESTION_ORDER, SystemDesignInterview.MAX_QUESTION_COUNT,
                        questionOrder));
    }
}
