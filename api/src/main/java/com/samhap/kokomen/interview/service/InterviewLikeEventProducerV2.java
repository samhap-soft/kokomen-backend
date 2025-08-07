package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InterviewLikeEventProducerV2 {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public InterviewLikeEventProducerV2(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        String[] profiles = environment.getActiveProfiles();
        String activeProfile = (profiles.length > 0) ? profiles[0] : "test";
        this.topic = String.format("%s-interview-like-v2", activeProfile);
    }

    public void sendLikeEvent(Long interviewId, Long receiverMemberId, Long likerMemberId, Long likeCount) {
        String key = String.valueOf(interviewId);
        Map<String, Object> payload = Map.of(
                "interviewId", interviewId,
                "receiverMemberId", receiverMemberId,
                "likerMemberId", likerMemberId,
                "likeCount", likeCount
        );
        String message = parsePayload(payload);
        try {
            // 동기 전송: 카프카 브로커에 메시지가 정상적으로 저장될 때까지 대기
            kafkaTemplate.send(topic, key, message).get();
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 메시지 전송에 실패했습니다.", e);
        }
    }

    private String parsePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("payload를 JSON으로 파싱하는데 실패했습니다.", e);
        }
    }
}
