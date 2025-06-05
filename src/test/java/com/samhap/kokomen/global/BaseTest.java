package com.samhap.kokomen.global;


import com.samhap.kokomen.interview.external.GptClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public abstract class BaseTest {

    @MockitoBean
    protected GptClient gptClient;
    @Autowired
    private MySQLDatabaseCleaner mySQLDatabaseCleaner;

    @BeforeEach
    void baseTestSetUp() {
        mySQLDatabaseCleaner.executeTruncate();
    }
}
