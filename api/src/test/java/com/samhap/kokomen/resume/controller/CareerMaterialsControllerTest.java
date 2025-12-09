package com.samhap.kokomen.resume.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
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
import com.samhap.kokomen.global.fixture.resume.ResumeEvaluationFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.repository.ResumeEvaluationRepository;
import com.samhap.kokomen.resume.service.ResumeEvaluationAsyncService;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

class CareerMaterialsControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberPortfolioRepository memberPortfolioRepository;
    @Autowired
    private MemberResumeRepository memberResumeRepository;
    @Autowired
    private ResumeEvaluationRepository resumeEvaluationRepository;
    @MockitoBean
    private ResumeEvaluationAsyncService resumeEvaluationAsyncService;
    @MockitoBean
    private PdfValidator pdfValidator;
    @MockitoBean
    private PdfTextExtractor pdfTextExtractor;

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

    @Test
    void 회원_이력서_평가_비동기_제출_성공() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        doNothing().when(pdfValidator).validate(any(MultipartFile.class));
        when(pdfTextExtractor.extractText(any(MultipartFile.class)))
                .thenReturn("Java, Spring Boot 경험 3년. 백엔드 개발자로서 RESTful API 설계 및 구현 경험이 있습니다.")
                .thenReturn("GitHub: https://github.com/example");

        MockMultipartFile resume = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "Java, Spring Boot 경험 3년. 백엔드 개발자로서 RESTful API 설계 및 구현 경험이 있습니다.".getBytes()
        );
        MockMultipartFile portfolio = new MockMultipartFile(
                "portfolio",
                "portfolio.pdf",
                "application/pdf",
                "GitHub: https://github.com/example".getBytes()
        );
        String jobPosition = "백엔드 개발자";
        String jobDescription = "Spring Boot 기반 백엔드 개발";
        String jobCareer = "경력";

        mockMvc.perform(multipart("/api/v1/resumes/evaluations")
                        .file(resume)
                        .file(portfolio)
                        .file("job_position", jobPosition.getBytes())
                        .file("job_description", jobDescription.getBytes())
                        .file("job_career", jobCareer.getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.evaluation_id").exists())
                .andDo(document("resume-evaluation-async-submit",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키 (선택)")
                        ),
                        requestParts(
                                partWithName("resume").description("이력서 PDF 파일"),
                                partWithName("portfolio").description("포트폴리오 PDF 파일 (선택)").optional(),
                                partWithName("job_position").description("지원 직무"),
                                partWithName("job_description").description("채용공고 상세 내용 (선택)").optional(),
                                partWithName("job_career").description("경력 구분 (신입/경력)")
                        ),
                        responseFields(
                                fieldWithPath("evaluation_id").description("평가 ID (회원: 숫자, 비회원: uuid-xxx 형식)")
                        )
                ));
    }

    @Test
    void 이력서_평가_상태_조회_대기중() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        ResumeEvaluation evaluation = resumeEvaluationRepository.save(
                ResumeEvaluationFixtureBuilder.builder()
                        .member(member)
                        .build()
        );

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/resumes/evaluations/{evaluationId}/state",
                                evaluation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andDo(document("resume-evaluation-state-pending",
                        pathParameters(
                                parameterWithName("evaluationId").description("평가 ID")
                        ),
                        responseFields(
                                fieldWithPath("state").description("평가 상태 (PENDING, COMPLETED, FAILED)")
                        )
                ));
    }

    @Test
    void 이력서_평가_상태_조회_완료() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        ResumeEvaluation evaluation = resumeEvaluationRepository.save(
                ResumeEvaluationFixtureBuilder.builder()
                        .member(member)
                        .completed()
                        .build()
        );

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/resumes/evaluations/{evaluationId}/state",
                                evaluation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").exists())
                .andExpect(jsonPath("$.result.total_score").value(81))
                .andDo(document("resume-evaluation-state-completed",
                        pathParameters(
                                parameterWithName("evaluationId").description("평가 ID")
                        ),
                        responseFields(
                                fieldWithPath("state").description("평가 상태 (PENDING, COMPLETED, FAILED)"),
                                fieldWithPath("result").description("평가 결과"),
                                fieldWithPath("result.technical_skills").description("기술 역량 평가"),
                                fieldWithPath("result.technical_skills.score").description("기술 역량 점수"),
                                fieldWithPath("result.technical_skills.reason").description("기술 역량 평가 사유"),
                                fieldWithPath("result.technical_skills.improvements").description("기술 역량 개선점"),
                                fieldWithPath("result.project_experience").description("프로젝트 경험 평가"),
                                fieldWithPath("result.project_experience.score").description("프로젝트 경험 점수"),
                                fieldWithPath("result.project_experience.reason").description("프로젝트 경험 평가 사유"),
                                fieldWithPath("result.project_experience.improvements").description("프로젝트 경험 개선점"),
                                fieldWithPath("result.problem_solving").description("문제 해결 평가"),
                                fieldWithPath("result.problem_solving.score").description("문제 해결 점수"),
                                fieldWithPath("result.problem_solving.reason").description("문제 해결 평가 사유"),
                                fieldWithPath("result.problem_solving.improvements").description("문제 해결 개선점"),
                                fieldWithPath("result.career_growth").description("성장 가능성 평가"),
                                fieldWithPath("result.career_growth.score").description("성장 가능성 점수"),
                                fieldWithPath("result.career_growth.reason").description("성장 가능성 평가 사유"),
                                fieldWithPath("result.career_growth.improvements").description("성장 가능성 개선점"),
                                fieldWithPath("result.documentation").description("문서화 평가"),
                                fieldWithPath("result.documentation.score").description("문서화 점수"),
                                fieldWithPath("result.documentation.reason").description("문서화 평가 사유"),
                                fieldWithPath("result.documentation.improvements").description("문서화 개선점"),
                                fieldWithPath("result.total_score").description("총점"),
                                fieldWithPath("result.total_feedback").description("종합 피드백")
                        )
                ));
    }

    @Test
    void 이력서_평가_히스토리_조회() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        for (int i = 0; i < 3; i++) {
            resumeEvaluationRepository.save(
                    ResumeEvaluationFixtureBuilder.builder()
                            .member(member)
                            .completed()
                            .totalScore(80 + i)
                            .build()
            );
        }

        mockMvc.perform(get("/api/v1/resumes/evaluations")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations").isArray())
                .andExpect(jsonPath("$.evaluations.length()").value(3))
                .andExpect(jsonPath("$.current_page").value(0))
                .andExpect(jsonPath("$.total_resume_evaluation_count").value(3))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.has_next").value(false))
                .andDo(document("resume-evaluation-history",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("page").description("페이지 번호 (0부터 시작)"),
                                parameterWithName("size").description("페이지 크기")
                        ),
                        responseFields(
                                fieldWithPath("evaluations").description("평가 목록"),
                                fieldWithPath("evaluations[].id").description("평가 ID"),
                                fieldWithPath("evaluations[].state").description("평가 상태"),
                                fieldWithPath("evaluations[].job_position").description("지원 직무"),
                                fieldWithPath("evaluations[].job_career").description("경력 구분"),
                                fieldWithPath("evaluations[].total_score").description("총점").optional(),
                                fieldWithPath("evaluations[].created_at").description("생성일시"),
                                fieldWithPath("current_page").description("현재 페이지 번호"),
                                fieldWithPath("total_resume_evaluation_count").description("전체 평가 개수"),
                                fieldWithPath("total_pages").description("전체 페이지 수"),
                                fieldWithPath("has_next").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 이력서_평가_상세_조회() throws Exception {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        ResumeEvaluation evaluation = resumeEvaluationRepository.save(
                ResumeEvaluationFixtureBuilder.builder()
                        .member(member)
                        .completed()
                        .build()
        );

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/resumes/evaluations/{evaluationId}",
                                evaluation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evaluation.getId()))
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.resume").exists())
                .andExpect(jsonPath("$.result").exists())
                .andDo(document("resume-evaluation-detail",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("evaluationId").description("평가 ID")
                        ),
                        responseFields(
                                fieldWithPath("id").description("평가 ID"),
                                fieldWithPath("state").description("평가 상태"),
                                fieldWithPath("resume").description("이력서 텍스트"),
                                fieldWithPath("portfolio").description("포트폴리오 텍스트").optional(),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_description").description("직무 설명").optional(),
                                fieldWithPath("job_career").description("경력 구분"),
                                fieldWithPath("result").description("평가 결과"),
                                fieldWithPath("result.technical_skills").description("기술 역량 평가"),
                                fieldWithPath("result.technical_skills.score").description("기술 역량 점수"),
                                fieldWithPath("result.technical_skills.reason").description("기술 역량 평가 사유"),
                                fieldWithPath("result.technical_skills.improvements").description("기술 역량 개선점"),
                                fieldWithPath("result.project_experience").description("프로젝트 경험 평가"),
                                fieldWithPath("result.project_experience.score").description("프로젝트 경험 점수"),
                                fieldWithPath("result.project_experience.reason").description("프로젝트 경험 평가 사유"),
                                fieldWithPath("result.project_experience.improvements").description("프로젝트 경험 개선점"),
                                fieldWithPath("result.problem_solving").description("문제 해결 평가"),
                                fieldWithPath("result.problem_solving.score").description("문제 해결 점수"),
                                fieldWithPath("result.problem_solving.reason").description("문제 해결 평가 사유"),
                                fieldWithPath("result.problem_solving.improvements").description("문제 해결 개선점"),
                                fieldWithPath("result.career_growth").description("성장 가능성 평가"),
                                fieldWithPath("result.career_growth.score").description("성장 가능성 점수"),
                                fieldWithPath("result.career_growth.reason").description("성장 가능성 평가 사유"),
                                fieldWithPath("result.career_growth.improvements").description("성장 가능성 개선점"),
                                fieldWithPath("result.documentation").description("문서화 평가"),
                                fieldWithPath("result.documentation.score").description("문서화 점수"),
                                fieldWithPath("result.documentation.reason").description("문서화 평가 사유"),
                                fieldWithPath("result.documentation.improvements").description("문서화 개선점"),
                                fieldWithPath("result.total_score").description("총점"),
                                fieldWithPath("result.total_feedback").description("종합 피드백"),
                                fieldWithPath("created_at").description("생성일시")
                        )
                ));
    }
}
