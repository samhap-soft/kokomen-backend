package com.samhap.kokomen.global;


import com.samhap.kokomen.auth.external.GoogleOAuthClient;
import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.interview.external.AnswerFeedbackBedrockClient;
import com.samhap.kokomen.interview.external.InterviewProceedBedrockClient;
import com.samhap.kokomen.interview.external.InterviewProceedGptClient;
import com.samhap.kokomen.interview.external.ResumeBasedQuestionBedrockService;
import com.samhap.kokomen.interview.external.ResumeBasedQuestionGptClient;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.interview.service.question.QuestionGenerationAsyncService;
import com.samhap.kokomen.payment.external.TosspaymentsClient;
import com.samhap.kokomen.resume.external.ResumeEvaluationBedrockClient;
import com.samhap.kokomen.resume.external.ResumeEvaluationGptClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
    protected TosspaymentsClient tosspaymentsClient;
    @MockitoBean
    protected InterviewProceedGptClient interviewProceedGptClient;
    @MockitoBean
    protected InterviewProceedBedrockClient interviewProceedBedrockClient;
    @MockitoBean
    protected AnswerFeedbackBedrockClient answerFeedbackBedrockClient;
    @MockitoBean
    protected ResumeEvaluationBedrockClient resumeEvaluationBedrockClient;
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
    @MockitoSpyBean
    protected RedissonClient redissonClient;
    @Autowired
    private MySQLDatabaseCleaner mySQLDatabaseCleaner;

    @Autowired
    private RedisCleaner redisCleaner;

    @BeforeEach
    void baseTestSetUp() {
        redisCleaner.clearAllRedisData();
    }
}
