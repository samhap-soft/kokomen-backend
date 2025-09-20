package com.samhap.kokomen.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.auth.external.dto.GoogleUserInfoResponse;
import com.samhap.kokomen.auth.external.dto.KakaoAccount;
import com.samhap.kokomen.auth.external.dto.KakaoIdResponse;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.auth.external.dto.Profile;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.MemberSocialLogin;
import com.samhap.kokomen.member.domain.SocialProvider;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.MemberSocialLoginRepository;
import java.util.List;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpSession;

class AuthControllerTest extends BaseControllerTest {

    @Value("${oauth.kakao.client-id}")
    private String kakaoClientId;
    @Value("${oauth.google.client-id}")
    private String googleClientId;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberSocialLoginRepository memberSocialLoginRepository;

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
                        .formatted(kakaoClientId, redirectUri, state)))
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
                  "nickname": "%s",
                  "profile_completed": false
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
                                fieldWithPath("nickname").description("멤버 닉네임"),
                                fieldWithPath("profile_completed").description("프로필을 완성했는지 여부")
                        )
                ));
    }

    @Test
    void 구글_로그인_페이지로_리다이렉트한다() throws Exception {
        // given
        String redirectUri = "https://kokomen.kr/login/callback";
        String state = "https://kokomen.kr/prev-page";
        String expectedScope = "openid%20profile%20email";

        // when & then
        mockMvc.perform(get("/api/v1/auth/google-login")
                        .param("redirectUri", redirectUri)
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=%s"
                        .formatted(googleClientId, redirectUri, expectedScope, state)))
                .andDo(document("auth-redirectGoogleLoginPage",
                        queryParameters(
                                parameterWithName("redirectUri").description("구글 로그인 후 리다이렉트될 URI"),
                                parameterWithName("state").description("로그인 전 페이지의 URI")
                        ),
                        responseHeaders(
                                headerWithName("Location").description("구글 로그인 페이지를 받을 리다이렉션 URI")
                        )
                ));
    }

    @Test
    void 구글_로그인_성공() throws Exception {
        // given
        String code = "test-code";
        String redirectUri = "https://kokomen.kr/login/callback";
        String name = "John Doe";
        String googleId = "123456789";
        String requestJson = """
                {
                  "code": "%s",
                  "redirect_uri": "%s"
                }
                """.formatted(code, redirectUri);
        String responseJson = """
                {
                  "id": 1,
                  "nickname": "%s",
                  "profile_completed": false
                }
                """.formatted(name);
        when(googleOAuthClient.requestGoogleUserInfo(code, redirectUri))
                .thenReturn(new GoogleUserInfoResponse(googleId, "john@example.com", true, name, "John", "Doe", "https://example.com/picture.jpg", "en"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(result -> {
                    HttpSession session = result.getRequest().getSession(false);
                    result.getResponse().addHeader("Set-Cookie", "JSESSIONID=" + session.getId());
                })
                .andExpect(header().string("Set-Cookie", containsString("JSESSIONID=")))
                .andDo(document("auth-googleLogin",
                        requestFields(
                                fieldWithPath("code").description("구글 로그인 인증 코드"),
                                fieldWithPath("redirect_uri").description("구글 로그인 후 리다이렉트될 URI")
                        ),
                        responseHeaders(
                                headerWithName("Set-Cookie").description("로그인 성공 후 세션 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("id").description("멤버 id"),
                                fieldWithPath("nickname").description("멤버 닉네임"),
                                fieldWithPath("profile_completed").description("프로필을 완성했는지 여부")
                        )
                ));
    }

    @Test
    void 회원_탈퇴_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long kakaoId = 12345L;
        MemberSocialLogin kakaoSocialLogin = memberSocialLoginRepository.save(
                new MemberSocialLogin(member, SocialProvider.KAKAO, String.valueOf(kakaoId)));
        
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(kakaoOAuthClient.unlinkKakaoUser(kakaoId)).thenReturn(new KakaoIdResponse(kakaoId));

        // when & then
        mockMvc.perform(delete("/api/v1/auth/kakao-withdraw")
                        .session(session)
                        .cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andDo(document("auth-kakaoWithdraw",
                        requestCookies(
                                cookieWithName("JSESSIONID").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }

    @Test
    void 회원_로그아웃_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long kakaoId = 12345L;
        MemberSocialLogin kakaoSocialLogin = memberSocialLoginRepository.save(
                new MemberSocialLogin(member, SocialProvider.KAKAO, String.valueOf(kakaoId)));
        
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(kakaoOAuthClient.logoutKakaoUser(kakaoId)).thenReturn(new KakaoIdResponse(kakaoId));

        // when & then
        mockMvc.perform(post("/api/v1/auth/kakao-logout")
                        .session(session)
                        .cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andDo(document("auth-kakaoLogout",
                        requestCookies(
                                cookieWithName("JSESSIONID").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }

    @Test
    void 통합_회원_탈퇴_성공_카카오() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long kakaoId = 12345L;
        MemberSocialLogin kakaoSocialLogin = memberSocialLoginRepository.save(
                new MemberSocialLogin(member, SocialProvider.KAKAO, String.valueOf(kakaoId)));
        
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(kakaoOAuthClient.unlinkKakaoUser(kakaoId)).thenReturn(new KakaoIdResponse(kakaoId));

        // when & then
        mockMvc.perform(delete("/api/v1/auth/withdraw")
                        .session(session)
                        .cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andDo(document("auth-withdraw",
                        requestCookies(
                                cookieWithName("JSESSIONID").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }

    @Test
    void 통합_회원_탈퇴_성공_구글() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        String googleId = "123456789";
        MemberSocialLogin googleSocialLogin = memberSocialLoginRepository.save(
                new MemberSocialLogin(member, SocialProvider.GOOGLE, googleId));
        
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(delete("/api/v1/auth/withdraw")
                        .session(session)
                        .cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andDo(document("auth-withdraw-google",
                        requestCookies(
                                cookieWithName("JSESSIONID").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }

    @Test
    void 통합_회원_로그아웃_성공_카카오() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long kakaoId = 12345L;
        MemberSocialLogin kakaoSocialLogin = memberSocialLoginRepository.save(
                new MemberSocialLogin(member, SocialProvider.KAKAO, String.valueOf(kakaoId)));
        
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        when(kakaoOAuthClient.logoutKakaoUser(kakaoId)).thenReturn(new KakaoIdResponse(kakaoId));

        // when & then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andDo(document("auth-logout",
                        requestCookies(
                                cookieWithName("JSESSIONID").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }

    @Test
    void 통합_회원_로그아웃_성공_구글() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        String googleId = "123456789";
        MemberSocialLogin googleSocialLogin = memberSocialLoginRepository.save(
                new MemberSocialLogin(member, SocialProvider.GOOGLE, googleId));
        
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andDo(document("auth-logout-google",
                        requestCookies(
                                cookieWithName("JSESSIONID").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }
}
