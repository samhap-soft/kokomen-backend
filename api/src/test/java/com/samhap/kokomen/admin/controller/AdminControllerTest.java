package com.samhap.kokomen.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class AdminControllerTest extends BaseControllerTest {

    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void 루트_질문_음성_파일_업로드() throws Exception {
        // given
        when(supertoneClient.request(any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice", rootQuestion.getId()))
                .andExpect(status().isOk())
                .andDo(document("admin-uploadRootQuestionVoice",
                        pathParameters(
                                parameterWithName("rootQuestionId").description("루트질문 ID")
                        ),
                        responseFields(
                                fieldWithPath("root_question_voice_url").description("루트 질문 음성 파일 URL")
                        )
                ));
    }
}
