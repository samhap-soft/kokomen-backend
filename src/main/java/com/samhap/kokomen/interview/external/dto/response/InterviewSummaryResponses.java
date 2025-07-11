package com.samhap.kokomen.interview.external.dto.response;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record InterviewSummaryResponses(
        List<InterviewSummaryResponse> interviewSummaries,
        Long totalMemberCount,
        Long intervieweeRank,
        String intervieweeNickname,
        Long totalPageCount
) {
    public static InterviewSummaryResponses createOfOtherMemberForAuthorized(
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank,
            List<Interview> interviews,
            Set<Long> likedInterviewIds,
            Map<Long, Long> viewCounts,
            Long totalPageCount
    ) {
        List<InterviewSummaryResponse> interviewSummaries = interviews.stream()
                .map(interview -> InterviewSummaryResponse.createOfOtherMemberForAuthorized(interview, viewCounts.get(interview.getId()),
                        likedInterviewIds.contains(interview.getId())))
                .toList();

        return new InterviewSummaryResponses(
                interviewSummaries, totalMemberCount, intervieweeRank, intervieweeNickname, totalPageCount
        );
    }

    public static InterviewSummaryResponses createOfOtherMemberForUnAuthorized(
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank,
            List<Interview> interviews,
            Map<Long, Long> viewCounts,
            Long pageCount
    ) {
        List<InterviewSummaryResponse> interviewSummaries = interviews.stream()
                .map(interview -> InterviewSummaryResponse.createOfOtherMemberForUnauthorized(interview, viewCounts.get(interview.getId())))
                .toList();

        return new InterviewSummaryResponses(
                interviewSummaries, totalMemberCount, intervieweeRank, intervieweeNickname, pageCount
        );
    }
}
