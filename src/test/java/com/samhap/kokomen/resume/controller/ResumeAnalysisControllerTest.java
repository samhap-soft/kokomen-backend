package com.samhap.kokomen.resume.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.doNothing;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
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
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.DimensionScoreFixture;
import com.samhap.kokomen.global.fixture.resume.GeneratedQuestionForAnalysisFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberPortfolioFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisSourceTextFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import com.samhap.kokomen.resume.service.ResumeAnalysisFacadeService;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import com.samhap.kokomen.resume.tool.PdfValidator;
import com.samhap.kokomen.resume.tool.ResumeAnalysisPdfPolicy;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

class ResumeAnalysisControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberResumeRepository memberResumeRepository;

    @Autowired
    private MemberPortfolioRepository memberPortfolioRepository;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;

    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private RedisService redisService;

    @MockitoBean
    private PdfValidator pdfValidator;

    @MockitoBean
    private PdfTextExtractor pdfTextExtractor;

    // ResumeAnalysisPdfPolicy는 Loader.loadPDF로 실제 PDF를 파싱하므로 반드시 목으로 잡는다.
    // 목이 없으면 모든 제출 테스트가 "PDF 파일을 읽을 수 없습니다." 400으로 떨어진다.
    // resumeAnalysisAsyncService는 BaseTest의 상속 필드를 쓴다(로컬 재선언 금지).
    @MockitoBean
    private ResumeAnalysisPdfPolicy resumeAnalysisPdfPolicy;

    @Test
    void 회원_파일_업로드로_이력서_분석_제출_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);
        stubExtraction();

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file(portfolioFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_description", "Spring Boot 기반 백엔드 개발".getBytes())
                        .file("job_career", "경력 3년".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysis_id").exists())
                .andExpect(jsonPath("$.guest_token").doesNotExist())
                .andDo(document("resume-analysis-submit-member-with-file",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestParts(
                                partWithName("resume").description("이력서 PDF 파일 (resume 또는 resume_id 중 하나 필수)"),
                                partWithName("portfolio").description("포트폴리오 PDF 파일 (선택)").optional(),
                                partWithName("job_position").description("지원 직무 (필수, 500자 이하)"),
                                partWithName("job_description").description("채용 공고 (선택, 10000자 이하)").optional(),
                                partWithName("job_career").description("경력 사항 (필수, 100자 이하)")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("생성된 이력서 분석 ID")
                        )
                ));
        // 파트가 어느 필드로 실렸는지와 UTF-8 디코딩을 함께 고정한다. 컨버터 기본 charset이 ISO-8859-1로
        // 바뀌면 한글 지원 직무가 조용히 깨진 값으로 저장되고 다른 단정은 전부 통과한다.
        ResumeAnalysis saved = resumeAnalysisRepository.findAll().get(0);
        assertThat(saved.getJobPosition()).isEqualTo("백엔드 개발자");
        assertThat(saved.getJobCareer()).isEqualTo("경력 3년");
        assertThat(saved.isJdProvided()).isTrue();
    }

    @Test
    void 회원_저장된_이력서로_분석_제출_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(MemberResumeFixtureBuilder.builder()
                .member(member)
                .content("Java, Spring Boot 경험 3년.")
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file("resume_id", String.valueOf(resume.getId()).getBytes())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_description", "Spring Boot 기반 백엔드 개발".getBytes())
                        .file("job_career", "경력 3년".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysis_id").exists())
                .andDo(document("resume-analysis-submit-member-with-saved-resume",
                        requestParts(
                                partWithName("resume_id").description("저장된 이력서 ID (회원 전용)"),
                                partWithName("job_position").description("지원 직무 (필수, 500자 이하)"),
                                partWithName("job_description").description("채용 공고 (선택, 10000자 이하)").optional(),
                                partWithName("job_career").description("경력 사항 (필수, 100자 이하)")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("생성된 이력서 분석 ID")
                        )
                ));
    }

    @Test
    void 채용공고_없이_이력서_분석_제출_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);
        stubExtraction();

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file(portfolioFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysis_id").exists())
                .andDo(document("resume-analysis-submit-member-without-jd",
                        requestParts(
                                partWithName("resume").description("이력서 PDF 파일"),
                                partWithName("portfolio").description("포트폴리오 PDF 파일 (선택)").optional(),
                                partWithName("job_position").description("지원 직무 (필수, 500자 이하)"),
                                partWithName("job_career").description("경력 사항 (필수, 100자 이하)")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("생성된 이력서 분석 ID")
                        )
                ));
        assertThat(resumeAnalysisRepository.findAll().get(0).isJdProvided()).isFalse();
    }

    @Test
    void 비회원_이력서_분석_제출_성공() throws Exception {
        // given
        stubExtraction();

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_description", "Spring Boot 기반 백엔드 개발".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("X-Forwarded-For", "11.22.33.51")
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysis_id").exists())
                .andExpect(jsonPath("$.guest_token").exists())
                .andDo(document("resume-analysis-submit-guest",
                        requestHeaders(
                                headerWithName("X-Forwarded-For").description("클라이언트 실제 IP 주소 (비회원 식별용)")
                        ),
                        requestParts(
                                partWithName("resume").description("이력서 PDF 파일 (비회원은 파일만 가능)"),
                                partWithName("job_position").description("지원 직무 (필수, 500자 이하)"),
                                partWithName("job_description").description("채용 공고 (선택, 10000자 이하)").optional(),
                                partWithName("job_career").description("경력 사항 (필수, 100자 이하)")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("생성된 이력서 분석 ID"),
                                fieldWithPath("guest_token").description("비회원 소유 증명 토큰 (조회·claim에 사용)")
                        )
                ));
    }

    @Test
    void 비회원이_같은_IP로_두_번_제출하면_400() throws Exception {
        // given
        String guestIp = "11.22.33.52";
        redisService.acquireLock(
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX + guestIp,
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_TTL);
        stubExtraction();

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("X-Forwarded-For", guestIp)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("비회원 이력서 분석은 1회만 가능합니다."))
                .andDo(document("resume-analysis-submit-guest-duplicate-ip",
                        requestHeaders(
                                headerWithName("X-Forwarded-For").description("클라이언트 실제 IP 주소 (비회원 식별용)")
                        )
                ));
    }

    @Test
    void 이력서_분석_조회_대기중() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(MemberResumeFixtureBuilder.builder()
                .member(member).title("이력서.pdf").build());
        MemberPortfolio portfolio = memberPortfolioRepository.save(MemberPortfolioFixtureBuilder.builder()
                .member(member).title("포트폴리오.pdf").build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .resume(resume)
                .portfolio(portfolio)
                .jobDescription("Spring Boot 기반 백엔드 개발")
                .state(ResumeAnalysisState.PENDING)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.jd_provided").value(true))
                .andExpect(jsonPath("$.interview_available").value(false))
                .andExpect(jsonPath("$.evaluation").doesNotExist())
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andExpect(jsonPath("$.question_retryable").doesNotExist())
                .andDo(document("resume-analysis-get-pending",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태 (PENDING, EVALUATION_COMPLETED, COMPLETED, "
                                        + "EVALUATION_FAILED, QUESTION_FAILED)"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부"),
                                fieldWithPath("resume").description("사용된 이력서 (회원 + 저장 자료일 때만)"),
                                fieldWithPath("resume.id").description("이력서 ID"),
                                fieldWithPath("resume.title").description("이력서 파일명"),
                                fieldWithPath("portfolio").description("사용된 포트폴리오 (회원 + 저장 자료일 때만)"),
                                fieldWithPath("portfolio.id").description("포트폴리오 ID"),
                                fieldWithPath("portfolio.title").description("포트폴리오 파일명"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_description").description("채용 공고 (제공했을 때만)"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시")
                        )
                ));
    }

    @Test
    void 이력서_분석_조회_평가완료_JD포함() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluatedWithJd(member,
                ResumeAnalysisState.EVALUATION_COMPLETED));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EVALUATION_COMPLETED"))
                .andExpect(jsonPath("$.interview_available").value(false))
                // 지표 5칸과 근거/보완 두 칸은 서로 같은 타입이라 자리가 바뀌어도 컴파일이 통과한다.
                // 다섯 지표의 점수와 가중치를 모두 단정해 어느 두 칸을 교환해도 깨지게 한다
                // (technical_skills와 jd_fit은 점수가 같으므로 가중치가 그 둘을 가른다).
                //
                // 이 판별은 다섯 지표의 (점수, 가중치) 쌍이 서로 다르다는 것에 전적으로 의존한다.
                // 두 지표가 점수와 가중치를 모두 공유하게 되면 그 둘 사이의 교환은 여기서 잡히지 않는다
                // -- 실제로 project_experience를 70/0.25로 맞춰 technical_skills와 완전히 겹치게 하면
                // 교환 변형이 생존하는 것이 확인됐다. 픽스처 점수나 가중치 표를 바꿀 때는 다섯 쌍이
                // 여전히 서로 다른지 확인해야 하고, 겹치게 만들 수밖에 없다면 그 두 지표의
                // reason/improvements 값까지 단정해 판별 근거를 따로 세워야 한다.
                .andExpect(jsonPath("$.job_position").value("백엔드 개발자"))
                .andExpect(jsonPath("$.job_description").value("Spring Boot 기반 백엔드 개발"))
                .andExpect(jsonPath("$.job_career").value("경력 3년"))
                .andExpect(jsonPath("$.evaluation.problem_solving.score").value(90))
                .andExpect(jsonPath("$.evaluation.problem_solving.weight").value(0.25))
                .andExpect(jsonPath("$.evaluation.problem_solving.reason").isArray())
                .andExpect(jsonPath("$.evaluation.problem_solving.reason[0]").value("근거1"))
                .andExpect(jsonPath("$.evaluation.problem_solving.improvements[0]").value("보완1"))
                .andExpect(jsonPath("$.evaluation.project_experience.score").value(80))
                .andExpect(jsonPath("$.evaluation.project_experience.weight").value(0.25))
                .andExpect(jsonPath("$.evaluation.technical_skills.score").value(70))
                .andExpect(jsonPath("$.evaluation.technical_skills.weight").value(0.25))
                .andExpect(jsonPath("$.evaluation.soft_skills.score").value(60))
                .andExpect(jsonPath("$.evaluation.soft_skills.weight").value(0.10))
                .andExpect(jsonPath("$.evaluation.jd_fit.score").value(70))
                .andExpect(jsonPath("$.evaluation.jd_fit.weight").value(0.15))
                .andExpect(jsonPath("$.evaluation.total_score").value(77))
                .andExpect(jsonPath("$.evaluation.total_feedback").value("전반적으로 우수합니다."))
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andExpect(jsonPath("$.question_retryable").doesNotExist())
                .andExpect(jsonPath("$.resume").doesNotExist())
                .andDo(document("resume-analysis-get-evaluation-completed",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_description").description("채용 공고"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시"),
                                fieldWithPath("evaluation").description("평가 결과 (평가 완료 이후에만)"),
                                fieldWithPath("evaluation.problem_solving").description("문제 해결력"),
                                fieldWithPath("evaluation.problem_solving.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.problem_solving.weight").description("가중치"),
                                fieldWithPath("evaluation.problem_solving.reason").description("근거 목록"),
                                fieldWithPath("evaluation.problem_solving.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.project_experience").description("프로젝트 경험"),
                                fieldWithPath("evaluation.project_experience.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.project_experience.weight").description("가중치"),
                                fieldWithPath("evaluation.project_experience.reason").description("근거 목록"),
                                fieldWithPath("evaluation.project_experience.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.technical_skills").description("기술 역량"),
                                fieldWithPath("evaluation.technical_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.technical_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.technical_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.technical_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.soft_skills").description("소프트 스킬"),
                                fieldWithPath("evaluation.soft_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.soft_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.soft_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.soft_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.jd_fit").description("JD 적합성 (채용 공고 제공 시에만)"),
                                fieldWithPath("evaluation.jd_fit.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.jd_fit.weight").description("가중치"),
                                fieldWithPath("evaluation.jd_fit.reason").description("근거 목록"),
                                fieldWithPath("evaluation.jd_fit.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.total_score").description("가중 종합 점수"),
                                fieldWithPath("evaluation.total_feedback").description("종합 총평")
                        )
                ));
    }

    @Test
    void 이력서_분석_조회_평가완료_JD미제공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluatedWithoutJd(member,
                ResumeAnalysisState.EVALUATION_COMPLETED));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jd_provided").value(false))
                .andExpect(jsonPath("$.job_description").doesNotExist())
                .andExpect(jsonPath("$.evaluation.problem_solving.weight").value(0.3))
                .andExpect(jsonPath("$.evaluation.jd_fit").doesNotExist())
                .andExpect(jsonPath("$.evaluation.total_score").value(78))
                .andDo(document("resume-analysis-get-evaluation-completed-without-jd",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부 (false)"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시"),
                                fieldWithPath("evaluation").description("평가 결과 (JD 미제공이므로 4지표)"),
                                fieldWithPath("evaluation.problem_solving").description("문제 해결력"),
                                fieldWithPath("evaluation.problem_solving.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.problem_solving.weight").description("가중치 (0.30)"),
                                fieldWithPath("evaluation.problem_solving.reason").description("근거 목록"),
                                fieldWithPath("evaluation.problem_solving.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.project_experience").description("프로젝트 경험"),
                                fieldWithPath("evaluation.project_experience.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.project_experience.weight").description("가중치 (0.30)"),
                                fieldWithPath("evaluation.project_experience.reason").description("근거 목록"),
                                fieldWithPath("evaluation.project_experience.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.technical_skills").description("기술 역량"),
                                fieldWithPath("evaluation.technical_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.technical_skills.weight").description("가중치 (0.30)"),
                                fieldWithPath("evaluation.technical_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.technical_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.soft_skills").description("소프트 스킬"),
                                fieldWithPath("evaluation.soft_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.soft_skills.weight").description("가중치 (0.10)"),
                                fieldWithPath("evaluation.soft_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.soft_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.total_score").description("가중 종합 점수"),
                                fieldWithPath("evaluation.total_feedback").description("종합 총평")
                        )
                ));
    }

    @Test
    void 이력서_분석_조회_완료() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluatedWithJd(member,
                ResumeAnalysisState.COMPLETED));
        generatedQuestionRepository.saveAll(GeneratedQuestionForAnalysisFixtureBuilder.five(analysis));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.interview_available").value(true))
                .andExpect(jsonPath("$.questions.length()").value(5))
                .andExpect(jsonPath("$.questions[0].question_order").value(0))
                .andExpect(jsonPath("$.questions[0].generated_question_id").exists())
                // question과 reason도 같은 타입 이웃이다. 값을 봐야 교환을 잡는다.
                .andExpect(jsonPath("$.questions[0].question").value("이력서 기반 질문 0"))
                .andExpect(jsonPath("$.questions[0].reason").value("질문 선정 이유 0"))
                .andExpect(jsonPath("$.questions[4].question_order").value(4))
                .andExpect(jsonPath("$.question_retryable").doesNotExist())
                .andDo(document("resume-analysis-get-completed",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부 (회원 + COMPLETED)"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_description").description("채용 공고"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시"),
                                fieldWithPath("evaluation").description("평가 결과"),
                                fieldWithPath("evaluation.problem_solving").description("문제 해결력"),
                                fieldWithPath("evaluation.problem_solving.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.problem_solving.weight").description("가중치"),
                                fieldWithPath("evaluation.problem_solving.reason").description("근거 목록"),
                                fieldWithPath("evaluation.problem_solving.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.project_experience").description("프로젝트 경험"),
                                fieldWithPath("evaluation.project_experience.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.project_experience.weight").description("가중치"),
                                fieldWithPath("evaluation.project_experience.reason").description("근거 목록"),
                                fieldWithPath("evaluation.project_experience.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.technical_skills").description("기술 역량"),
                                fieldWithPath("evaluation.technical_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.technical_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.technical_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.technical_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.soft_skills").description("소프트 스킬"),
                                fieldWithPath("evaluation.soft_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.soft_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.soft_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.soft_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.jd_fit").description("JD 적합성"),
                                fieldWithPath("evaluation.jd_fit.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.jd_fit.weight").description("가중치"),
                                fieldWithPath("evaluation.jd_fit.reason").description("근거 목록"),
                                fieldWithPath("evaluation.jd_fit.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.total_score").description("가중 종합 점수"),
                                fieldWithPath("evaluation.total_feedback").description("종합 총평"),
                                fieldWithPath("questions").description("생성된 면접 질문 목록 (COMPLETED에서만)"),
                                fieldWithPath("questions[].generated_question_id").description("질문 ID"),
                                fieldWithPath("questions[].question_order").description("질문 순서 (0부터)"),
                                fieldWithPath("questions[].question").description("질문 내용"),
                                fieldWithPath("questions[].reason").description("질문 의도")
                        )
                ));
    }

    @Test
    void 이력서_분석_조회_평가실패() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .state(ResumeAnalysisState.EVALUATION_FAILED)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EVALUATION_FAILED"))
                .andExpect(jsonPath("$.evaluation").doesNotExist())
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andExpect(jsonPath("$.question_retryable").doesNotExist())
                .andDo(document("resume-analysis-get-evaluation-failed",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태 (EVALUATION_FAILED)"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부 (false)"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시")
                        )
                ));
    }

    @Test
    void 이력서_분석_조회_질문생성실패() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluatedWithJd(member,
                ResumeAnalysisState.QUESTION_FAILED));
        resumeAnalysisSourceTextRepository.save(ResumeAnalysisSourceTextFixtureBuilder.builder()
                .analysis(analysis)
                .resumeContent("Java, Spring Boot 경험 3년.")
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUESTION_FAILED"))
                .andExpect(jsonPath("$.question_retryable").value(true))
                .andExpect(jsonPath("$.evaluation.total_score").value(77))
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andExpect(jsonPath("$.interview_available").value(false))
                .andDo(document("resume-analysis-get-question-failed",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태 (QUESTION_FAILED)"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부 (false)"),
                                fieldWithPath("question_retryable").description("질문 재생성 가능 여부"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_description").description("채용 공고"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시"),
                                fieldWithPath("evaluation").description("평가 결과 (질문만 실패했으므로 유지)"),
                                fieldWithPath("evaluation.problem_solving").description("문제 해결력"),
                                fieldWithPath("evaluation.problem_solving.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.problem_solving.weight").description("가중치"),
                                fieldWithPath("evaluation.problem_solving.reason").description("근거 목록"),
                                fieldWithPath("evaluation.problem_solving.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.project_experience").description("프로젝트 경험"),
                                fieldWithPath("evaluation.project_experience.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.project_experience.weight").description("가중치"),
                                fieldWithPath("evaluation.project_experience.reason").description("근거 목록"),
                                fieldWithPath("evaluation.project_experience.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.technical_skills").description("기술 역량"),
                                fieldWithPath("evaluation.technical_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.technical_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.technical_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.technical_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.soft_skills").description("소프트 스킬"),
                                fieldWithPath("evaluation.soft_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.soft_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.soft_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.soft_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.jd_fit").description("JD 적합성"),
                                fieldWithPath("evaluation.jd_fit.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.jd_fit.weight").description("가중치"),
                                fieldWithPath("evaluation.jd_fit.reason").description("근거 목록"),
                                fieldWithPath("evaluation.jd_fit.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.total_score").description("가중 종합 점수"),
                                fieldWithPath("evaluation.total_feedback").description("종합 총평")
                        )
                ));
    }

    // 원문이 남아 있어도 재생성 횟수를 모두 소진하면 false다. 이 단정이 없으면 파사드가
    // question_retryable에 항상 true를 실어도 아무 테스트가 깨지지 않는다.
    @Test
    void 재생성_횟수를_모두_소진하면_question_retryable이_false다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .jobDescription("Spring Boot 기반 백엔드 개발")
                .state(ResumeAnalysisState.QUESTION_FAILED)
                .questionRetryCount(2)
                .build());
        resumeAnalysisSourceTextRepository.save(ResumeAnalysisSourceTextFixtureBuilder.builder()
                .analysis(analysis)
                .resumeContent("Java, Spring Boot 경험 3년.")
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUESTION_FAILED"))
                .andExpect(jsonPath("$.question_retryable").value(false));
    }

    @Test
    void 비회원_이력서_분석_조회_성공() throws Exception {
        // given
        String guestToken = UUID.randomUUID().toString();
        ResumeAnalysis analysis = resumeAnalysisRepository.save(guestCompleted(guestToken, "11.22.33.53"));
        generatedQuestionRepository.saveAll(GeneratedQuestionForAnalysisFixtureBuilder.five(analysis));

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .param("guest_token", guestToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.interview_available").value(false))
                .andExpect(jsonPath("$.questions.length()").value(5))
                .andExpect(jsonPath("$.evaluation.jd_fit").doesNotExist())
                .andExpect(jsonPath("$.resume").doesNotExist())
                .andDo(document("resume-analysis-get-guest",
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        queryParameters(
                                parameterWithName("guest_token").description("비회원 소유 증명 토큰 (제출 응답의 guest_token)")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("상태"),
                                fieldWithPath("jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("interview_available").description("면접 시작 가능 여부 (비회원은 항상 false)"),
                                fieldWithPath("job_position").description("지원 직무"),
                                fieldWithPath("job_career").description("경력 사항"),
                                fieldWithPath("created_at").description("제출 일시"),
                                fieldWithPath("evaluation").description("평가 결과"),
                                fieldWithPath("evaluation.problem_solving").description("문제 해결력"),
                                fieldWithPath("evaluation.problem_solving.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.problem_solving.weight").description("가중치"),
                                fieldWithPath("evaluation.problem_solving.reason").description("근거 목록"),
                                fieldWithPath("evaluation.problem_solving.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.project_experience").description("프로젝트 경험"),
                                fieldWithPath("evaluation.project_experience.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.project_experience.weight").description("가중치"),
                                fieldWithPath("evaluation.project_experience.reason").description("근거 목록"),
                                fieldWithPath("evaluation.project_experience.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.technical_skills").description("기술 역량"),
                                fieldWithPath("evaluation.technical_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.technical_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.technical_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.technical_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.soft_skills").description("소프트 스킬"),
                                fieldWithPath("evaluation.soft_skills.score").description("점수 (0~100)"),
                                fieldWithPath("evaluation.soft_skills.weight").description("가중치"),
                                fieldWithPath("evaluation.soft_skills.reason").description("근거 목록"),
                                fieldWithPath("evaluation.soft_skills.improvements").description("보완점 목록"),
                                fieldWithPath("evaluation.total_score").description("가중 종합 점수"),
                                fieldWithPath("evaluation.total_feedback").description("종합 총평"),
                                fieldWithPath("questions").description("생성된 면접 질문 목록"),
                                fieldWithPath("questions[].generated_question_id").description("질문 ID"),
                                fieldWithPath("questions[].question_order").description("질문 순서 (0부터)"),
                                fieldWithPath("questions[].question").description("질문 내용"),
                                fieldWithPath("questions[].reason").description("질문 의도")
                        )
                ));
    }

    @Test
    void 내_이력서_분석_목록_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis completed = resumeAnalysisRepository.save(evaluatedWithJd(member,
                ResumeAnalysisState.COMPLETED));
        generatedQuestionRepository.saveAll(GeneratedQuestionForAnalysisFixtureBuilder.five(completed));
        resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .state(ResumeAnalysisState.PENDING)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .param("state", "COMPLETED")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].analysis_id").value(completed.getId()))
                .andExpect(jsonPath("$.data[0].total_score").value(77))
                .andExpect(jsonPath("$.data[0].question_count").value(5))
                // 같은 타입이 이웃한 자리는 컴파일러가 봐 주지 않는다. 존재 여부가 아니라 값을 단정해야
                // job_position과 job_career가 뒤바뀐 채로 응답되는 것을 잡을 수 있다.
                .andExpect(jsonPath("$.data[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].job_position").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data[0].job_career").value("경력 3년"))
                .andExpect(jsonPath("$.data[0].jd_provided").value(true))
                // 초 단위까지 단정한다. DB의 DATETIME(6)이 잘라내는 것보다 아래인 나노초까지 비교하면
                // 메모리 엔티티와 어긋나 간헐 실패하지만, 초 단위는 양쪽이 항상 일치한다.
                .andExpect(jsonPath("$.data[0].created_at")
                        .value(startsWith(completed.getCreatedAt().truncatedTo(ChronoUnit.SECONDS).toString())))
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.has_next").value(false))
                .andDo(document("resume-analysis-list",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("state").description("상태 필터 (선택)").optional(),
                                parameterWithName("page").description("페이지 번호 (기본 0)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20)").optional(),
                                parameterWithName("sort").description("정렬 (기본 createdAt,DESC)").optional()
                        ),
                        responseFields(
                                fieldWithPath("data").description("이력서 분석 목록"),
                                fieldWithPath("data[].analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("data[].state").description("상태"),
                                fieldWithPath("data[].job_position").description("지원 직무"),
                                fieldWithPath("data[].job_career").description("경력 사항"),
                                fieldWithPath("data[].jd_provided").description("채용 공고 제공 여부"),
                                fieldWithPath("data[].total_score").description("가중 종합 점수 (평가 완료 이후에만)"),
                                fieldWithPath("data[].question_count").description("생성된 질문 개수"),
                                fieldWithPath("data[].created_at").description("제출 일시"),
                                fieldWithPath("current_page").description("현재 페이지 번호"),
                                fieldWithPath("total_count").description("전체 건수"),
                                fieldWithPath("total_pages").description("전체 페이지 수"),
                                fieldWithPath("has_next").description("다음 페이지 존재 여부")
                        )
                ));
    }

    // state 파라미터를 아예 보내지 않는 경로를 고정한다. parseStateOrNull의 blank 가드를 지우면
    // ResumeAnalysisState.valueOf(null)이 NPE를 던져 500이 되므로 이 테스트가 그 결함을 잡는다.
    @Test
    void state_필터가_없으면_상태와_무관하게_전체_목록을_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(evaluatedWithJd(member, ResumeAnalysisState.COMPLETED));
        resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .state(ResumeAnalysisState.PENDING)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.total_count").value(2))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.has_next").value(false));
    }

    @Test
    void 분석이_하나도_없으면_빈_목록을_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.current_page").value(0))
                .andExpect(jsonPath("$.total_count").value(0))
                .andExpect(jsonPath("$.total_pages").value(0))
                .andExpect(jsonPath("$.has_next").value(false));
    }

    // 목록 두 경로(state 필터 유/무)가 모두 회원 범위로 좁혀지는지 고정한다.
    @Test
    void 남의_분석은_내_목록에_보이지_않는다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis mine = resumeAnalysisRepository.save(evaluatedWithJd(member,
                ResumeAnalysisState.COMPLETED));
        resumeAnalysisRepository.save(evaluatedWithJd(other, ResumeAnalysisState.COMPLETED));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].analysis_id").value(mine.getId()))
                .andExpect(jsonPath("$.total_count").value(1));
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .param("state", "COMPLETED")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].analysis_id").value(mine.getId()))
                .andExpect(jsonPath("$.total_count").value(1));
    }

    @Test
    void 비회원_이력서_분석_회원_귀속_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        String guestToken = UUID.randomUUID().toString();
        ResumeAnalysis analysis = resumeAnalysisRepository.save(guestCompleted(guestToken, "11.22.33.54"));
        MockHttpSession session = loginSession(member);

        String requestJson = """
                {
                    "guest_token": "%s"
                }
                """.formatted(guestToken);

        // when & then
        mockMvc.perform(post("/api/v1/resume-analyses/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis_id").value(analysis.getId()))
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andDo(document("resume-analysis-claim",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("guest_token").description("비회원 소유 증명 토큰")
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("귀속된 이력서 분석 ID (claim 전후 동일)"),
                                fieldWithPath("state").description("상태")
                        )
                ));
    }

    @Test
    void 질문_재생성_요청_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluatedWithJd(member,
                ResumeAnalysisState.QUESTION_FAILED));
        resumeAnalysisSourceTextRepository.save(ResumeAnalysisSourceTextFixtureBuilder.builder()
                .analysis(analysis)
                .resumeContent("Java, Spring Boot 경험 3년.")
                .build());
        given(resumeAnalysisAsyncService.readCommand(anyLong())).willReturn(new ResumeAnalysisCommand(
                analysis.getId(), null, true, "Java, Spring Boot 경험 3년.", null,
                "백엔드 개발자", "Spring Boot 기반 백엔드 개발", "경력 3년"));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/resume-analyses/{analysisId}/questions/retry", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysis_id").value(analysis.getId()))
                .andExpect(jsonPath("$.state").value("EVALUATION_COMPLETED"))
                .andExpect(jsonPath("$.question_retry_count").value(1))
                .andDo(document("resume-analysis-question-retry",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
                        ),
                        queryParameters(
                                parameterWithName("guest_token").description("비회원 소유 증명 토큰 (비회원만 사용)")
                                        .optional()
                        ),
                        responseFields(
                                fieldWithPath("analysis_id").description("이력서 분석 ID"),
                                fieldWithPath("state").description("복원된 상태 (EVALUATION_COMPLETED)"),
                                fieldWithPath("question_retry_count").description("누적 재시도 횟수 (최대 2)")
                        )
                ));
    }

    // 컨트롤러가 받은 guest_token을 파사드로 넘기는지 고정한다. null을 넘기면 게스트 재생성이 403이 된다.
    @Test
    void 게스트는_guest_token으로_질문_재생성을_요청할_수_있다() throws Exception {
        // given
        String guestToken = UUID.randomUUID().toString();
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .guest(guestToken, "11.22.33.58")
                .jobDescription("Spring Boot 기반 백엔드 개발")
                .state(ResumeAnalysisState.QUESTION_FAILED)
                .build());
        resumeAnalysisSourceTextRepository.save(ResumeAnalysisSourceTextFixtureBuilder.builder()
                .analysis(analysis)
                .resumeContent("Java, Spring Boot 경험 3년.")
                .build());
        given(resumeAnalysisAsyncService.readCommand(anyLong())).willReturn(new ResumeAnalysisCommand(
                analysis.getId(), null, true, "Java, Spring Boot 경험 3년.", null,
                "백엔드 개발자", "Spring Boot 기반 백엔드 개발", "신입"));

        // when & then
        mockMvc.perform(post("/api/v1/resume-analyses/{analysisId}/questions/retry", analysis.getId())
                        .param("guest_token", guestToken)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysis_id").value(analysis.getId()))
                .andExpect(jsonPath("$.state").value("EVALUATION_COMPLETED"))
                .andExpect(jsonPath("$.question_retry_count").value(1));
    }

    @Test
    void 이력서_분석_이용_상태_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/usage-status")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first_use_free").value(true))
                .andExpect(jsonPath("$.token_cost").value(5))
                .andDo(document("resume-analysis-usage-status",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("first_use_free").description("첫 사용 무료 대상 여부"),
                                fieldWithPath("token_cost").description("분석 1회 토큰 비용 (항상 5)")
                        )
                ));
    }

    // 컨트롤러가 인증 주체의 memberId를 그대로 넘기는지, 그리고 유료 전환(false)이 실제로 관측되는지
    // 함께 고정한다. 응답을 true로 굳혀도, 다른 회원의 이력을 봐도 이 테스트가 깨진다.
    @Test
    void 이용_상태는_인증_주체의_이력을_본다() throws Exception {
        // given
        Member freeMember = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member paidMember = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(paidMember)
                .state(ResumeAnalysisState.COMPLETED)
                .build());
        MockHttpSession freeSession = loginSession(freeMember);
        MockHttpSession paidSession = loginSession(paidMember);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/usage-status")
                        .header("Cookie", "JSESSIONID=" + freeSession.getId())
                        .session(freeSession)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first_use_free").value(true))
                .andExpect(jsonPath("$.token_cost").value(5));
        mockMvc.perform(get("/api/v1/resume-analyses/usage-status")
                        .header("Cookie", "JSESSIONID=" + paidSession.getId())
                        .session(paidSession)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first_use_free").value(false))
                .andExpect(jsonPath("$.token_cost").value(5));
    }

    // 기본 페이지 크기 20과 기본 정렬 방향(createdAt DESC)을 함께 고정한다. 21건을 넣어야 크기 상한과
    // has_next의 true 경로가 동시에 관측된다.
    @Test
    void 기본_페이지_크기는_20이고_최신순으로_정렬된다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        List<Long> savedIds = new ArrayList<>();
        for (int count = 0; count < 21; count++) {
            savedIds.add(resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                    .member(member)
                    .state(ResumeAnalysisState.PENDING)
                    .build()).getId());
        }
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(20))
                .andExpect(jsonPath("$.data[0].analysis_id").value(savedIds.get(20)))
                .andExpect(jsonPath("$.data[19].analysis_id").value(savedIds.get(1)))
                .andExpect(jsonPath("$.current_page").value(0))
                .andExpect(jsonPath("$.total_count").value(21))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.has_next").value(true));
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .param("page", "1")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].analysis_id").value(savedIds.get(0)))
                .andExpect(jsonPath("$.current_page").value(1))
                .andExpect(jsonPath("$.has_next").value(false));
    }

    @Test
    void 인증없이_목록을_조회하면_401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증없이_이용_상태나_귀속을_요청하면_401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/usage-status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/resume-analyses/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_token\": \"" + UUID.randomUUID() + "\"}")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 남의_분석을_조회하면_403() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(owner)
                .state(ResumeAnalysisState.COMPLETED)
                .build());
        MockHttpSession session = loginSession(other);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 이력서 분석만 조회할 수 있습니다."));
    }

    @Test
    void 존재하지_않는_분석을_조회하면_404() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", 999_999L)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 이력서 분석입니다. analysisId: 999999"));
    }

    @Test
    void 숫자가_아닌_분석_ID는_404() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", "not-a-number")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 이력서 분석입니다."));
    }

    @Test
    void guest_token없이_게스트_분석을_조회하면_403() throws Exception {
        // given
        ResumeAnalysis analysis = resumeAnalysisRepository.save(
                guestCompleted(UUID.randomUUID().toString(), "11.22.33.55"));

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 이력서 분석만 조회할 수 있습니다."));
    }

    @Test
    void 잘못된_guest_token으로_조회하면_403() throws Exception {
        // given
        ResumeAnalysis analysis = resumeAnalysisRepository.save(
                guestCompleted(UUID.randomUUID().toString(), "11.22.33.56"));

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .param("guest_token", UUID.randomUUID().toString())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 이력서 분석만 조회할 수 있습니다."));
    }

    @Test
    void claim_후_옛_guest_token으로_조회하면_403() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        String guestToken = UUID.randomUUID().toString();
        ResumeAnalysis analysis = resumeAnalysisRepository.save(guestCompleted(guestToken, "11.22.33.57"));
        MockHttpSession session = loginSession(member);
        mockMvc.perform(post("/api/v1/resume-analyses/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_token\": \"" + guestToken + "\"}")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk());

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses/{analysisId}", analysis.getId())
                        .param("guest_token", guestToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 이력서 분석만 조회할 수 있습니다."));
    }

    @Test
    void claim_guest_token이_공백이면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/resume-analyses/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_token\": \"  \"}")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("게스트 토큰은 필수입니다."));
    }

    @Test
    void PDF가_아닌_파일을_제출하면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);
        doThrow(new BadRequestException("파일은 PDF 형식만 업로드 가능합니다."))
                .when(pdfValidator).validate(any(MultipartFile.class));

        MockMultipartFile textFile = new MockMultipartFile("resume", "resume.txt", "text/plain",
                "이력서 내용".getBytes());

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(textFile)
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("파일은 PDF 형식만 업로드 가능합니다."));
    }

    @Test
    void 페이지_수_상한을_넘는_PDF를_제출하면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);
        stubExtraction();
        doThrow(new BadRequestException("이력서 PDF의 페이지 수가 너무 많습니다."))
                .when(resumeAnalysisPdfPolicy).validatePageCount(any(MultipartFile.class));

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이력서 PDF의 페이지 수가 너무 많습니다."));
    }

    @Test
    void 이력서_파일과_ID가_모두_없으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이력서 파일 또는 이력서 ID는 필수입니다."));
    }

    // 요청 자체가 잘못된 경우 상태 검사보다 먼저 400이 나간다. ResumeAnalysisSubmitRequest의 이력서 필수 검사와
    // 파사드 extractResumeText의 검사가 메시지까지 같아서, 이 순서 단정만이 요청 DTO의 분기를 고정한다.
    @Test
    void 이력서_파일과_ID_누락은_진행_중_분석_검사보다_먼저_400이다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .state(ResumeAnalysisState.PENDING)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이력서 파일 또는 이력서 ID는 필수입니다."));
    }

    @Test
    void job_position이_없으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원 직무는 필수입니다."));
    }

    @Test
    void job_career가_없으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("경력 사항은 필수입니다."));
    }

    @Test
    void job_position이_500자를_넘으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "가".repeat(501).getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원 직무는 500자를 초과할 수 없습니다."));
    }

    @Test
    void job_career가_100자를_넘으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "가".repeat(101).getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("경력 사항은 100자를 초과할 수 없습니다."));
    }

    @Test
    void job_description이_10000자를_넘으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_description", "가".repeat(10_001).getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("채용 공고는 10000자를 초과할 수 없습니다."));
    }

    @Test
    void resume_id가_숫자가_아니면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file("resume_id", "abc".getBytes())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 ID 형식입니다: abc"));
    }

    @Test
    void 토큰이_부족하면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(0).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .state(ResumeAnalysisState.COMPLETED)
                .build());
        MockHttpSession session = loginSession(member);
        stubExtraction();

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("토큰 갯수가 부족합니다."));
    }

    @Test
    void 진행_중_분석이_있으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .state(ResumeAnalysisState.PENDING)
                .build());
        MockHttpSession session = loginSession(member);
        stubExtraction();

        // when & then
        mockMvc.perform(multipart("/api/v1/resume-analyses")
                        .file(resumeFile())
                        .file("job_position", "백엔드 개발자".getBytes())
                        .file("job_career", "신입".getBytes())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 진행 중인 이력서 분석이 있습니다."));
    }

    @Test
    void 잘못된_state_파라미터는_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get("/api/v1/resume-analyses")
                        .param("state", "WRONG_STATE")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 상태 값입니다: WRONG_STATE"));
    }

    private MockHttpSession loginSession(Member member) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        return session;
    }

    private void stubExtraction() {
        doNothing().when(pdfValidator).validate(any(MultipartFile.class));
        doNothing().when(resumeAnalysisPdfPolicy).validatePageCount(any(MultipartFile.class));
        given(pdfTextExtractor.extractTextWithLinks(any(MultipartFile.class)))
                .willReturn("Java, Spring Boot 경험 3년. 백엔드 개발자입니다.");
    }

    private MockMultipartFile resumeFile() {
        return new MockMultipartFile("resume", "resume.pdf", "application/pdf", "이력서 내용".getBytes());
    }

    private MockMultipartFile portfolioFile() {
        return new MockMultipartFile("portfolio", "portfolio.pdf", "application/pdf", "포트폴리오 내용".getBytes());
    }

    private ResumeAnalysis evaluatedWithJd(Member member, ResumeAnalysisState state) {
        return ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .jobPosition("백엔드 개발자")
                .jobDescription("Spring Boot 기반 백엔드 개발")
                .jobCareer("경력 3년")
                .problemSolving(DimensionScoreFixture.of(90, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .projectExperience(DimensionScoreFixture.of(80, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .technicalSkills(DimensionScoreFixture.of(70, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .softSkills(DimensionScoreFixture.of(60, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .jdFit(DimensionScoreFixture.of(70, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .totalFeedback("전반적으로 우수합니다.")
                .state(state)
                .build();
    }

    private ResumeAnalysis evaluatedWithoutJd(Member member, ResumeAnalysisState state) {
        return ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .jobPosition("백엔드 개발자")
                .jobCareer("신입")
                .problemSolving(DimensionScoreFixture.of(90, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .projectExperience(DimensionScoreFixture.of(80, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .technicalSkills(DimensionScoreFixture.of(70, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .softSkills(DimensionScoreFixture.of(60, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .totalFeedback("전반적으로 우수합니다.")
                .state(state)
                .build();
    }

    private ResumeAnalysis guestCompleted(String guestToken, String guestIp) {
        return ResumeAnalysisFixtureBuilder.builder()
                .guest(guestToken, guestIp)
                .jobPosition("백엔드 개발자")
                .jobCareer("신입")
                .problemSolving(DimensionScoreFixture.of(90, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .projectExperience(DimensionScoreFixture.of(80, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .technicalSkills(DimensionScoreFixture.of(70, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .softSkills(DimensionScoreFixture.of(60, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .totalFeedback("전반적으로 우수합니다.")
                .state(ResumeAnalysisState.COMPLETED)
                .build();
    }
}
