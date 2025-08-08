package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.repository.InterviewBatchRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewSchedulerService {

    private static final String INTERVIEW_VIEW_COUNT_SCHEDULER_LOCK = "lock:interview:viewCount:scheduler";
    private static final String INTERVIEW_VIEW_COUNT_KEY_PATTERN = InterviewViewCountService.INTERVIEW_VIEW_COUNT_KEY_PREFIX + "*";
    private static final int REDIS_INTERVIEW_VIEW_COUNT_BATCH_SIZE = 100;
    private static final int DB_INTERVIEW_VIEW_COUNT_BATCH_SIZE = 1_000;

    private final RedisService redisService;
    private final InterviewBatchRepository interviewBatchRepository;

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void syncInterviewViewCounts() {
        boolean lockAcquired = redisService.acquireLock(INTERVIEW_VIEW_COUNT_SCHEDULER_LOCK, Duration.ofHours(6));
        if (!lockAcquired) {
            return;
        }

        try (Cursor<String> cursor = redisService.scanKeys(INTERVIEW_VIEW_COUNT_KEY_PATTERN, REDIS_INTERVIEW_VIEW_COUNT_BATCH_SIZE)) {
            syncInterviewViewCounts(cursor);
        } catch (Exception e) {
            log.error("인터뷰 조회수를 DB에 반영하는 스케줄러 동작 중 에러 발생", e);
            redisService.releaseLock(INTERVIEW_VIEW_COUNT_SCHEDULER_LOCK);
        }
    }

    private void syncInterviewViewCounts(Cursor<String> cursor) {
        List<String> keys = new ArrayList<>();
        Map<Long, Long> interviewViewCounts = new HashMap<>();

        while (cursor.hasNext()) {
            keys.add(cursor.next());
            processBatchesIfReady(keys, interviewViewCounts);
        }
        putInterviewViewCounts(keys, interviewViewCounts);
        batchUpdateInterviewViewCounts(interviewViewCounts);
    }

    private void processBatchesIfReady(List<String> keys, Map<Long, Long> interviewViewCounts) {
        if (keys.size() >= REDIS_INTERVIEW_VIEW_COUNT_BATCH_SIZE) {
            putInterviewViewCounts(keys, interviewViewCounts);
        }
        if (interviewViewCounts.size() >= DB_INTERVIEW_VIEW_COUNT_BATCH_SIZE) {
            batchUpdateInterviewViewCounts(interviewViewCounts);
        }
    }

    private void putInterviewViewCounts(List<String> keys, Map<Long, Long> interviewViewCounts) {
        Map<String, Object> keyAndValues = redisService.multiGet(keys);
        interviewViewCounts.putAll(convertToInterviewViewCounts(keyAndValues));
        keys.clear();
    }

    private void batchUpdateInterviewViewCounts(Map<Long, Long> interviewViewCounts) {
        interviewBatchRepository.batchUpdateInterviewViewCount(interviewViewCounts, DB_INTERVIEW_VIEW_COUNT_BATCH_SIZE);
        interviewViewCounts.clear();
    }

    private Map<Long, Long> convertToInterviewViewCounts(Map<String, Object> keyAndValues) {
        return keyAndValues.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> extractInterviewId(entry.getKey()),
                        entry -> Long.parseLong(entry.getValue().toString())
                ));
    }

    private Long extractInterviewId(String key) {
        return Long.parseLong(key.replace(InterviewViewCountService.INTERVIEW_VIEW_COUNT_KEY_PREFIX, ""));
    }
}
