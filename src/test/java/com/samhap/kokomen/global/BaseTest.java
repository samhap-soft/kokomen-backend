package com.samhap.kokomen.global;


import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.GptClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public abstract class BaseTest {

    @MockitoBean
    protected GptClient gptClient;
    @MockitoBean
    protected BedrockClient bedrockClient;
    @MockitoBean
    protected KakaoOAuthClient kakaoOAuthClient;
    @Autowired
    private MySQLDatabaseCleaner mySQLDatabaseCleaner;

    @BeforeEach
    void baseTestSetUp() {
        mySQLDatabaseCleaner.executeTruncate();
    }
}
