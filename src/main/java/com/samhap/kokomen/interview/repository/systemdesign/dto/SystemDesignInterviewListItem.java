package com.samhap.kokomen.interview.repository.systemdesign.dto;

/**
 * 면접 목록의 시스템 설계 행에 보일 값. interviewId는 부모 interview 행의 id이고, answerCount는 저장된 시스템 설계 답변 수다.
 */
public record SystemDesignInterviewListItem(
        Long interviewId,
        String problemTitle,
        Long answerCount
) {
}
