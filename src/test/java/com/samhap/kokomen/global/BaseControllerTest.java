package com.samhap.kokomen.global;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyUris;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(RestDocumentationExtension.class)
public abstract class BaseControllerTest extends BaseTest {

    protected MockMvc mockMvc;

    @BeforeEach
    void baseControllerTestSetUp(WebApplicationContext context, RestDocumentationContextProvider restDocumentation) {
        var uriPreprocessor = modifyUris()
                .scheme("https")
                .host("api-dev.kokomen.kr")
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
