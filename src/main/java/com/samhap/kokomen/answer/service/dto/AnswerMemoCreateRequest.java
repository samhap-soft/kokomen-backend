package com.samhap.kokomen.answer.service.dto;

import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;

public record AnswerMemoCreateRequest(
        AnswerMemoVisibility visibility,
        String content
) {
}
