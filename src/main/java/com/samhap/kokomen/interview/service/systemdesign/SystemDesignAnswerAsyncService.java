package com.samhap.kokomen.interview.service.systemdesign;

import com.samhap.kokomen.interview.external.SystemDesignAnswerBedrockClient;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerService.SystemDesignAnswerDraft;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 시스템 설계 답변 채점 워커. 요청 스레드는 채점 상태를 LLM_PENDING으로 두고 작업을 맡긴 뒤 바로 돌아간다(202).
 * 워커는 트랜잭션 밖에서 LLM(1~5번째 답변은 L2, 6번째 답변은 L3)을 부르고, 다른 빈의 트랜잭션으로 저장·차감한 뒤 COMPLETED를 쓴다.
 * 어떤 예외든 LLM_FAILED로 끝나며 그때는 아무것도 저장·차감되지 않는다(저장 커밋 뒤 COMPLETED 기록만 실패한 경우는 답변이 남고,
 * 폴링이 DB를 먼저 봐 COMPLETED를 준다). 진행 락은 제출이 실패하면 제출 스레드가, 제출에 성공하면 워커의 finally가 한 번 푼다.
 * LLM_FAILED를 남길 때마다 그 질문의 연속 실패 횟수를 세고, 답변을 저장하면 지운다(SystemDesignProceedFailureCounter).
 *
 * <p>draft의 command에는 숨은 요구사항의 답까지 들어 있으므로 로그에는 식별 값만 남긴다.
 */
@Slf4j
@Service
public class SystemDesignAnswerAsyncService {

    private final SystemDesignAnswerService answerService;
    private final SystemDesignAnswerBedrockClient answerBedrockClient;
    private final SystemDesignProceedStateStore stateStore;
    private final SystemDesignProceedFailureCounter failureCounter;
    private final SystemDesignProceedLock proceedLock;
    private final ThreadPoolTaskExecutor executor;

    public SystemDesignAnswerAsyncService(SystemDesignAnswerService answerService,
                                          SystemDesignAnswerBedrockClient answerBedrockClient,
                                          SystemDesignProceedStateStore stateStore,
                                          SystemDesignProceedFailureCounter failureCounter,
                                          SystemDesignProceedLock proceedLock,
                                          @Qualifier("bedrockFlowCallbackExecutor")
                                          ThreadPoolTaskExecutor bedrockFlowCallbackExecutor) {
        this.answerService = answerService;
        this.answerBedrockClient = answerBedrockClient;
        this.stateStore = stateStore;
        this.failureCounter = failureCounter;
        this.proceedLock = proceedLock;
        this.executor = bedrockFlowCallbackExecutor;
    }

    /**
     * 채점 상태를 LLM_PENDING으로 두고 워커에 맡긴다. 이 실행기에는 TaskDecorator가 없어 제출 스레드의 MDC를 직접 넘긴다.
     * 상태 기록이나 제출이 실패하면(Redis 오류, 큐 포화) LLM_FAILED 기록을 시도하고 락을 푼 뒤 예외를 던지지 않는다.
     * 기존 V2와 달리 LLM_PENDING 기록도 try 안에 두어, Redis 오류 때 락이 TTL까지 남지 않게 한다.
     *
     * @param lockValue 요청 스레드가 잡은 진행 락의 값. 넘긴 뒤에는 이 서비스가 푼다
     */
    public void submit(SystemDesignAnswerDraft draft, String lockValue) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        try {
            stateStore.markPending(draft.interviewId(), draft.questionOrder());
            executor.execute(() -> grade(draft, lockValue, mdcContext));
        } catch (RuntimeException e) {
            log.error("시스템 설계 답변 채점 작업 제출 실패 - interviewId: {}, questionOrder: {}, memberId: {}",
                    draft.interviewId(), draft.questionOrder(), draft.memberId(), e);
            markFailedQuietly(draft);
            proceedLock.release(draft.memberId(), lockValue);
        }
    }

    private void grade(SystemDesignAnswerDraft draft, String lockValue, Map<String, String> mdcContext) {
        try {
            restoreMdc(mdcContext);
            if (draft.isLast()) {
                answerService.saveLastAnswer(draft, answerBedrockClient.evaluateEnd(draft.endCommand()));
            } else {
                answerService.saveAnswer(draft, answerBedrockClient.evaluateAnswer(draft.answerCommand()));
            }
            failureCounter.clear(draft.interviewId(), draft.questionOrder());
            stateStore.markCompleted(draft.interviewId(), draft.questionOrder());
        } catch (Exception e) {
            log.error("시스템 설계 답변 채점 실패 - interviewId: {}, questionOrder: {}, memberId: {}",
                    draft.interviewId(), draft.questionOrder(), draft.memberId(), e);
            markFailedQuietly(draft);
        } finally {
            proceedLock.release(draft.memberId(), lockValue);
            MDC.clear();
        }
    }

    // 실패 기록마저 실패하면(Redis 오류) 상태가 LLM_PENDING이나 없음으로 남는다. 폴링은 TTL이 지나거나 키가 없으면 LLM_FAILED로 본다.
    // 같은 질문의 연속 실패 횟수도 센다. 횟수 기록은 Redis 오류를 삼키므로 락 해제를 막지 않는다.
    private void markFailedQuietly(SystemDesignAnswerDraft draft) {
        try {
            stateStore.markFailed(draft.interviewId(), draft.questionOrder());
        } catch (RuntimeException e) {
            log.warn("시스템 설계 답변 채점 실패 상태 기록 실패 - interviewId: {}, questionOrder: {}",
                    draft.interviewId(), draft.questionOrder(), e);
        }
        failureCounter.recordFailure(draft.interviewId(), draft.questionOrder(), draft.memberId());
    }

    private static void restoreMdc(Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }
}
