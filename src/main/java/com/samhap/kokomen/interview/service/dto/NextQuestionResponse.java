package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Question;

public record NextQuestionResponse(
        Long questionId,
        String question,
        boolean isRoot
) {

    public static NextQuestionResponse createFollowingQuestionResponse(Question question) {
        return new NextQuestionResponse(
                question.getId(),
                question.getContent(),
                false
        );
    }
}
