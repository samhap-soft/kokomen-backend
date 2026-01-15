package com.samhap.kokomen.global;


import com.samhap.kokomen.auth.external.GoogleOAuthClient;
import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.InterviewProceedGptClient;
import com.samhap.kokomen.interview.external.ResumeBasedQuestionBedrockService;
import com.samhap.kokomen.interview.external.ResumeBasedQuestionGptClient;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.interview.service.QuestionGenerationAsyncService;
import com.samhap.kokomen.resume.external.ResumeEvaluationGptClient;
import com.samhap.kokomen.token.external.PaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

@ActiveProfiles("test")
@ExtendWith(MySQLDatabaseCleaner.class)
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public abstract class BaseTest {

    @MockitoBean
    protected SupertoneClient supertoneClient;
    @MockitoBean
    protected S3Client s3Client;
    @MockitoBean
    protected PaymentClient paymentClient;
    @MockitoBean
    protected InterviewProceedGptClient interviewProceedGptClient;
    @MockitoBean
    protected KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean
    protected BedrockClient bedrockClient;
    @MockitoBean
    protected BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    @MockitoBean
    protected KakaoOAuthClient kakaoOAuthClient;
    @MockitoBean
    protected GoogleOAuthClient googleOAuthClient;
    @MockitoBean
    protected ResumeEvaluationGptClient resumeEvaluationGptClient;
    @MockitoBean
    protected ResumeBasedQuestionGptClient resumeBasedQuestionGptClient;
    @MockitoBean
    protected ResumeBasedQuestionBedrockService resumeBasedQuestionBedrockService;
    @MockitoBean
    protected QuestionGenerationAsyncService questionGenerationAsyncService;
    @MockitoSpyBean
    protected RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private MySQLDatabaseCleaner mySQLDatabaseCleaner;

    @Autowired
    private RedisCleaner redisCleaner;

    @BeforeEach
    void baseTestSetUp() {
        redisCleaner.clearAllRedisData();
    }
}
