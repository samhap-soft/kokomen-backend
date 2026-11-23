package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시스템 설계 면접 API의 진입점. 트랜잭션은 아래 서비스가 연다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignInterviewFacadeService {

    private final SystemDesignInterviewService systemDesignInterviewService;

    // 기존 시작 API처럼 진행 락을 잡지 않는다.
    public SystemDesignInterviewStartResponse startInterview(MemberAuth memberAuth) {
        return systemDesignInterviewService.start(memberAuth.memberId());
    }
}
