package com.samhap.kokomen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class MemberControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private TokenService tokenService;

    @Test
    void 멤버_프로필_조회() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String responseJson = """
                {
                    "id": %d,
                    "nickname": %s,
                    "score": %d,
                    "total_member_count": 1,
                    "rank": 1,
                    "token_count": 20,
                    "profile_completed": %s,
                    "is_test_user": false
                }
                """.formatted(member.getId(), member.getNickname(), member.getScore(), member.getProfileCompleted());

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
                                fieldWithPath("total_member_count").description("전체 회원 수"),
                                fieldWithPath("rank").description("회원 등수"),
                                fieldWithPath("token_count").description("현재 회원 토큰 개수"),
                                fieldWithPath("profile_completed").description("프로필 완성 여부"),
                                fieldWithPath("is_test_user").description("테스트 유저 여부")
                        )
                ));
    }

    @Test
    void 프로필을_변경한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String newNickname = "새로운닉네임";
        String requestJson = """
                {
                  "nickname": "%s"
                }
                """.formatted(newNickname);

        // when & then
        mockMvc.perform(patch("/api/v1/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andDo(document("member-updateProfile",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("nickname").description("변경할 닉네임")
                        )
                ));

        Member updatedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getNickname()).isEqualTo(newNickname);
    }

    @Test
    void 닉네임이_공백이면_프로필_변경에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String invalidNickname = "  ";
        String requestJson = """
                {
                  "nickname": "%s"
                }
                """.formatted(invalidNickname);

        // when & then
        mockMvc.perform(patch("/api/v1/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andDo(document("member-updateProfile-error",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("nickname").description("변경할 닉네임")
                        )
                ));
    }

    @Test
    void 랭킹_조회에_성공한다() throws Exception {
        // given
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().nickname("100점 회원").score(100).kakaoId(1L).build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().nickname("200점 회원").score(200).kakaoId(2L).build());
        Member member3 = memberRepository.save(MemberFixtureBuilder.builder().nickname("300점 회원").score(300).kakaoId(3L).build());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());

        String responseJson = """
                [
                  {
                    "id": %d,
                    "nickname": "300점 회원",
                    "score": 300,
                    "finished_interview_count": 2
                  },
                  {
                    "id": %d,
                    "nickname": "200점 회원",
                    "score": 200,
                    "finished_interview_count": 1
                  }
                ]
                """.formatted(member3.getId(), member2.getId());

        // when & then
        mockMvc.perform(get("/api/v1/members/ranking")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("member-findRanking",
                        queryParameters(
                                parameterWithName("page").description("페이지 번호 (0부터 시작)"),
                                parameterWithName("size").description("한 페이지 크기")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("회원 ID"),
                                fieldWithPath("[].nickname").description("회원 닉네임"),
                                fieldWithPath("[].score").description("회원 점수"),
                                fieldWithPath("[].finished_interview_count").description("회원의 완료한 인터뷰 수")
                        )
                ));
    }
}
