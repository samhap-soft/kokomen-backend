package com.samhap.kokomen.interview.dto;

import java.time.LocalDate;

public record DailyInterviewCount(
        LocalDate date,
        Long count
) {
}