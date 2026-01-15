package com.samhap.kokomen.interview.controller;

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
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberPortfolioFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.ResumeBasedRootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.ResumeBasedRootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private InterviewRepository interviewRepository;

    @Autowired
    private ResumeBasedRootQuestionRepository resumeBasedRootQuestionRepository;

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
                        .file("question_count", "3".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.interview_id").exists())
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andDo(document("resume-based-interview-submit-question-generation-with-file",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume").description("이력서 PDF 파일 (resume 또는 resume_id 중 하나 필수)"),
                                partWithName("job_career").description("경력 구분 (신입/경력)"),
                                partWithName("question_count").description("생성할 질문 수 (기본: 3, 최대: 5)").optional()
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID (질문 생성 진행 중)")
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
                .andExpect(jsonPath("$.interview_id").exists())
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
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID (질문 생성 진행 중)")
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
                        .file("question_count", "5".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.interview_id").exists())
                .andDo(document("resume-based-interview-submit-question-generation-with-portfolio",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume_id").description("기존 이력서 ID"),
                                partWithName("portfolio_id").description("기존 포트폴리오 ID (선택)").optional(),
                                partWithName("job_career").description("경력 구분"),
                                partWithName("question_count").description("생성할 질문 수 (최대 5)").optional()
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID (질문 생성 진행 중)")
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
        Interview interview = interviewRepository.save(
                Interview.createResumeBasedInterview(member, null, null, "신입", 3)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{interviewId}/generation-status", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATING_QUESTIONS"))
                .andExpect(jsonPath("$.question_count").doesNotExist());
    }

    @Test
    void 질문_생성_완료_상태_조회_성공_질문_개수_포함() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = Interview.createResumeBasedInterview(member, null, null, "신입", 3);
        interview.completeQuestionGeneration();
        interview = interviewRepository.save(interview);

        resumeBasedRootQuestionRepository.save(
                new ResumeBasedRootQuestion(interview, "첫 번째 질문입니다.", "이력서 기반 질문", 0)
        );
        resumeBasedRootQuestionRepository.save(
                new ResumeBasedRootQuestion(interview, "두 번째 질문입니다.", "포트폴리오 기반 질문", 1)
        );
        resumeBasedRootQuestionRepository.save(
                new ResumeBasedRootQuestion(interview, "세 번째 질문입니다.", "경력 기반 질문", 2)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{interviewId}/generation-status", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.question_count").value(3))
                .andDo(document("resume-based-interview-generation-status",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("인터뷰 ID")
                        ),
                        responseFields(
                                fieldWithPath("status").description("질문 생성 상태 (GENERATING_QUESTIONS: 생성 중, PENDING: 생성 완료, QUESTION_GENERATION_FAILED: 생성 실패)"),
                                fieldWithPath("question_count").description("생성된 질문 수 (PENDING 상태일 때만 포함)").optional()
                        )
                ));
    }

    @Test
    void 질문_생성_실패_상태_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = Interview.createResumeBasedInterview(member, null, null, "신입", 3);
        interview.failQuestionGeneration();
        interview = interviewRepository.save(interview);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{interviewId}/generation-status", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUESTION_GENERATION_FAILED"))
                .andExpect(jsonPath("$.question_count").doesNotExist());
    }

    @Test
    void 본인이_아닌_인터뷰_상태_조회시_403_에러_반환() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                Interview.createResumeBasedInterview(owner, null, null, "신입", 3)
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", other.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{interviewId}/generation-status", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void 존재하지_않는_인터뷰_상태_조회시_400_에러_반환() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/resume-based/{interviewId}/generation-status", 999999L)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest());
    }
}
