package com.samhap.kokomen.interview.external.dto.response;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import java.util.List;
import java.util.Set;

public record InterviewSummaryResponses(
        String intervieweeNickname,
        Long totalMemberCount,
        Long intervieweeRank,
        List<InterviewSummaryResponse> interviewSummaries
) {
    public static InterviewSummaryResponses createOfOtherMemberForLoginMember(
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank,
            List<Interview> interviews,
            Set<Long> likedInterviewIds
    ) {
        List<InterviewSummaryResponse> interviewSummaries = interviews.stream()
                .map(interview -> InterviewSummaryResponse.createOfOtherMemberForLoginMember(
                        interview, likedInterviewIds.contains(interview.getId())))
                .toList();

        return new InterviewSummaryResponses(
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank,
                interviewSummaries
        );
    }

    public static InterviewSummaryResponses createOfOtherMemberForLogoutMember(
            String intervieweeNickname,
            Long totalMemberCount,
            Long intervieweeRank,
            List<Interview> interviews
    ) {
        List<InterviewSummaryResponse> interviewSummaries = interviews.stream()
                .map(interview -> InterviewSummaryResponse.createOfOtherMemberForLogoutMember(interview))
                .toList();

        return new InterviewSummaryResponses(
                intervieweeNickname,
                totalMemberCount,
                intervieweeRank,
                interviewSummaries
        );
    }
}
