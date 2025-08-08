package com.samhap.kokomen.interview.service.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record InterviewLikeEventPayload(
        Long interviewId,
        Long receiverMemberId,
        Long likerMemberId,
        Long likeCount
) {

    public String parseToString(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("payload를 JSON으로 파싱하는데 실패했습니다.", e);
        }
    }
}
