package com.samhap.kokomen.interview.external.dto.response;

import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import java.util.List;

public record InterviewSummaryResponses(
        String intervieweeNickname,
        List<InterviewSummaryResponse> InterviewSummaryResponses
) {
}
