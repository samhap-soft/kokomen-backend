package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.interview.service.dto.InterviewLikeEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InterviewLikeEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public InterviewLikeEventProducer(
            @Qualifier("interviewLikeKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        String[] profiles = environment.getActiveProfiles();
        String activeProfile = (profiles.length > 0) ? profiles[0] : "unknown";
        this.topic = String.format("%s-interview-like", activeProfile);
    }

    public void sendLikeEvent(Long interviewId, Long receiverMemberId, Long likerMemberId, Long likeCount) {
        String key = String.valueOf(interviewId);
        InterviewLikeEventPayload payload = new InterviewLikeEventPayload(interviewId, receiverMemberId, likerMemberId, likeCount);
        String message = payload.parseToString(objectMapper);
        try {
            // 동기 전송: 카프카 브로커에 메시지가 정상적으로 저장될 때까지 대기
            kafkaTemplate.send(topic, key, message).get();
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 메시지 전송에 실패했습니다.", e);
        }
    }
}
