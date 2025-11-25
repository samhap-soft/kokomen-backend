package com.samhap.kokomen.resume.controller;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberPortfolioFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

class CareerMaterialsControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberPortfolioRepository memberPortfolioRepository;
    @Autowired
    private MemberResumeRepository memberResumeRepository;

    @Test
    void 이력서_업로드_성공() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        MockMultipartFile resume = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "test resume content".getBytes()
        );

        MockMultipartFile portfolio = new MockMultipartFile(
                "portfolio",
                "portfolio.pdf",
                "application/pdf",
                "test portfolio content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/resumes")
                        .file(resume)
                        .file(portfolio)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNoContent())
                .andDo(document("resume-upload",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume").description("업로드할 이력서 PDF 파일"),
                                partWithName("portfolio").description("업로드할 포트폴리오 PDF 파일 (선택사항)").optional()
                        )
                ));
    }

    @Test
    void 멤버_이력서_반환() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        for (int i = 0; i < 3; i++) {
            memberResumeRepository.save(
                    MemberResumeFixtureBuilder.builder()
                            .member(member)
                            .build()
            );
            memberPortfolioRepository.save(
                    MemberPortfolioFixtureBuilder.builder()
                            .member(member)
                            .build()
            );
        }

        mockMvc.perform(get("/api/v1/resumes")
                        .param("type", "ALL")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumes").isArray())
                .andExpect(jsonPath("$.resumes.length()").value(3))
                .andExpect(jsonPath("$.portfolios").isArray())
                .andExpect(jsonPath("$.portfolios.length()").value(3))
                .andDo(document("resume-getCareerMaterials",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("type").description("조회할 이력서 및 포트폴리오 타입 (ALL, RESUME, PORTFOLIO)")
                        ),
                        responseFields(
                                fieldWithPath("resumes").description("업로드할 이력서 PDF 파일"),
                                fieldWithPath("resumes[].id").description("이력서 ID"),
                                fieldWithPath("resumes[].title").description("이력서 파일명"),
                                fieldWithPath("resumes[].url").description("이력서 CDN URL"),
                                fieldWithPath("resumes[].created_at").description("이력서 업로드 일시"),
                                fieldWithPath("portfolios").description("업로드할 포트폴리오 PDF 파일 (선택사항)").optional(),
                                fieldWithPath("portfolios[].id").description("포트폴리오 ID"),
                                fieldWithPath("portfolios[].title").description("포트폴리오 파일명"),
                                fieldWithPath("portfolios[].url").description("포트폴리오 CDN URL"),
                                fieldWithPath("portfolios[].created_at").description("포트폴리오 업로드 일시")
                        )
                ));
    }
}
