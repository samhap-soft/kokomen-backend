package com.samhap.kokomen.interview.service.systemdesign;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 시스템 설계 답변 채점 상태(Redis). 키는 면접 id와 질문 순번(1~6)이고, 기존 면접의 진행 상태 키(질문 PK 기준)와 접두사를 나눠
 * 기존 폴링이 이 키를 읽지 않는다. 값은 LLM_PENDING·LLM_FAILED·COMPLETED만 쓴다.
 *
 * <p>워커가 답변 저장을 커밋한 뒤에 COMPLETED를 쓰므로, 답변이 저장됐는지는 이 값이 아니라 DB가 정한다. 폴링은 DB를 먼저 본다.
 */
@RequiredArgsConstructor
@Component
public class SystemDesignProceedStateStore {

    public static final String KEY_PREFIX = "system-design:proceed:state:";
    public static final Duration TTL = Duration.ofSeconds(300);

    private final RedisService redisService;

    public static String createKey(Long interviewId, int questionOrder) {
        return KEY_PREFIX + interviewId + ":" + questionOrder;
    }

    public void markPending(Long interviewId, int questionOrder) {
        mark(interviewId, questionOrder, InterviewProceedState.LLM_PENDING);
    }

    public void markCompleted(Long interviewId, int questionOrder) {
        mark(interviewId, questionOrder, InterviewProceedState.COMPLETED);
    }

    public void markFailed(Long interviewId, int questionOrder) {
        mark(interviewId, questionOrder, InterviewProceedState.LLM_FAILED);
    }

    private void mark(Long interviewId, int questionOrder, InterviewProceedState state) {
        redisService.setValue(createKey(interviewId, questionOrder), state.name(), TTL);
    }

    /**
     * @return 기록한 상태. 기록하지 않았거나 TTL이 지났으면 비어 있다
     */
    public Optional<InterviewProceedState> find(Long interviewId, int questionOrder) {
        return redisService.get(createKey(interviewId, questionOrder), String.class)
                .map(InterviewProceedState::valueOf);
    }
}
