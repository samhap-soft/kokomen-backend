package com.samhap.kokomen.interview.domain;

import java.util.List;

public enum InterviewType {
    CATEGORY_BASED,
    RESUME_BASED,
    LIVE_CODING,
    PERSONALITY,
    SYSTEM_DESIGN,
    ;

    /**
     * question/answer 테이블과 꼬리 질문 흐름을 쓰는 면접 타입이다. 기존 면접의 결과·진행·좋아요 API는 이 타입만 다룬다.
     * 허용 목록이므로 새 타입을 추가하면 기존 상세 경로에서 자동으로 빠지고, 다루려면 여기에 명시해야 한다.
     */
    public static final List<InterviewType> TAIL_QUESTION_FLOW_TYPES =
            List.of(CATEGORY_BASED, RESUME_BASED, LIVE_CODING, PERSONALITY);

    public boolean isTailQuestionFlow() {
        return TAIL_QUESTION_FLOW_TYPES.contains(this);
    }
}
