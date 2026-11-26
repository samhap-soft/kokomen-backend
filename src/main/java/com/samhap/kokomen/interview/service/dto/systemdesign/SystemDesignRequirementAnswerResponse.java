package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;

/**
 * 역질문 답. question은 앞뒤 공백을 지운 저장값이고, remainingCount는 이번 질문까지 센 뒤 더 할 수 있는 역질문 수다.
 */
public record SystemDesignRequirementAnswerResponse(
        String question,
        String answer,
        int remainingCount
) {

    // 저장한 역질문 행에서 만든다. 새로 저장한 직후와, 응답을 받지 못해 다시 보낸 요청에 같은 행으로 답할 때가 같은 응답이다.
    public static SystemDesignRequirementAnswerResponse from(SystemDesignRequirementQuestion requirementQuestion) {
        return new SystemDesignRequirementAnswerResponse(requirementQuestion.getContent(),
                requirementQuestion.getAnswer(),
                SystemDesignRequirementQuestion.MAX_COUNT - requirementQuestion.getQuestionOrder());
    }
}
