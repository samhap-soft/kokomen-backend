package com.samhap.kokomen.member.controller;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

class MemberControllerTest extends BaseControllerTest {

    @Autowired
    protected MemberRepository memberRepository;

    @Test
    void 자신의_정보를_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String responseJson = """
                {
                    "id": %d,
                    "nickname": %s,
                    "score": %d,
                    "token_count": %d
                }
                """.formatted(member.getId(), member.getNickname(), member.getScore(), member.getFreeTokenCount());

        // when & then
        mockMvc.perform(get("/api/v1/members/me/profile")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("member-findMyProfile",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("id").description("회원 id"),
                                fieldWithPath("nickname").description("회원 닉네임"),
                                fieldWithPath("score").description("현재 회원 점수"),
                                fieldWithPath("token_count").description("현재 회원 토큰 개수")
                        )
                ));
    }
}
