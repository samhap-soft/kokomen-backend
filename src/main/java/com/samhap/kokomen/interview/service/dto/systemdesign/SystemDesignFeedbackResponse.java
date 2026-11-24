package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

/**
 * 리포트의 질문별 피드백. questionId는 면접 안 순번(1~6)이고 stage는 그 질문의 단계다. diagram은 그 답변과 함께 낸 다이어그램이다.
 */
public record SystemDesignFeedbackResponse(
        Integer questionId,
        SystemDesignStage stage,
        String question,
        String answer,
        SystemDesignDiagramResponse diagram,
        AnswerRank answerRank,
        String answerFeedback
) {

    public static SystemDesignFeedbackResponse of(SystemDesignQuestion question, SystemDesignAnswer answer) {
        return new SystemDesignFeedbackResponse(question.getQuestionOrder(), question.getStage(), question.getContent(),
                answer.getContent(), SystemDesignDiagramResponse.from(answer.getDiagram()), answer.getAnswerRank(),
                answer.getFeedback());
    }
}
