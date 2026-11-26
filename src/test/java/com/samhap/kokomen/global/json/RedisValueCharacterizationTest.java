package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.service.RedisService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

/**
 * Redis에 쓰는 값의 바이트 형식을 고정한다. 세션은 JDK 직렬화, RedisService(Redisson StringCodec)와 RedisTemplate은 문자열 그대로다.
 * 어느 쪽도 Jackson을 거치지 않으므로 Jackson을 바꿔도 이미 저장된 세션과 값을 그대로 읽어야 한다.
 */
class RedisValueCharacterizationTest extends BaseTest {

    private static final byte[] JDK_SERIALIZATION_MAGIC = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05};

    @Autowired
    private RedisSessionRepository sessionRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private RedisService redisService;

    @Test
    void 세션_속성은_JDK_직렬화로_저장한다() {
        String sessionId = saveSessionWithMemberId(sessionRepository, 7L);

        byte[] stored = hashValue("spring:session:sessions:" + sessionId, "sessionAttr:MEMBER_ID");

        assertThat(Arrays.copyOf(stored, JDK_SERIALIZATION_MAGIC.length)).isEqualTo(JDK_SERIALIZATION_MAGIC);
        assertThat(new JdkSerializationRedisSerializer().deserialize(stored)).isEqualTo(7L);
    }

    @Test
    void RedisService_값과_RedisTemplate_값은_UTF8_문자열_바이트로_저장한다() {
        redisService.setValue("characterization:redis-service", "값 😀 {\"a\":1}", Duration.ofMinutes(1));
        redisTemplate.opsForValue().set("characterization:redis-template", "값 😀 {\"a\":1}");

        byte[] expected = "값 😀 {\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(value("characterization:redis-service")).isEqualTo(expected);
        assertThat(value("characterization:redis-template")).isEqualTo(expected);
    }

    // RedisSessionRepository의 세션 타입은 공개 타입이 아니라서 Session 인터페이스로 다룬다.
    private static <S extends Session> String saveSessionWithMemberId(SessionRepository<S> repository, Long memberId) {
        S session = repository.createSession();
        session.setAttribute("MEMBER_ID", memberId);
        repository.save(session);
        return session.getId();
    }

    private byte[] hashValue(String key, String field) {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return connection.hashCommands().hGet(key.getBytes(StandardCharsets.UTF_8),
                    field.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] value(String key) {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
        }
    }
}
