package com.samhap.kokomen.global;


import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.external.NotificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public abstract class BaseTest {

    @MockitoBean
    protected NotificationClient notificationClient;
    @MockitoBean
    protected GptClient gptClient;
    @MockitoBean
    protected KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean
    protected BedrockClient bedrockClient;
    @MockitoBean
    protected KakaoOAuthClient kakaoOAuthClient;
    @MockitoSpyBean
    protected RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private MySQLDatabaseCleaner mySQLDatabaseCleaner;

    @Autowired
    private RedisCleaner redisCleaner;

    @BeforeEach
    void baseTestSetUp() {
        mySQLDatabaseCleaner.executeTruncate();
        redisCleaner.clearAllRedisData();
    }
}
