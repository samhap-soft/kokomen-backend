package com.samhap.kokomen.interview.service.systemdesign;

import com.samhap.kokomen.global.service.RedisService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 같은 면접·같은 질문의 채점 실패(LLM_FAILED)가 연달아 쌓이는 것을 센다(Redis). 실패마다 1을 더하고, 그 질문의 채점이 성공하면 지운다.
 * 키는 면접 id와 질문 순번이고 마지막 실패부터 {@link #TTL} 뒤에 사라진다. 사용자 동작은 바꾸지 않는다(다시 내는 것을 막지 않는다).
 * {@value #REPEATED_FAILURE_THRESHOLD}번째 실패부터는 실패할 때마다 ERROR 한 줄("반복 실패")을 남겨, 마지막 답변 평가가 잘림으로 거듭
 * 실패하는 것처럼 면접자가 그 질문을 넘길 수 없는 상황을 운영에서 알아채게 한다.
 *
 * <p>운영 신호일 뿐이라 Redis 오류는 WARN만 남기고 삼킨다. 채점 결과 기록(LLM_FAILED·COMPLETED)과 진행 락 해제를 막지 않는다.
 * 횟수를 올린 뒤 만료 설정만 실패하면 이미 아는 횟수로 반복 실패 알림은 남긴다. 횟수를 올린 응답을 잃거나 만료 설정이 실패하면 키가
 * TTL 없이 남을 수 있고, 그 질문의 다음 실패(TTL을 다시 건다)나 성공(지운다) 때 정리된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SystemDesignProceedFailureCounter {

    public static final String KEY_PREFIX = "system-design:proceed:fail-count:";
    public static final Duration TTL = Duration.ofHours(1);
    public static final int REPEATED_FAILURE_THRESHOLD = 3;

    private final RedisService redisService;

    public static String createKey(Long interviewId, int questionOrder) {
        return KEY_PREFIX + interviewId + ":" + questionOrder;
    }

    public void recordFailure(Long interviewId, int questionOrder, Long memberId) {
        String key = createKey(interviewId, questionOrder);
        long failCount;
        try {
            failCount = redisService.incrementKey(key);
        } catch (RuntimeException e) {
            log.warn("시스템 설계 답변 채점 실패 횟수 기록 실패 - interviewId: {}, questionOrder: {}", interviewId,
                    questionOrder, e);
            return;
        }
        if (failCount >= REPEATED_FAILURE_THRESHOLD) {
            log.error("시스템 설계 답변 채점 반복 실패 - interviewId: {}, questionOrder: {}, memberId: {}, failCount: {}",
                    interviewId, questionOrder, memberId, failCount);
        }
        try {
            redisService.expireKey(key, TTL);
        } catch (RuntimeException e) {
            log.warn("시스템 설계 답변 채점 실패 횟수 만료 설정 실패 - interviewId: {}, questionOrder: {}", interviewId,
                    questionOrder, e);
        }
    }

    public void clear(Long interviewId, int questionOrder) {
        try {
            redisService.deleteKey(createKey(interviewId, questionOrder));
        } catch (RuntimeException e) {
            log.warn("시스템 설계 답변 채점 실패 횟수 삭제 실패 - interviewId: {}, questionOrder: {}", interviewId,
                    questionOrder, e);
        }
    }
}
