package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.RedisException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewViewCountService {

    public static final String INTERVIEW_VIEW_COUNT_LOCK_KEY_PREFIX = "lock:interview:viewCount:";
    public static final String INTERVIEW_VIEW_COUNT_KEY_PREFIX = "interview:viewCount:";

    private final RedisService redisService;
    private final ApplicationEventPublisher eventPublisher;

    @Retryable(recover = "recoverIncrementViewCount", retryFor = RedisException.class, maxAttempts = 1)
    public Long incrementViewCount(Interview interview, MemberAuth memberAuth, ClientIp clientIp) {
        if (isInterviewee(memberAuth, interview)) {
            return findViewCount(interview);
        }

        String viewCountLockKey = createInterviewViewCountLockKey(interview, clientIp);
        if (!redisService.acquireLock(viewCountLockKey, Duration.ofDays(1))) {
            return findViewCount(interview);
        }

        String viewCountKey = createInterviewViewCountKey(interview);
        boolean expireSuccess = redisService.expireKey(viewCountKey, Duration.ofDays(2));
        if (!expireSuccess) {
            redisService.setIfAbsent(viewCountKey, String.valueOf(interview.getViewCount()), Duration.ofDays(2));
        }
        return redisService.incrementKey(viewCountKey);
    }

    private boolean isInterviewee(MemberAuth memberAuth, Interview interview) {
        return memberAuth.isAuthenticated() && interview.isInterviewee(memberAuth.memberId());
    }

    public String createInterviewViewCountLockKey(Interview interview, ClientIp clientIp) {
        return INTERVIEW_VIEW_COUNT_LOCK_KEY_PREFIX + interview.getId() + ":" + clientIp.address();
    }

    public String createInterviewViewCountKey(Interview interview) {
        return INTERVIEW_VIEW_COUNT_KEY_PREFIX + interview.getId();
    }

    @Recover
    public Long recoverIncrementViewCount(RedisException e, Interview interview, MemberAuth memberAuth,
                                          ClientIp clientIp) {
        log.error("Redis 조회수 업데이트 실패", e);
        return interview.getViewCount();
    }

    @Retryable(recover = "recoverFindViewCount", retryFor = RedisException.class, maxAttempts = 1)
    public Long findViewCount(Interview interview) {
        return redisService.get(createInterviewViewCountKey(interview), String.class)
                .map(Long::valueOf)
                .orElse(interview.getViewCount());
    }

    @Recover
    public Long recoverFindViewCount(RedisException e, Interview interview) {
        log.error("Redis 조회수 조회 실패", e);
        return interview.getViewCount();
    }
}
