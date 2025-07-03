package com.samhap.kokomen.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.auth.external.dto.KakaoAccount;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.auth.external.dto.Profile;
import com.samhap.kokomen.global.BaseControllerTest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class AuthControllerTest extends BaseControllerTest {

    @Value("${oauth.kakao.client-id}")
    private String clientId;

    @Test
    void 카카오_로그인_페이지로_리다이렉트한다() throws Exception {
        // given
        String redirectUri = "https://kokomen.kr/login/callback";
        String state = "https://kokomen.kr/prev-page";

        // when & then
        mockMvc.perform(get("/api/v1/auth/kakao-login")
                        .param("redirectUri", redirectUri)
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s"
                        .formatted(clientId, redirectUri, state)))
                .andDo(document("auth-redirectKakaoLoginPage",
                        queryParameters(
                                parameterWithName("redirectUri").description("카카오 로그인 후 리다이렉트될 URI"),
                                parameterWithName("state").description("로그인 전 페이지의 URI")
                        ),
                        responseHeaders(
                                headerWithName("Location").description("카카오 로그인 페이지를 받을 리다이렉션 URI")
                        )
                ));
    }

    @Test
    void 카카오_로그인_성공() throws Exception {
        // given
        String code = "test-code";
        String redirectUri = "https://kokomen.kr/login/callback";
        String nickname = "오상훈";
        String requestJson = """
                {
                  "code": "%s",
                  "redirect_uri": "%s"
                }
                """.formatted(code, redirectUri);
        String responseJson = """
                {
                  "id": 1,
                  "nickname": "%s"
                }
                """.formatted(nickname);
        when(kakaoOAuthClient.requestKakaoUserInfo(code, redirectUri)).thenReturn(new KakaoUserInfoResponse(1L, new KakaoAccount(new Profile(nickname))));

        // when & then
        mockMvc.perform(post("/api/v1/auth/kakao-login")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(result -> {
                    HttpSession session = result.getRequest().getSession(false);
                    result.getResponse().addHeader("Set-Cookie", "JSESSIONID=" + session.getId());
                })
                .andExpect(header().string("Set-Cookie", containsString("JSESSIONID=")))
                .andDo(document("auth-kakaoLogin",
                        requestFields(
                                fieldWithPath("code").description("카카오 로그인 인증 코드"),
                                fieldWithPath("redirect_uri").description("카카오 로그인 후 리다이렉트될 URI")
                        ),
                        responseHeaders(
                                headerWithName("Set-Cookie").description("로그인 성공 후 세션 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("id").description("멤버 id"),
                                fieldWithPath("nickname").description("멤버 닉네임")
                        )
                ));
    }
}
