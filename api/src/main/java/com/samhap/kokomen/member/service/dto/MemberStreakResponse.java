package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.interview.dto.DailyInterviewCount;
import java.util.List;

public record MemberStreakResponse(
        List<DailyInterviewCount> dailyCounts,
        Integer maxStreak,
        Integer currentStreak
) {
}
