package com.samhap.kokomen.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
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
    @Value("${oauth.kakao.redirect-uri}")
    private String redirectUri;

    @Test
    void 카카오_로그인_페이지로_리다이렉트한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao-login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s".formatted(clientId, redirectUri)))
                .andDo(document("auth-redirectKakaoLoginPage",
                        responseHeaders(
                                headerWithName("Location").description("카카오 로그인 페이지를 받을 리다이렉션 URI")
                        )
                ));
    }

    @Test
    void 카카오_로그인_성공() throws Exception {
        String code = "test-code";
        String nickname = "오상훈";
        String responseJson = """
                {
                  "id": 1,
                  "nickname": "%s"
                }
                """.formatted(nickname);
        when(kakaoOAuthClient.requestKakaoUserInfo(code)).thenReturn(new KakaoUserInfoResponse(1L, new KakaoAccount(new Profile(nickname))));

        mockMvc.perform(post("/api/v1/auth/kakao-login")
                        .param("code", code))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(result -> {
                    HttpSession session = result.getRequest().getSession(false);
                    result.getResponse().addHeader("Set-Cookie", "JSESSIONID=" + session.getId());
                })
                .andExpect(header().string("Set-Cookie", containsString("JSESSIONID=")))
                .andDo(document("auth-kakaoLogin",
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
