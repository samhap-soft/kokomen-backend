package com.samhap.kokomen.answer.service.dto;

import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;

public record AnswerMemoRequest(
        AnswerMemoVisibility visibility,
        String content
) {
}
