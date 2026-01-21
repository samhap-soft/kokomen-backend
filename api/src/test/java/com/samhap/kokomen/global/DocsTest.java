package com.samhap.kokomen.global;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyUris;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.samhap.kokomen.interview.external.InterviewProceedGptClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("docs")
@ExtendWith(RestDocumentationExtension.class)
@Transactional
@SpringBootTest
public abstract class DocsTest {

    protected MockMvc mockMvc;
    @MockitoBean
    protected InterviewProceedGptClient interviewProceedGptClient;
    @Autowired
    private H2AutoIncrementCleaner h2AutoIncrementCleaner;

    @BeforeEach
    void baseControllerTestSetUp(WebApplicationContext context, RestDocumentationContextProvider restDocumentation) {
        h2AutoIncrementCleaner.executeResetAutoIncrement();
        var uriPreprocessor = modifyUris()
                .scheme("https")
                .host("api.dev.kokomen.kr")
                .removePort();

        var headerPreprocessor = modifyHeaders().remove(HttpHeaders.CONTENT_LENGTH);

        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .alwaysDo(print())
                .apply(documentationConfiguration(restDocumentation).operationPreprocessors()
                        .withRequestDefaults(uriPreprocessor, prettyPrint(), headerPreprocessor)
                        .withResponseDefaults(prettyPrint(), headerPreprocessor)
                ).build();
    }
}
