package com.samhap.kokomen.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class InterviewLikeEventConsumer {

    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "#{environment.getProperty('spring.profiles.active', 'local') + '-interview-like'}",
            containerFactory = "kafkaBatchListenerContainerFactory"
    )
    public void consumeBatch(List<ConsumerRecord<String, String>> records) {
        if (records.isEmpty()) {
            return;
        }

        // interviewId별로 갯수 세기
        Map<Long, Long> interviewLikeCountMap = new HashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            try {
                Map<String, Object> payload = objectMapper.readValue(record.value(), Map.class);
                Long interviewId = Long.valueOf(payload.get("interviewId").toString());
                interviewLikeCountMap.put(interviewId, interviewLikeCountMap.getOrDefault(interviewId, 0L) + 1);
            } catch (Exception e) {
                log.error("Kafka 메시지 파싱 실패: {}", record.value(), e);
            }
        }

        // interviewId별로 likeCount만큼 한 번에 증가
        for (Map.Entry<Long, Long> entry : interviewLikeCountMap.entrySet()) {
            Long interviewId = entry.getKey();
            Long count = entry.getValue();
            interviewRepository.increaseLikeCountModifying(interviewId, count);
            log.info("인터뷰 좋아요 카운트 증가: interviewId={}, count={}", interviewId, count);
        }
    }
}
