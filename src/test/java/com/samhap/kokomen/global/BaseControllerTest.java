package com.samhap.kokomen.global;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyUris;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.interview.external.GptClient;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(RestDocumentationExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public abstract class BaseControllerTest {

    protected MockMvc mockMvc;
    @Autowired
    protected AnswerRepository answerRepository;
    @Autowired
    protected InterviewRepository interviewRepository;
    @Autowired
    protected QuestionRepository questionRepository;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected RootQuestionRepository rootQuestionRepository;
    @Autowired
    protected ObjectMapper objectMapper;
    @MockitoBean
    protected GptClient gptClient;

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocumentation) {
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
