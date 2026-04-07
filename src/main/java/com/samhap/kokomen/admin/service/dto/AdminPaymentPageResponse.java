package com.samhap.kokomen.admin.service.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminPaymentPageResponse(
        List<AdminPaymentResponse> data,
        int currentPage,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminPaymentPageResponse of(List<AdminPaymentResponse> data, Page<?> page) {
        return new AdminPaymentPageResponse(
                data,
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
