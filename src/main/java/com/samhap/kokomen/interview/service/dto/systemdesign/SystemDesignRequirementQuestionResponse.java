package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;

public record SystemDesignRequirementQuestionResponse(
        String question,
        String answer
) {

    public static SystemDesignRequirementQuestionResponse from(SystemDesignRequirementQuestion requirementQuestion) {
        return new SystemDesignRequirementQuestionResponse(requirementQuestion.getContent(),
                requirementQuestion.getAnswer());
    }
}
