package com.samhap.kokomen.token.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseMetadata(
        @NotBlank(message = "product_name은 비어있거나 공백일 수 없습니다.")
        String productName,
        @NotNull(message = "count는 null일 수 없습니다.")
        @Positive(message = "count는 양수여야 합니다.")
        Integer count,
        @NotNull(message = "unit_price는 null일 수 없습니다.")
        @Positive(message = "unit_price는 양수여야 합니다.")
        Long unitPrice
) {
}
