package com.samhap.kokomen.answer.service.dto;

import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record AnswerMemoUpdateRequest(
        @NotNull(message = "visibility는 null일 수 없습니다.")
        AnswerMemoVisibility visibility,
        @Length(max = 5000, message = "content는 최대 5000자까지 입력할 수 있습니다.")
        @NotBlank(message = "content는 비어있을 수 없습니다.")
        String content
) {
}
