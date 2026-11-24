package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.tool.InterviewProceedState;

/**
 * 답변이 저장된 질문의 채점 결과. 면접의 지금 상태가 아니라 그 답변을 처리한 결과라, 지난 질문을 다시 조회해도 그때의 값이 나온다.
 * stage는 다음 질문의 단계이고(마지막 답변이면 그 질문의 단계), nextQuestionId는 다음 질문의 면접 안 순번이다.
 */
public record SystemDesignAnswerCompletedResponse(
        InterviewProceedState proceedState,
        InterviewState interviewState,
        SystemDesignStage stage,
        AnswerRank curAnswerRank,
        // 전역 non_null 설정과 달리 null이어도 키를 내보낸다(프론트 계약: number | null, string | null).
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Integer nextQuestionId,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String nextQuestion
) implements SystemDesignAnswerResultResponse {

    public static SystemDesignAnswerCompletedResponse inProgress(SystemDesignAnswer answer,
                                                                 SystemDesignQuestion nextQuestion) {
        return new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED, InterviewState.IN_PROGRESS,
                nextQuestion.getStage(), answer.getAnswerRank(), nextQuestion.getQuestionOrder(),
                nextQuestion.getContent());
    }

    public static SystemDesignAnswerCompletedResponse finished(SystemDesignQuestion lastQuestion,
                                                               SystemDesignAnswer lastAnswer) {
        return new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED, InterviewState.FINISHED,
                lastQuestion.getStage(), lastAnswer.getAnswerRank(), null, null);
    }
}
