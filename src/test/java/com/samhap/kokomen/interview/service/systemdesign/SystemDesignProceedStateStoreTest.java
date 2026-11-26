package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.willThrow;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.RedisException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignProceedStateStoreTest extends BaseTest {

    // 면접 id와 질문 순번이 서로 다른 값이어야 둘을 바꿔 넣는 실수가 드러난다.
    private static final Long INTERVIEW_ID = 17L;
    private static final int QUESTION_ORDER = 3;

    @Autowired
    private SystemDesignProceedStateStore stateStore;

    @Test
    void 키는_시스템_설계_접두사와_면접_id와_질문_순번이다() {
        // when
        String key = SystemDesignProceedStateStore.createKey(INTERVIEW_ID, QUESTION_ORDER);

        // then
        assertAll(
                () -> assertThat(key).isEqualTo("system-design:proceed:state:17:3"),
                // 기존 면접의 진행 상태 키(질문 PK 기준)와 접두사가 달라 기존 폴링이 이 키를 읽지 않는다.
                () -> assertThat(key).doesNotStartWith(InterviewProceedFacadeService.INTERVIEW_PROCEED_STATE_KEY_PREFIX)
        );
    }

    @Test
    void 기록한_상태를_읽는다() {
        // when & then
        stateStore.markPending(INTERVIEW_ID, QUESTION_ORDER);
        assertThat(stateStore.find(INTERVIEW_ID, QUESTION_ORDER)).contains(InterviewProceedState.LLM_PENDING);

        stateStore.markFailed(INTERVIEW_ID, QUESTION_ORDER);
        assertThat(stateStore.find(INTERVIEW_ID, QUESTION_ORDER)).contains(InterviewProceedState.LLM_FAILED);

        stateStore.markCompleted(INTERVIEW_ID, QUESTION_ORDER);
        assertAll(
                () -> assertThat(stateStore.find(INTERVIEW_ID, QUESTION_ORDER))
                        .contains(InterviewProceedState.COMPLETED),
                // 같은 면접의 다른 질문과 다른 면접의 같은 순번에는 기록하지 않는다.
                () -> assertThat(stateStore.find(INTERVIEW_ID, QUESTION_ORDER + 1)).isEmpty(),
                () -> assertThat(stateStore.find(INTERVIEW_ID + 1, QUESTION_ORDER)).isEmpty()
        );
    }

    @Test
    void 기록하지_않은_상태는_비어_있다() {
        assertThat(stateStore.find(INTERVIEW_ID, QUESTION_ORDER)).isEmpty();
    }

    @Test
    void 상태는_5분_뒤_만료된다() {
        // given
        String key = SystemDesignProceedStateStore.createKey(INTERVIEW_ID, QUESTION_ORDER);

        // when & then
        stateStore.markPending(INTERVIEW_ID, QUESTION_ORDER);
        long pendingTimeToLive = remainTimeToLive(key);
        stateStore.markFailed(INTERVIEW_ID, QUESTION_ORDER);
        long failedTimeToLive = remainTimeToLive(key);
        stateStore.markCompleted(INTERVIEW_ID, QUESTION_ORDER);
        long completedTimeToLive = remainTimeToLive(key);
        assertAll(
                () -> assertThat(SystemDesignProceedStateStore.TTL).isEqualTo(Duration.ofMinutes(5)),
                () -> assertThat(pendingTimeToLive).isGreaterThan(Duration.ofMinutes(4).toMillis())
                        .isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis()),
                () -> assertThat(failedTimeToLive).isGreaterThan(Duration.ofMinutes(4).toMillis())
                        .isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis()),
                () -> assertThat(completedTimeToLive).isGreaterThan(Duration.ofMinutes(4).toMillis())
                        .isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis())
        );
    }

    // 답변 결과 조회가 Redis를 읽지 못하면 내부 문구(레디스 예외 발생)의 500 대신 사용자 문구의 503으로 알린다.
    @Test
    void 조회할_때_Redis가_실패하면_사용자_문구의_503이고_원인은_레디스_예외다() {
        // given
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(SystemDesignProceedStateStore.createKey(INTERVIEW_ID, QUESTION_ORDER), StringCodec.INSTANCE);

        // when & then
        assertThatThrownBy(() -> stateStore.find(INTERVIEW_ID, QUESTION_ORDER))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("일시적인 문제로 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.")
                .hasCauseInstanceOf(RedisException.class);
    }

    private long remainTimeToLive(String key) {
        return redissonClient.getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
    }
}
