package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.tool.InterviewProceedState;

/**
 * 답변 채점 결과 폴링 응답. 채점 중·실패는 proceed_state 하나만 주고, 완료는 그 답변을 처리한 결과까지 준다.
 */
public interface SystemDesignAnswerResultResponse {

    InterviewProceedState proceedState();
}
