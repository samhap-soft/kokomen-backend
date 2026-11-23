package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.tool.InterviewProceedState;

/**
 * 답변이 아직 저장되지 않은 질문의 채점 상태. proceedState는 LLM_PENDING(채점 중)이나 LLM_FAILED(실패, 다시 낼 수 있다)다.
 */
public record SystemDesignAnswerPendingResponse(
        InterviewProceedState proceedState
) implements SystemDesignAnswerResultResponse {
}
