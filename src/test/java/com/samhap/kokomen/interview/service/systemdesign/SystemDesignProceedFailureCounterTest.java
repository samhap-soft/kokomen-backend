package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.willThrow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.samhap.kokomen.global.BaseTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignProceedFailureCounterTest extends BaseTest {

    // 면접 id, 질문 순번, 회원 id가 서로 다른 값이어야 둘을 바꿔 넣는 실수가 드러난다.
    private static final Long INTERVIEW_ID = 23L;
    private static final int QUESTION_ORDER = 6;
    private static final Long MEMBER_ID = 7L;
    private static final String REPEATED_FAILURE = "반복 실패";

    @Autowired
    private SystemDesignProceedFailureCounter failureCounter;

    private Logger counterLogger;
    private ListAppender<ILoggingEvent> counterLogs;

    @BeforeEach
    void setUp() {
        counterLogger = (Logger) LoggerFactory.getLogger(SystemDesignProceedFailureCounter.class);
        counterLogs = new ListAppender<>();
        counterLogs.start();
        counterLogger.addAppender(counterLogs);
    }

    @AfterEach
    void tearDown() {
        counterLogger.detachAppender(counterLogs);
    }

    @Test
    void 키는_실패_횟수_접두사와_면접_id와_질문_순번이고_상태_키와_다르다() {
        // when
        String key = SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID, QUESTION_ORDER);

        // then
        assertAll(
                () -> assertThat(key).isEqualTo("system-design:proceed:fail-count:23:6"),
                () -> assertThat(key)
                        .isNotEqualTo(SystemDesignProceedStateStore.createKey(INTERVIEW_ID, QUESTION_ORDER))
        );
    }

    @Test
    void 실패를_기록할_때마다_횟수가_오르고_마지막_실패부터_1시간_뒤_만료된다() {
        // given
        String key = SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID, QUESTION_ORDER);

        // when
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);

        // then
        assertAll(
                () -> assertThat(SystemDesignProceedFailureCounter.TTL).isEqualTo(Duration.ofHours(1)),
                () -> assertThat(readCount(key)).isEqualTo("2"),
                () -> assertThat(remainTimeToLive(key)).isGreaterThan(Duration.ofMinutes(59).toMillis())
                        .isLessThanOrEqualTo(Duration.ofHours(1).toMillis())
        );
    }

    // 사용자 동작은 바꾸지 않는다. 세 번째 실패부터 실패할 때마다 운영 알림(ERROR) 한 줄을 남긴다.
    @Test
    void 같은_질문에서_세_번째_실패부터_실패할_때마다_반복_실패_ERROR를_한_줄_남긴다() {
        // when
        for (int attempt = 1; attempt <= 4; attempt++) {
            failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);
        }

        // then
        assertThat(errorMessages()).satisfiesExactly(
                message -> assertThat(message).contains(REPEATED_FAILURE, "interviewId: 23", "questionOrder: 6",
                        "memberId: 7", "failCount: 3"),
                message -> assertThat(message).contains(REPEATED_FAILURE, "interviewId: 23", "questionOrder: 6",
                        "memberId: 7", "failCount: 4"));
    }

    @Test
    void 다른_질문과_다른_면접의_실패는_따로_센다() {
        // when
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER - 1, MEMBER_ID);
        failureCounter.recordFailure(INTERVIEW_ID + 1, QUESTION_ORDER, MEMBER_ID);

        // then
        assertAll(
                () -> assertThat(readCount(SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID, QUESTION_ORDER)))
                        .isEqualTo("2"),
                () -> assertThat(readCount(SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID,
                        QUESTION_ORDER - 1))).isEqualTo("1"),
                () -> assertThat(readCount(SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID + 1,
                        QUESTION_ORDER))).isEqualTo("1"),
                () -> assertThat(errorMessages()).isEmpty()
        );
    }

    @Test
    void 지우면_키가_없어지고_다음_실패는_1부터_센다() {
        // given
        String key = SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID, QUESTION_ORDER);
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);

        // when
        failureCounter.clear(INTERVIEW_ID, QUESTION_ORDER);
        boolean existsAfterClear = redisTemplate.hasKey(key);
        failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID);

        // then
        assertAll(
                () -> assertThat(existsAfterClear).isFalse(),
                () -> assertThat(readCount(key)).isEqualTo("1")
        );
    }

    // 실패 횟수는 운영 신호일 뿐이다. Redis 오류가 채점 결과 기록(LLM_FAILED·COMPLETED)과 락 해제를 막지 않게 WARN만 남긴다.
    @Test
    void Redis가_실패해도_예외를_던지지_않고_WARN만_남긴다() {
        // given
        String key = SystemDesignProceedFailureCounter.createKey(INTERVIEW_ID, QUESTION_ORDER);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient).getAtomicLong(key);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient).getKeys();

        // when & then
        assertAll(
                () -> assertThatCode(() -> failureCounter.recordFailure(INTERVIEW_ID, QUESTION_ORDER, MEMBER_ID))
                        .doesNotThrowAnyException(),
                () -> assertThatCode(() -> failureCounter.clear(INTERVIEW_ID, QUESTION_ORDER))
                        .doesNotThrowAnyException(),
                () -> assertThat(counterLogs.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(2),
                () -> assertThat(errorMessages()).isEmpty()
        );
    }

    private List<String> errorMessages() {
        return counterLogs.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private String readCount(String key) {
        return (String) redissonClient.getBucket(key, StringCodec.INSTANCE).get();
    }

    private long remainTimeToLive(String key) {
        return redissonClient.getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
    }
}
