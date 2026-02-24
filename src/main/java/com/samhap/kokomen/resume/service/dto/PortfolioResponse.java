package com.samhap.kokomen.resume.service.dto;

import java.time.LocalDateTime;

public record PortfolioResponse(
        Long id,
        String title,
        String url,
        LocalDateTime createdAt
) {
}
