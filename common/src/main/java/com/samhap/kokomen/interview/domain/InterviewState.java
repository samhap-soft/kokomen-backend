package com.samhap.kokomen.interview.domain;

public enum InterviewState {
    GENERATING_QUESTIONS,       // 질문 생성 중
    QUESTION_GENERATION_FAILED, // 질문 생성 실패
    PENDING,                    // 질문 생성 완료, 면접 시작 전
    IN_PROGRESS,                // 면접 진행 중
    FINISHED,                   // 면접 완료
    ;
}
