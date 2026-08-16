package com.samhap.kokomen.global;


import com.samhap.kokomen.auth.external.GoogleOAuthClient;
import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.interview.external.AnswerFeedbackBedrockClient;
import com.samhap.kokomen.interview.external.InterviewProceedBedrockClient;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.payment.external.TosspaymentsClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisEvaluationBedrockClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisQuestionBedrockClient;
import com.samhap.kokomen.resume.service.ResumeAnalysisAsyncService;
import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import com.samhap.kokomen.resume.tool.PdfValidator;
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

    // 여기 있는 타입을 하위 클래스에서 다시 @MockitoBean으로 선언하지 않는다.
    // 같은 이름으로 선언하면 Duplicate BeanOverrideHandler로 컨텍스트 기동이 즉시 실패하므로 바로 드러나지만,
    // 다른 이름으로 선언하면 기동은 성공하고 한 빈에 타입 기준 오버라이드가 둘 붙는다. 이때 상속 필드와 로컬 필드가
    // 같은 목 인스턴스를 본다는 보장이 없어, 스텁을 건 쪽과 실제로 호출되는 쪽이 어긋나도 조용히 통과한다.
    // 목이 더 필요하면 이 클래스에 추가하고, 특정 테스트에만 필요한 목은 그 클래스에만 있는 타입으로 한정한다.
    @MockitoBean
    protected SupertoneClient supertoneClient;
    @MockitoBean
    protected S3Client s3Client;
    @MockitoBean
    protected TosspaymentsClient tosspaymentsClient;
    @MockitoBean
    protected InterviewProceedBedrockClient interviewProceedBedrockClient;
    @MockitoBean
    protected AnswerFeedbackBedrockClient answerFeedbackBedrockClient;
    @MockitoBean
    protected KakaoOAuthClient kakaoOAuthClient;
    @MockitoBean
    protected GoogleOAuthClient googleOAuthClient;
    @MockitoBean
    protected ResumeAnalysisEvaluationBedrockClient resumeAnalysisEvaluationBedrockClient;
    @MockitoBean
    protected ResumeAnalysisQuestionBedrockClient resumeAnalysisQuestionBedrockClient;
    @MockitoBean
    protected ResumeAnalysisAsyncService resumeAnalysisAsyncService;
    @MockitoBean
    protected PdfValidator pdfValidator;
    @MockitoBean
    protected PdfTextExtractor pdfTextExtractor;
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
