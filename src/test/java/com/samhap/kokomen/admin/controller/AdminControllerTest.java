package com.samhap.kokomen.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Admin;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.AdminRepository;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class AdminControllerTest extends BaseControllerTest {

    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Test
    void 루트_질문_음성_파일_업로드() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(supertoneClient.request(any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice", rootQuestion.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andDo(document("admin-uploadRootQuestionVoice",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("rootQuestionId").description("루트질문 ID")
                        ),
                        responseFields(
                                fieldWithPath("root_question_voice_url").description("루트 질문 음성 파일 URL")
                        )
                ));
    }

    @Test
    void 비로그인_요청은_루트_질문_음성을_업로드할_수_없다() throws Exception {
        // given
        when(supertoneClient.request(any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice", rootQuestion.getId()))
                .andExpect(status().isUnauthorized());
        verify(supertoneClient, never()).request(any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 관리자가_아닌_회원은_루트_질문_음성을_업로드할_수_없다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(supertoneClient.request(any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice", rootQuestion.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isForbidden());
        verify(supertoneClient, never()).request(any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 관리자는_API_키로_루트_질문_음성을_업로드할_수_있다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(supertoneClient.requestWithApiKey(any(), any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice-with-api-key",
                        rootQuestion.getId())
                        .param("oneTimeApiKey", "one-time-api-key")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk());
        verify(supertoneClient).requestWithApiKey(any(), eq("one-time-api-key"));
    }

    @Test
    void 비로그인_요청은_API_키로_루트_질문_음성을_업로드할_수_없다() throws Exception {
        // given
        when(supertoneClient.requestWithApiKey(any(), any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice-with-api-key",
                        rootQuestion.getId())
                        .param("oneTimeApiKey", "one-time-api-key"))
                .andExpect(status().isUnauthorized());
        verify(supertoneClient, never()).requestWithApiKey(any(), any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 관리자가_아닌_회원은_API_키로_루트_질문_음성을_업로드할_수_없다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(supertoneClient.requestWithApiKey(any(), any())).thenReturn(new SupertoneResponse(new byte[0]));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post("/api/v1/admin/root-question/{rootQuestionId}/upload-voice-with-api-key",
                        rootQuestion.getId())
                        .param("oneTimeApiKey", "one-time-api-key")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isForbidden());
        verify(supertoneClient, never()).requestWithApiKey(any(), any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
