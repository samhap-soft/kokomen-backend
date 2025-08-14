package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.InterviewMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record AnswerRequest(
        @Length(max = 2000, message = "answer는 최대 2000자까지 입력할 수 있습니다.")
        @NotBlank(message = "answer는 비어있을 수 없습니다.")
        String answer,
        @NotNull(message = "mode는 null일 수 없습니다.")
        InterviewMode mode
) {
}
