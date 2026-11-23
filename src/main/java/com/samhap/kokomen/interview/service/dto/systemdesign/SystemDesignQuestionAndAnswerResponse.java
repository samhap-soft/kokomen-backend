package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

/**
 * 답이 저장된 설계 질문. questionId는 면접 안 순번(1~6)이다.
 */
public record SystemDesignQuestionAndAnswerResponse(
        Integer questionId,
        SystemDesignStage stage,
        String question,
        String answer,
        SystemDesignDiagramResponse diagram
) {

    public static SystemDesignQuestionAndAnswerResponse of(SystemDesignQuestion question, SystemDesignAnswer answer) {
        return new SystemDesignQuestionAndAnswerResponse(question.getQuestionOrder(), question.getStage(),
                question.getContent(), answer.getContent(), SystemDesignDiagramResponse.from(answer.getDiagram()));
    }
}
