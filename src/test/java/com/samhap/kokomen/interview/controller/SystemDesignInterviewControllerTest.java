package com.samhap.kokomen.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class SystemDesignInterviewControllerTest extends BaseControllerTest {

    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 시스템_설계_면접을_시작한다() throws Exception {
        // given
        Member member = saveMemberWithTokens(10, 0);
        problemTestSupport.saveProblem();
        MockHttpSession session = loginSession(member);

        // when
        MvcResult result = mockMvc.perform(post("/api/v1/interviews/system-design")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interview_id").isNumber())
                .andDo(document("system-design-interview-start",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 시스템 설계 면접 ID")
                        )
                ))
                .andReturn();

        // then
        Long interviewId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("interview_id").asLong();
        assertAll(
                () -> assertThat(interviewRepository.findById(interviewId)).get()
                        .extracting(Interview::getInterviewType)
                        .isEqualTo(InterviewType.SYSTEM_DESIGN),
                () -> assertThat(systemDesignInterviewRepository.findByInterviewId(interviewId)).isPresent(),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(10),
                () -> assertThat(readTokenCount(member, TokenType.PAID)).isZero()
        );
    }

    @Test
    void 토큰이_6개보다_적으면_시작할_수_없다() throws Exception {
        // given
        Member member = saveMemberWithTokens(5, 0);
        problemTestSupport.saveProblem();
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/system-design")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("토큰 갯수가 부족합니다."))
                .andDo(document("system-design-interview-start-not-enough-tokens",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("message").description("FREE와 PAID 토큰을 합해 6개보다 적을 때의 오류 문구")
                        )
                ));
        assertAll(
                () -> assertThat(interviewRepository.count()).isZero(),
                () -> assertThat(systemDesignInterviewRepository.count()).isZero()
        );
    }

    @Test
    void 시작은_로그인하지_않으면_401() throws Exception {
        // given
        problemTestSupport.saveProblem();

        // when & then
        mockMvc.perform(post("/api/v1/interviews/system-design"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
        assertThat(interviewRepository.count()).isZero();
    }

    private Member saveMemberWithTokens(int freeTokenCount, int paidTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(paidTokenCount).build());
        return member;
    }

    private MockHttpSession loginSession(Member member) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        return session;
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }
}
