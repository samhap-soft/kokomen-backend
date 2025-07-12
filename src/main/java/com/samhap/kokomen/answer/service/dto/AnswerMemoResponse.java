package com.samhap.kokomen.answer.service.dto;

import com.samhap.kokomen.answer.domain.AnswerMemo;

public record AnswerMemoResponse(
        Long answerMemoId
) {

    public AnswerMemoResponse(AnswerMemo answerMemo) {
        this(answerMemo.getId());
    }
}
