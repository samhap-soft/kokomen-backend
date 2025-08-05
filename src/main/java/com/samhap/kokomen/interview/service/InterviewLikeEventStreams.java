package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InterviewLikeEventStreams {

    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;
    private final StreamsConfig streamsConfig;
    private final String topic;

    public InterviewLikeEventStreams(
            InterviewRepository interviewRepository,
            ObjectMapper objectMapper,
            StreamsConfig streamsConfig,
            Environment environment
    ) {
        this.interviewRepository = interviewRepository;
        this.objectMapper = objectMapper;
        this.streamsConfig = streamsConfig;
        String[] profiles = environment.getActiveProfiles();
        String activeProfile = (profiles.length > 0) ? profiles[0] : "test";
        this.topic = String.format("%s-interview-like", activeProfile);
    }

    @PostConstruct
    public void startKafkaStreams() {
        StreamsBuilder builder = new StreamsBuilder();

        //
        log.info("Kafka Streams 시작: topic = {}", topic);
        //
        KStream<String, String> stream = builder.stream(topic);

        KTable<String, String> latestLikeTable = stream
                .groupByKey()
                .reduce((oldVal, newVal) -> newVal, Materialized.as("interview-like-store"));

        latestLikeTable.toStream().foreach((key, value) -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(value, Map.class);
                Long interviewId = Long.valueOf(payload.get("interviewId").toString());
                Long likeCount = Long.valueOf(payload.get("likeCount").toString());

                interviewRepository.updateLikeCount(interviewId, likeCount);
                log.info("Streams 기반 DB 업데이트 완료: interviewId={}, likeCount={}", interviewId, likeCount);

            } catch (Exception e) {
                log.error("Kafka Streams 값 파싱 실패: {}", value, e);
            }
        });

        KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfig);
        streams.setUncaughtExceptionHandler((t, e) -> log.error("KafkaStreams 예외 발생", e));
        streams.start();
    }
}
