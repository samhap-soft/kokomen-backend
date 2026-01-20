package com.samhap.kokomen.interview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.ResumeQuestionGenerationFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberPortfolioFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.ResumeQuestionGenerationRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

class ResumeBasedInterviewControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberResumeRepository memberResumeRepository;

    @Autowired
    private MemberPortfolioRepository memberPortfolioRepository;

    @Autowired
    private ResumeQuestionGenerationRepository resumeQuestionGenerationRepository;

    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @MockitoBean
    private PdfValidator pdfValidator;

    @MockitoBean
    private PdfTextExtractor pdfTextExtractor;

    @Test
    void 이력서_파일로_질문_생성_요청_성공_202_ACCEPTED_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        doNothing().when(pdfValidator).validate(any(MultipartFile.class));
        when(pdfTextExtractor.extractText(any(MultipartFile.class)))
                .thenReturn("Java, Spring Boot 경험 3년. 백엔드 개발자입니다.");

        MockMultipartFile resumeFile = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "이력서 내용".getBytes()
        );

        // when & then
        mockMvc.perform(multipart("/api/v1/interviews/resume-based/questions/generate")
                        .file(resumeFile)
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resume_based_interview_result_id").exists())
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andDo(document("resume-based-interview-submit-question-generation-with-file",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume").description("이력서 PDF 파일 (resume 또는 resume_id 중 하나 필수)"),
                                partWithName("job_career").description("경력 구분 (신입/경력)")
                        ),
                        responseFields(
                                fieldWithPath("resume_based_interview_result_id").description(
                                        "질문 생성 요청 ID (질문 생성 진행 중)")
                        )
                ));
    }

    @Test
    void 기존_이력서_ID로_질문_생성_요청_성공_202_ACCEPTED_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .content("이력서 내용입니다.")
                        .build()
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(multipart("/api/v1/interviews/resume-based/questions/generate")
                        .file("resume_id", String.valueOf(resume.getId()).getBytes())
                        .file("job_career", "경력 3년".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resume_based_interview_result_id").exists())
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andDo(document("resume-based-interview-submit-question-generation-with-resume-id",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume_id").description("기존 이력서 ID (resume 또는 resume_id 중 하나 필수)"),
                                partWithName("job_career").description("경력 구분 (신입/경력)")
                        ),
                        responseFields(
                                fieldWithPath("resume_based_interview_result_id").description(
                                        "질문 생성 요청 ID (질문 생성 진행 중)")
                        )
                ));
    }

    @Test
    void 이력서와_포트폴리오_모두_포함하여_질문_생성_요청_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .content("이력서 내용")
                        .build()
        );
        MemberPortfolio portfolio = memberPortfolioRepository.save(
                MemberPortfolioFixtureBuilder.builder()
                        .member(member)
                        .content("포트폴리오 내용")
                        .build()
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(multipart("/api/v1/interviews/resume-based/questions/generate")
                        .file("resume_id", String.valueOf(resume.getId()).getBytes())
                        .file("portfolio_id", String.valueOf(portfolio.getId()).getBytes())
                        .file("job_career", "경력 5년".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resume_based_interview_result_id").exists())
                .andDo(document("resume-based-interview-submit-question-generation-with-portfolio",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume_id").description("기존 이력서 ID"),
                                partWithName("portfolio_id").description("기존 포트폴리오 ID (선택)").optional(),
                                partWithName("job_career").description("경력 구분")
                        ),
                        responseFields(
                                fieldWithPath("resume_based_interview_result_id").description(
                                        "질문 생성 요청 ID (질문 생성 진행 중)")
                        )
                ));
    }

    @Test
    void 이력서가_없으면_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(multipart("/api/v1/interviews/resume-based/questions/generate")
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증되지_않은_사용자는_401_에러_반환() throws Exception {
        // given
        MockMultipartFile resumeFile = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "이력서 내용".getBytes()
        );

        // when & then
        mockMvc.perform(multipart("/api/v1/interviews/resume-based/questions/generate")
                        .file(resumeFile)
                        .file("job_career", "신입".getBytes())
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 질문_생성_중_상태_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.save(
                new ResumeQuestionGeneration(member, null, null, "신입")
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}/check",
                        generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void 질문_생성_완료_상태_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "첫 번째 질문입니다.", "이력서 기반 질문", 0)
        );
        generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "두 번째 질문입니다.", "포트폴리오 기반 질문", 1)
        );
        generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "세 번째 질문입니다.", "경력 기반 질문", 2)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}/check",
                        generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andDo(document("resume-based-interview-check",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("resumeBasedInterviewResultId").description("질문 생성 요청 ID")
                        ),
                        responseFields(
                                fieldWithPath("state").description(
                                        "질문 생성 상태 (PENDING: 생성 중, COMPLETED: 생성 완료, FAILED: 생성 실패)")
                        )
                ));
    }

    @Test
    void 질문_생성_실패_상태_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "신입");
        generation.fail();
        generation = resumeQuestionGenerationRepository.save(generation);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}/check",
                        generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }

    @Test
    void 본인이_아닌_질문_생성_상태_조회시_403_에러_반환() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.save(
                new ResumeQuestionGeneration(owner, null, null, "신입")
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", other.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}/check",
                        generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void 존재하지_않는_질문_생성_상태_조회시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}/check", 999999L)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 완료된_질문_생성_요청의_질문_목록_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "첫 번째 질문입니다.", "이력서 기반 질문", 0)
        );
        generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "두 번째 질문입니다.", "포트폴리오 기반 질문", 1)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].question").value("첫 번째 질문입니다."))
                .andExpect(jsonPath("$[1].id").exists())
                .andExpect(jsonPath("$[1].question").value("두 번째 질문입니다."))
                .andDo(document("resume-based-interview-get-generated-questions",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("resumeBasedInterviewResultId").description("질문 생성 요청 ID")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("생성된 질문 ID"),
                                fieldWithPath("[].question").description("질문 내용")
                        )
                ));
    }

    @Test
    void 미완료_상태의_질문_목록_조회시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.save(
                new ResumeQuestionGeneration(member, null, null, "신입")
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 본인이_아닌_질문_목록_조회시_403_에러_반환() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(owner, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", other.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void 존재하지_않는_질문_생성_요청의_질문_목록_조회시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", 999999L)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증되지_않은_사용자의_질문_목록_조회시_401_에러_반환() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 이력서_기반_인터뷰_시작_텍스트모드_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        GeneratedQuestion generatedQuestion = generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "Spring Boot의 자동 설정에 대해 설명해주세요.", "이력서 기반 질문", 0)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """.formatted(generatedQuestion.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interview_id").exists())
                .andExpect(jsonPath("$.question_id").exists())
                .andExpect(jsonPath("$.root_question").value("Spring Boot의 자동 설정에 대해 설명해주세요."))
                .andDo(document("resume-based-interview-start-text-mode",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("resumeBasedInterviewResultId").description("질문 생성 요청 ID")
                        ),
                        requestFields(
                                fieldWithPath("generated_question_id").description("선택한 생성 질문 ID"),
                                fieldWithPath("max_question_count").description("최대 질문 개수 (3-20)"),
                                fieldWithPath("mode").description("인터뷰 모드 (TEXT, VOICE)")
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID"),
                                fieldWithPath("question_id").description("생성된 첫 질문 ID"),
                                fieldWithPath("root_question").description("첫 질문 내용")
                        )
                ));
    }

    @Test
    void 이력서_기반_인터뷰_시작_음성모드_성공() throws Exception {
        // given
        when(supertoneClient.request(any())).thenReturn(new SupertoneResponse(new byte[0]));

        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "경력 3년");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        GeneratedQuestion generatedQuestion = generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "마이크로서비스 아키텍처 경험에 대해 설명해주세요.", "이력서 기반 질문", 0)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "VOICE"
                }
                """.formatted(generatedQuestion.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interview_id").exists())
                .andExpect(jsonPath("$.question_id").exists())
                .andExpect(jsonPath("$.root_question_voice_url").exists())
                .andDo(document("resume-based-interview-start-voice-mode",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("resumeBasedInterviewResultId").description("질문 생성 요청 ID")
                        ),
                        requestFields(
                                fieldWithPath("generated_question_id").description("선택한 생성 질문 ID"),
                                fieldWithPath("max_question_count").description("최대 질문 개수 (3-20)"),
                                fieldWithPath("mode").description("인터뷰 모드 (TEXT, VOICE)")
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID"),
                                fieldWithPath("question_id").description("생성된 첫 질문 ID"),
                                fieldWithPath("root_question_voice_url").description("첫 질문 음성 URL")
                        )
                ));
    }

    @Test
    void 본인이_아닌_질문_생성_결과로_인터뷰_시작시_403_에러_반환() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(other.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(other.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(owner, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        GeneratedQuestion generatedQuestion = generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "테스트 질문입니다.", "이력서 기반 질문", 0)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", other.getId());

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """.formatted(generatedQuestion.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void 미완료_상태의_질문_생성_결과로_인터뷰_시작시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.save(
                new ResumeQuestionGeneration(member, null, null, "신입")
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                    "generated_question_id": 1,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_생성_질문으로_인터뷰_시작시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                    "generated_question_id": 999999,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 다른_질문_생성_요청에_속한_질문으로_인터뷰_시작시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation1 = new ResumeQuestionGeneration(member, null, null, "신입");
        generation1.complete();
        generation1 = resumeQuestionGenerationRepository.save(generation1);

        ResumeQuestionGeneration generation2 = new ResumeQuestionGeneration(member, null, null, "경력");
        generation2.complete();
        generation2 = resumeQuestionGenerationRepository.save(generation2);

        GeneratedQuestion questionFromGeneration2 = generatedQuestionRepository.save(
                new GeneratedQuestion(generation2, "테스트 질문입니다.", "이력서 기반 질문", 0)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """.formatted(questionFromGeneration2.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 토큰이_부족하면_인터뷰_시작시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(0).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());

        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(member, null, null, "신입");
        generation.complete();
        generation = resumeQuestionGenerationRepository.save(generation);

        GeneratedQuestion generatedQuestion = generatedQuestionRepository.save(
                new GeneratedQuestion(generation, "테스트 질문입니다.", "이력서 기반 질문", 0)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """.formatted(generatedQuestion.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", generation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증되지_않은_사용자의_인터뷰_시작시_401_에러_반환() throws Exception {
        // given
        String requestJson = """
                {
                    "generated_question_id": 1,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-based/{resumeBasedInterviewResultId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내_질문_생성_기록_목록_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .title("테스트 이력서")
                        .resumeUrl("https://example.com/resume.pdf")
                        .build()
        );
        MemberPortfolio portfolio = memberPortfolioRepository.save(
                MemberPortfolioFixtureBuilder.builder()
                        .member(member)
                        .title("테스트 포트폴리오")
                        .portfolioUrl("https://example.com/portfolio.pdf")
                        .build()
        );

        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .memberResume(resume)
                        .memberPortfolio(portfolio)
                        .jobCareer("신입")
                        .state(ResumeQuestionGenerationState.COMPLETED)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/questions/generations")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].job_career").value("신입"))
                .andExpect(jsonPath("$.data[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].created_at").exists())
                .andExpect(jsonPath("$.data[0].resume.name").value("테스트 이력서"))
                .andExpect(jsonPath("$.data[0].resume.url").value("https://example.com/resume.pdf"))
                .andExpect(jsonPath("$.data[0].portfolio.name").value("테스트 포트폴리오"))
                .andExpect(jsonPath("$.data[0].portfolio.url").value("https://example.com/portfolio.pdf"))
                .andExpect(jsonPath("$.current_page").value(0))
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.has_next").value(false))
                .andDo(document("resume-question-generation-list",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("state").description(
                                        "상태 필터 (PENDING, COMPLETED, FAILED). 미지정시 PENDING, COMPLETED만 조회").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본값: 20)").optional(),
                                parameterWithName("sort").description("정렬 (기본값: createdAt,DESC)").optional()
                        ),
                        responseFields(
                                fieldWithPath("data[]").description("질문 생성 기록 목록"),
                                fieldWithPath("data[].id").description("질문 생성 ID"),
                                fieldWithPath("data[].job_career").description("경력 구분"),
                                fieldWithPath("data[].state").description("생성 상태 (PENDING, COMPLETED, FAILED)"),
                                fieldWithPath("data[].created_at").description("생성 일시"),
                                fieldWithPath("data[].resume").description("이력서 정보 (없으면 null)").optional(),
                                fieldWithPath("data[].resume.name").description("이력서 파일명"),
                                fieldWithPath("data[].resume.url").description("이력서 URL"),
                                fieldWithPath("data[].portfolio").description("포트폴리오 정보 (없으면 null)").optional(),
                                fieldWithPath("data[].portfolio.name").description("포트폴리오 파일명"),
                                fieldWithPath("data[].portfolio.url").description("포트폴리오 URL"),
                                fieldWithPath("current_page").description("현재 페이지 번호"),
                                fieldWithPath("total_count").description("전체 기록 수"),
                                fieldWithPath("total_pages").description("전체 페이지 수"),
                                fieldWithPath("has_next").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 상태_필터로_질문_생성_기록_조회() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .jobCareer("신입")
                        .state(ResumeQuestionGenerationState.PENDING)
                        .build()
        );
        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .jobCareer("경력")
                        .state(ResumeQuestionGenerationState.COMPLETED)
                        .build()
        );
        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .jobCareer("경력 3년")
                        .state(ResumeQuestionGenerationState.FAILED)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then - PENDING 상태만 조회
        mockMvc.perform(get("/api/v1/interviews/resume-based/questions/generations")
                        .param("state", "PENDING")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.data[0].state").value("PENDING"));
    }

    @Test
    void 기본_조회시_FAILED_상태는_제외된다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .jobCareer("신입")
                        .state(ResumeQuestionGenerationState.PENDING)
                        .build()
        );
        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .jobCareer("실패 건")
                        .state(ResumeQuestionGenerationState.FAILED)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then - state 파라미터 없이 조회하면 PENDING, COMPLETED만 반환
        mockMvc.perform(get("/api/v1/interviews/resume-based/questions/generations")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.data[0].state").value("PENDING"));
    }

    @Test
    void 이력서와_포트폴리오가_없는_경우_null로_반환된다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        resumeQuestionGenerationRepository.save(
                ResumeQuestionGenerationFixtureBuilder.builder()
                        .member(member)
                        .memberResume(null)
                        .memberPortfolio(null)
                        .jobCareer("신입")
                        .state(ResumeQuestionGenerationState.COMPLETED)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/questions/generations")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resume").doesNotExist())
                .andExpect(jsonPath("$.data[0].portfolio").doesNotExist());
    }

    @Test
    void 인증되지_않은_사용자의_질문_생성_기록_조회시_401_반환() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/questions/generations"))
                .andExpect(status().isUnauthorized());
    }
}
