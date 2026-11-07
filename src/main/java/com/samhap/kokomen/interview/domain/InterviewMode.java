package com.samhap.kokomen.interview.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 면접 토큰 비용 규칙의 단일 출처다. 시작 검증, 답변 전 검증, 실제 차감이 모두 이 값을 쓴다.
 *
 * <p>실제 차감은 두 곳에서만 일어난다. 답변마다 {@link #ANSWER_TOKEN_COST}를 InterviewProceedService가 차감하고,
 * 다음 질문이 이어지면 그 질문의 음성 비용을 InterviewProceedBedrockFlowAsyncService가 TTS 성공 뒤에 차감한다.
 * 첫 질문 음성(루트 질문은 미리 만들어 두고, 이력서 기반은 시작할 때 만든다)과 마지막 답변 뒤에는 음성 비용이 없다.
 * 그래서 N문항 면접은 텍스트 N개, 음성 2N-1개를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum InterviewMode {

    TEXT(0),
    VOICE(1),
    ;

    public static final int ANSWER_TOKEN_COST = 1;

    private final int questionVoiceTokenCost;

    public int calculateRequiredTokenCountToStart(int maxQuestionCount) {
        return calculateRequiredTokenCountToAnswer(true) * (maxQuestionCount - 1)
                + calculateRequiredTokenCountToAnswer(false);
    }

    public int calculateRequiredTokenCountToAnswer(boolean hasNextQuestion) {
        if (hasNextQuestion) {
            return ANSWER_TOKEN_COST + questionVoiceTokenCost;
        }
        return ANSWER_TOKEN_COST;
    }
}
