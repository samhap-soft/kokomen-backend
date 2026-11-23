package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignInterviewService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignProceedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시스템 설계 면접 API의 진입점. 트랜잭션은 아래 서비스가 열고, 여기서는 진행 락처럼 트랜잭션 밖에서 해야 하는 순서를 맞춘다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignInterviewFacadeService {

    private final SystemDesignInterviewService systemDesignInterviewService;
    private final SystemDesignProceedLock systemDesignProceedLock;

    // 기존 시작 API처럼 진행 락을 잡지 않는다.
    public SystemDesignInterviewStartResponse startInterview(MemberAuth memberAuth) {
        return systemDesignInterviewService.start(memberAuth.memberId());
    }

    public SystemDesignInterviewResponse findInterview(Long interviewId, MemberAuth memberAuth) {
        return systemDesignInterviewService.findInterview(interviewId, memberAuth.memberId());
    }

    // 사전 검사(소유자 → 단계) → 진행 락 → 완료. 완료 트랜잭션은 락을 풀기 전에 커밋되므로 다음 요청은 바뀐 단계를 본다.
    public SystemDesignRequirementsCompleteResponse completeRequirements(Long interviewId, MemberAuth memberAuth) {
        Long memberId = memberAuth.memberId();
        systemDesignInterviewService.validateRequirementsCompletable(interviewId, memberId);
        String lockValue = systemDesignProceedLock.acquire(memberId);
        try {
            return systemDesignInterviewService.completeRequirements(interviewId, memberId);
        } finally {
            systemDesignProceedLock.release(memberId, lockValue);
        }
    }
}
