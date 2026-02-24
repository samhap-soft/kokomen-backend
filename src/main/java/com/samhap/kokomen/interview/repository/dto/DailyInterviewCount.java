package com.samhap.kokomen.interview.repository.dto;

import java.time.LocalDate;

public record DailyInterviewCount(
        LocalDate date,
        Long count
) {
}
