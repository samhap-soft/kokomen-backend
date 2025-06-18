package com.samhap.kokomen.auth.controller;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
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
                .andDo(document("auth-kakaoLogin",
                        responseHeaders(
                                headerWithName("Location").description("카카오 로그인 페이지를 받을 리다이렉션 URI")
                        )
                ));
    }
}
