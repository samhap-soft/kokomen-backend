package com.samhap.kokomen.interview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.DimensionScoreFixture;
import com.samhap.kokomen.global.fixture.resume.GeneratedQuestionForAnalysisFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class ResumeAnalysisInterviewControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void 이력서_분석_기반_면접_시작_텍스트모드_성공() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        desyncIdSequences(member, question);
        MockHttpSession session = loginSession(member);

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """.formatted(question.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interview_id").value(2))
                .andExpect(jsonPath("$.question_id").value(3))
                .andExpect(jsonPath("$.root_question").value(question.getContent()))
                .andDo(document("resume-analysis-interview-start-text-mode",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
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
    void 이력서_분석_기반_면접_시작_음성모드_성공() throws Exception {
        // given
        given(supertoneClient.request(any())).willReturn(new SupertoneResponse(new byte[0]));
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        desyncIdSequences(member, question);
        MockHttpSession session = loginSession(member);

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5,
                    "mode": "VOICE"
                }
                """.formatted(question.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interview_id").value(2))
                .andExpect(jsonPath("$.question_id").value(3))
                .andExpect(jsonPath("$.root_question_voice_url")
                        .value("https://dhtg8wzvkbfxr.cloudfront.net/mock-path/3.wav"))
                .andDo(document("resume-analysis-interview-start-voice-mode",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("analysisId").description("이력서 분석 ID")
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
    void 미claim_게스트_분석으로_면접을_시작하면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .guest(UUID.randomUUID().toString(), "11.22.33.62")
                .state(ResumeAnalysisState.COMPLETED)
                .build());
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("먼저 이력서 분석을 내 계정에 연결해야 합니다."));
    }

    @Test
    void 질문_생성이_완료되지_않은_분석으로_면접을_시작하면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.EVALUATION_COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문 생성이 완료되지 않았습니다."));
    }

    @Test
    void 존재하지_않는_질문으로_면접을_시작하면_404() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        saveFiveQuestions(analysis);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(999_999L, 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 질문입니다."));
    }

    @Test
    void 분석에_속하지_않는_질문으로_면접을_시작하면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis target = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        ResumeAnalysis other = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        saveFiveQuestions(target);
        GeneratedQuestion otherQuestion = saveFiveQuestions(other).get(0);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(otherQuestion.getId(), 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해당 이력서 분석에 속하지 않는 질문입니다."));
    }

    @Test
    void 토큰이_부족하면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(2);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("토큰 갯수가 부족합니다."));
    }

    @Test
    void max_question_count가_하한_미만이면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 1, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("최대 질문 개수는 3 이상 20 이하이어야 합니다."));
    }

    /**
     * 토큰 검증이 개수 검증보다 먼저 실행되므로, 토큰을 넉넉히 줘야 거절 이유가 상한 자체가 된다.
     * 21문항 텍스트 모드는 21개를 요구하므로 25개를 준다.
     */
    @Test
    void max_question_count가_상한_초과면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(25);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 21, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("최대 질문 개수는 3 이상 20 이하이어야 합니다."));
    }

    @Test
    void 질문_ID가_누락되면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        saveFiveQuestions(analysis);
        MockHttpSession session = loginSession(member);

        String requestJson = """
                {
                    "max_question_count": 5,
                    "mode": "TEXT"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문 ID는 필수입니다."));
    }

    /**
     * 세 필드의 @NotNull을 각각 검증한다. 하나만 검증하면 나머지 두 제약을 지워도 테스트가 통과하고,
     * 프로덕션은 400 대신 역참조 시점의 NPE가 전역 핸들러에 걸려 500으로 퇴화한다 -- 사용자에게는
     * 어떤 필드가 빠졌는지 알 수 없는 응답이 된다.
     */
    @Test
    void 최대_질문_개수가_누락되면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "mode": "TEXT"
                }
                """.formatted(question.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("최대 질문 개수는 필수입니다."));
    }

    @Test
    void 면접_모드가_누락되면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);

        String requestJson = """
                {
                    "generated_question_id": %d,
                    "max_question_count": 5
                }
                """.formatted(question.getId());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("면접 모드는 필수입니다."));
    }

    @Test
    void 다른_회원의_분석으로_면접을_시작하면_403() throws Exception {
        // given
        Member owner = saveMemberWithTokens(20);
        Member other = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(owner, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(other);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", analysis.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 이력서 분석만 조회할 수 있습니다."));
    }

    /**
     * 숫자이지만 없는 분석 ID는 조회 단계에서 걸리며, 파싱 실패와 달리 문제된 값을 메시지에 되싣는다.
     * 두 404의 본문이 다른 것은 의도한 차이다.
     */
    @Test
    void 존재하지_않는_분석_ID로_면접을_시작하면_404() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        MockHttpSession session = loginSession(member);
        long missingAnalysisId = analysis.getId() + 999L;

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", missingAnalysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(question.getId(), 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("존재하지 않는 이력서 분석입니다. analysisId: " + missingAnalysisId));
    }

    @Test
    void 숫자가_아닌_분석_ID로_면접을_시작하면_404() throws Exception {
        // given
        Member member = saveMemberWithTokens(20);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(1L, 5, "TEXT"))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 이력서 분석입니다."));
    }

    @Test
    void 게스트가_면접을_시작하려_하면_401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/interviews/resume-analyses/{analysisId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequestJson(1L, 5, "TEXT"))
                )
                .andExpect(status().isUnauthorized());
    }

    private String startRequestJson(Long generatedQuestionId, int maxQuestionCount, String mode) {
        return """
                {
                    "generated_question_id": %d,
                    "max_question_count": %d,
                    "mode": "%s"
                }
                """.formatted(generatedQuestionId, maxQuestionCount, mode);
    }

    private MockHttpSession loginSession(Member member) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        return session;
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    private ResumeAnalysis saveAnalysis(Member member, ResumeAnalysisState state) {
        return resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .jobPosition("백엔드 개발자")
                .jobCareer("경력 3년")
                .problemSolving(DimensionScoreFixture.of(90, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .projectExperience(DimensionScoreFixture.of(80, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .technicalSkills(DimensionScoreFixture.of(70, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .softSkills(DimensionScoreFixture.of(60, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .totalFeedback("전반적으로 우수합니다.")
                .state(state)
                .build());
    }

    private List<GeneratedQuestion> saveFiveQuestions(ResumeAnalysis analysis) {
        return generatedQuestionRepository.saveAll(GeneratedQuestionForAnalysisFixtureBuilder.five(analysis));
    }

    /**
     * interview_id와 question_id가 둘 다 1로 맞물리면 두 값이 뒤바뀌어도 단정이 통과한다.
     * 면접 1건과 질문 2건을 미리 저장해 시퀀스를 어긋내므로, 다음 면접은 2번 질문은 3번이 된다.
     */
    private void desyncIdSequences(Member member, GeneratedQuestion generatedQuestion) {
        Interview seed = interviewRepository.save(
                new Interview(member, generatedQuestion, 3, InterviewMode.TEXT));
        questionRepository.save(new Question(seed, "시퀀스 어긋내기용 질문 1"));
        questionRepository.save(new Question(seed, "시퀀스 어긋내기용 질문 2"));
    }
}
