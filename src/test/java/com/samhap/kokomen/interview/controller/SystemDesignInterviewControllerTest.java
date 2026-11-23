package com.samhap.kokomen.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignHiddenRequirementFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignLlmBulkhead;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.web.servlet.MvcResult;

class SystemDesignInterviewControllerTest extends BaseControllerTest {

    private static final String INTERVIEW_URL = "/api/v1/interviews/system-design/{interviewId}";
    private static final String COMPLETE_REQUIREMENTS_URL =
            "/api/v1/interviews/system-design/{interviewId}/requirements/complete";
    private static final String REQUIREMENT_QUESTIONS_URL =
            "/api/v1/interviews/system-design/{interviewId}/requirement-questions";
    private static final String DEFAULT_REQUIREMENT_ANSWER = "등록되지 않은 내용이에요. 가정하고 진행하세요.";
    private static final String REQUIREMENT_QUESTION_JSON = """
            {
              "question": "하루에 단축 URL이 몇 개나 만들어지나요?"
            }
            """;

    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    @Autowired
    private SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    @Autowired
    private SystemDesignLlmBulkhead llmBulkhead;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private SystemDesignAnswerRepository answerRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ObjectMapper objectMapper;

    // MySQLDatabaseCleaner가 매번 TRUNCATE해 테이블마다 id가 1부터 다시 시작하면 interview와 system_design_interview의 id,
    // 질문 PK와 순번이 우연히 같아진다. 다른 회원의 면접 기록(시스템 설계 면접 1개와 그 질문 1개, 일반 면접 1개)을 먼저 두어
    // id를 서로 어긋나게 한다. 그래야 interview id 대신 다른 테이블의 PK를 찾거나 내보내는 실수가 테스트에서 드러난다.
    // 문제는 INACTIVE라 시작 API가 고르지 않는다.
    @BeforeEach
    void setUpOtherMemberInterviews() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        SystemDesignProblemQuestion retiredQuestion = problemQuestionRepository.save(
                SystemDesignProblemQuestionFixtureBuilder.builder()
                        .problem(retiredProblem)
                        .questionOrder(1)
                        .stage(SystemDesignStage.HIGH_LEVEL)
                        .build());
        hiddenRequirementRepository.save(SystemDesignHiddenRequirementFixtureBuilder.builder()
                .problem(retiredProblem)
                .code("RETIRED_REQUIREMENT")
                .displayOrder(1)
                .build());
        SystemDesignInterview otherInterview = saveSystemDesignInterview(otherMember, retiredProblem,
                SystemDesignStage.HIGH_LEVEL, null);
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(otherInterview)
                .questionOrder(1)
                .content("지난 문제의 역질문")
                .askedRequirementCodes(List.of("RETIRED_REQUIREMENT"))
                .build());
        questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(otherInterview)
                .problemQuestion(retiredQuestion)
                .build());
        // 테스트가 기본값(OPERATING_SYSTEM, 순번 1)으로 루트 질문을 또 저장해도 (카테고리, 순번) 유니크와 겹치지 않게 한다.
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.NETWORK)
                .build());
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(otherMember)
                .rootQuestion(rootQuestion)
                .build());
    }

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
        Long interviewId = readBody(result).get("interview_id").asLong();
        Interview interview = interviewRepository.findById(interviewId).orElseThrow();
        assertAll(
                () -> assertThat(interview.getInterviewType()).isEqualTo(InterviewType.SYSTEM_DESIGN),
                () -> assertThat(interview.getMember().getId()).isEqualTo(member.getId()),
                () -> assertThat(systemDesignInterviewRepository.findByInterviewId(interviewId)).get()
                        .extracting(SystemDesignInterview::getStage)
                        .isEqualTo(SystemDesignStage.REQUIREMENTS),
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
        assertThat(interviewRepository.findByMember(member, Pageable.unpaged())).isEmpty();
    }

    @Test
    void 시작은_로그인하지_않으면_401() throws Exception {
        // given
        problemTestSupport.saveProblem();
        long interviewCount = interviewRepository.count();

        // when & then
        mockMvc.perform(post("/api/v1/interviews/system-design"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
        assertThat(interviewRepository.count()).isEqualTo(interviewCount);
    }

    @Test
    void 요구사항_단계의_면접을_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(member);

        // when
        MvcResult result = mockMvc.perform(get(INTERVIEW_URL, interviewId)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview_id").value(interviewId))
                .andExpect(jsonPath("$.interview_type").value("SYSTEM_DESIGN"))
                .andExpect(jsonPath("$.interview_state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.difficulty").value("BASIC"))
                .andExpect(jsonPath("$.topic.title").value(SystemDesignProblemTestSupport.TITLE))
                .andExpect(jsonPath("$.topic.description").value(SystemDesignProblemTestSupport.DESCRIPTION))
                .andExpect(jsonPath("$.stage").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.requirement_questions", empty()))
                .andExpect(jsonPath("$.max_requirement_question_count").value(5))
                .andExpect(jsonPath("$.cur_question_id").value(nullValue()))
                .andExpect(jsonPath("$.cur_question").value(nullValue()))
                .andExpect(jsonPath("$.prev_questions_and_answers", empty()))
                .andExpect(jsonPath("$.cur_question_count").value(0))
                .andExpect(jsonPath("$.max_question_count").value(6))
                .andExpect(jsonPath("$.diagram.blocks[*].id", contains("client", "api", "db")))
                .andExpect(jsonPath("$.diagram.connections[0].label").value("HTTPS"))
                .andExpect(jsonPath("$.diagram.connections[1].label").doesNotExist())
                .andDo(document("system-design-interview-find-requirements",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        interviewResponseFields()
                ))
                .andReturn();

        // then
        JsonNode body = readBody(result);
        assertAll(
                () -> assertThat(body.has("cur_question_id")).isTrue(),
                () -> assertThat(body.has("cur_question")).isTrue(),
                () -> assertThat(body.get("diagram"))
                        .isEqualTo(readJson(SystemDesignDiagramFixture.urlShortenerStarter()))
        );
    }

    @Test
    void 설계_단계의_면접을_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignDiagram firstAnswerDiagram = SystemDesignDiagramFixture.withCache();
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL, firstAnswerDiagram);
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(1)
                .content("하루에 단축 URL이 몇 개나 만들어지나요?")
                .answer("새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.")
                .build());
        SystemDesignQuestion firstQuestion = saveQuestion(systemDesignInterview, 1, "전체 구조를 그리고 두 요청의 처리 순서를 설명해 주세요.");
        answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                .question(firstQuestion)
                .content("API 서버 앞에 캐시를 두어 DB 조회를 줄여요.")
                .diagram(firstAnswerDiagram)
                .build());
        saveQuestion(systemDesignInterview, 2, "방금 그린 API 서버에서 단축 키를 어떻게 만드나요?");
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(member);

        // when
        MvcResult result = mockMvc.perform(get(INTERVIEW_URL, interviewId)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("HIGH_LEVEL"))
                .andExpect(jsonPath("$.requirement_questions", hasSize(1)))
                .andExpect(jsonPath("$.requirement_questions[0].question").value("하루에 단축 URL이 몇 개나 만들어지나요?"))
                .andExpect(jsonPath("$.requirement_questions[0].answer").value("새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요."))
                .andExpect(jsonPath("$.cur_question_count").value(1))
                .andExpect(jsonPath("$.cur_question_id").value(2))
                .andExpect(jsonPath("$.cur_question").value("방금 그린 API 서버에서 단축 키를 어떻게 만드나요?"))
                .andExpect(jsonPath("$.prev_questions_and_answers", hasSize(1)))
                .andExpect(jsonPath("$.prev_questions_and_answers[0].question_id").value(1))
                .andExpect(jsonPath("$.prev_questions_and_answers[0].stage").value("HIGH_LEVEL"))
                .andExpect(jsonPath("$.prev_questions_and_answers[0].question")
                        .value("전체 구조를 그리고 두 요청의 처리 순서를 설명해 주세요."))
                .andExpect(jsonPath("$.prev_questions_and_answers[0].answer").value("API 서버 앞에 캐시를 두어 DB 조회를 줄여요."))
                .andExpect(jsonPath("$.diagram.connections[1].label").doesNotExist())
                .andExpect(jsonPath("$.diagram.connections[3].label").value(""))
                .andDo(document("system-design-interview-find-in-progress",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        interviewResponseFields()
                ))
                .andReturn();

        // then
        JsonNode body = readBody(result);
        JsonNode expectedDiagram = readJson(firstAnswerDiagram);
        assertAll(
                () -> assertThat(body.get("prev_questions_and_answers").get(0).get("diagram"))
                        .isEqualTo(expectedDiagram),
                () -> assertThat(body.get("diagram")).isEqualTo(expectedDiagram)
        );
    }

    @Test
    void 시작_다이어그램이_없는_문제는_빈_다이어그램을_준다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(null), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(INTERVIEW_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagram.blocks", empty()))
                .andExpect(jsonPath("$.diagram.connections", empty()));
    }

    @Test
    void 없는_면접_숫자가_아닌_id_기존_면접_id는_404() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview categoryInterview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(INTERVIEW_URL, categoryInterview.getId() + 999L)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."))
                .andDo(document("system-design-interview-not-found",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message")
                                        .description("없는 면접, 숫자가 아닌 ID, 시스템 설계가 아닌 면접일 때의 오류 문구")
                        )
                ));
        mockMvc.perform(get(INTERVIEW_URL, "not-a-number")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        mockMvc.perform(get(INTERVIEW_URL, categoryInterview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
    }

    @Test
    void 다른_회원의_면접은_403() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(owner,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(otherMember);

        // when & then
        mockMvc.perform(get(INTERVIEW_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 면접만 볼 수 있어요."))
                .andDo(document("system-design-interview-forbidden",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("다른 회원의 면접일 때의 오류 문구")
                        )
                ));
    }

    @Test
    void 조회는_로그인하지_않으면_401() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);

        // when & then
        mockMvc.perform(get(INTERVIEW_URL, systemDesignInterview.getInterview().getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
    }

    @Test
    void 요구사항_역질문에_답한다() throws Exception {
        // given
        Member member = saveMemberWithTokens(3, 0);
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        Long interviewId = systemDesignInterview.getInterview().getId();
        String answer = "하루에 새로 만들어지는 단축 URL은 10만 개 정도예요.";
        given(systemDesignRequirementBedrockClient.answerRequirement(any()))
                .willReturn(new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), answer));
        MockHttpSession session = loginSession(member);

        // when
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("하루에 단축 URL이 몇 개나 만들어지나요?"))
                .andExpect(jsonPath("$.answer").value(answer))
                .andExpect(jsonPath("$.remaining_count").value(4))
                .andDo(document("system-design-interview-ask-requirement-question",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        requestFields(
                                fieldWithPath("question").description("요구사항 역질문. 앞뒤 공백을 지운 뒤 1~200자")
                        ),
                        responseFields(
                                fieldWithPath("question").description("앞뒤 공백을 지운 역질문. 저장한 값 그대로"),
                                fieldWithPath("answer").description("면접관 답. 문제에 정해 둔 요구사항을 근거로 만들고, "
                                        + "정해 두지 않은 내용을 물으면 `" + DEFAULT_REQUIREMENT_ANSWER + "`"),
                                fieldWithPath("remaining_count")
                                        .description("더 할 수 있는 역질문 수. 5 - (이번 질문까지 포함한 역질문 수)")
                        )
                ));

        // then
        ArgumentCaptor<SystemDesignRequirementAnswerCommand> command =
                ArgumentCaptor.forClass(SystemDesignRequirementAnswerCommand.class);
        verify(systemDesignRequirementBedrockClient).answerRequirement(command.capture());
        assertAll(
                () -> assertThat(command.getValue().interviewId()).isEqualTo(interviewId),
                () -> assertThat(command.getValue().currentQuestion()).isEqualTo("하루에 단축 URL이 몇 개나 만들어지나요?"),
                () -> assertThat(command.getValue().previousTurns()).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .extracting(SystemDesignRequirementQuestion::getQuestionOrder,
                                SystemDesignRequirementQuestion::getContent, SystemDesignRequirementQuestion::getAnswer,
                                SystemDesignRequirementQuestion::getAskedRequirementCodes)
                        .containsExactly(tuple(1, "하루에 단축 URL이 몇 개나 만들어지나요?", answer,
                                List.of("DAILY_CREATION_VOLUME"))),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(3),
                () -> assertThat(readTokenCount(member, TokenType.PAID)).isZero(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 등록되지_않은_내용을_물으면_정해진_문구로_답한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        saveRequirementQuestion(systemDesignInterview, 1);
        given(systemDesignRequirementBedrockClient.answerRequirement(any()))
                .willReturn(new SystemDesignRequirementAnswerResult(List.of(), "결제 기능은 없어요."));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "결제 기능도 만들어야 하나요?"
                                }
                                """)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(DEFAULT_REQUIREMENT_ANSWER))
                .andExpect(jsonPath("$.remaining_count").value(3));
        assertThat(readRequirementQuestions(systemDesignInterview))
                .extracting(SystemDesignRequirementQuestion::getAnswer,
                        SystemDesignRequirementQuestion::getAskedRequirementCodes)
                .last()
                .isEqualTo(tuple(DEFAULT_REQUIREMENT_ANSWER, List.of()));
    }

    @Test
    void 역질문이_5개를_넘으면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        for (int order = 1; order <= SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order);
        }
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("역질문은 5개까지 할 수 있어요."))
                .andDo(document("system-design-interview-ask-requirement-question-limit-exceeded",
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("이미 역질문을 5개 했을 때의 오류 문구")
                        )
                ));
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .hasSize(SystemDesignRequirementQuestion.MAX_COUNT),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 질문이_비었거나_201자면_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "   "
                                }
                                """)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문은 1~200자로 적어 주세요."))
                .andDo(document("system-design-interview-ask-requirement-question-invalid-length",
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message")
                                        .description("질문이 없거나, 공백뿐이거나, 앞뒤 공백을 지운 뒤 200자를 넘을 때의 오류 문구")
                        )
                ));
        for (String body : List.of("{}", "{\"question\": \"" + "가".repeat(201) + "\"}")) {
            mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, interviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("질문은 1~200자로 적어 주세요."));
        }
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    @Test
    void 요구사항_단계가_끝났으면_409() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("요구사항 단계가 끝났어요."))
                .andDo(document("system-design-interview-ask-requirement-question-conflict",
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("요구사항 정리를 끝낸 뒤(끝난 면접 포함) 역질문했을 때의 오류 문구")
                        )
                ));
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 역질문은_다른_요청이_진행_락을_쥐고_있으면_409다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요."));
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request")
        );
    }

    @Test
    void LLM이_답하지_못하면_500이고_역질문이_남지_않는다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        given(systemDesignRequirementBedrockClient.answerRequirement(any()))
                .willThrow(new ExternalApiException("시스템 설계 역질문 답변 호출에 실패했습니다."));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("질문에 답하지 못했어요. 잠시 후 다시 시도해 주세요."))
                .andDo(document("system-design-interview-ask-requirement-question-failed",
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message")
                                        .description("면접관 답을 만들지 못했을 때의 오류 문구. 저장되지 않아 남은 개수가 줄지 않고 다시 물을 수 있다")
                        )
                ));
        assertAll(
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 역질문이_몰려_동시_실행_허가를_못_받으면_503() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(member);
        ExecutorService executor = Executors.newFixedThreadPool(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch acquired = new CountDownLatch(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> occupants = new ArrayList<>();
        try {
            for (int i = 0; i < SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS; i++) {
                occupants.add(executor.submit(() -> llmBulkhead.execute(() -> {
                    acquired.countDown();
                    return awaitQuietly(release);
                })));
            }
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();

            // when & then
            mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUIREMENT_QUESTION_JSON)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요."))
                    .andDo(document("system-design-interview-ask-requirement-question-busy",
                            pathParameters(
                                    parameterWithName("interviewId").description("시스템 설계 면접 ID")
                            ),
                            responseFields(
                                    fieldWithPath("message")
                                            .description("역질문 요청이 몰려 2초 안에 처리를 시작하지 못했을 때의 오류 문구. 잠시 뒤 다시 보내면 된다")
                            )
                    ));
        } finally {
            release.countDown();
            for (Future<?> occupant : occupants) {
                occupant.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 역질문_없는_면접은_404() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview categoryInterview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        for (Object interviewId : List.of(systemDesignInterview.getInterview().getId() + 999L, "not-a-number",
                categoryInterview.getId())) {
            mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, interviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUIREMENT_QUESTION_JSON)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        }
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 역질문_다른_회원의_면접은_403() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(owner,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(otherMember);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 면접만 볼 수 있어요."));
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(otherMember), String.class)).isEmpty()
        );
    }

    @Test
    void 역질문은_로그인하지_않으면_401() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);

        // when & then
        mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, systemDesignInterview.getInterview().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUIREMENT_QUESTION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    @Test
    void 요구사항_정리를_끝내면_첫_기준_질문을_낸다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS, null);
        saveRequirementQuestion(systemDesignInterview, 1);
        saveRequirementQuestion(systemDesignInterview, 2);
        String firstBaseQuestion = readBaseQuestion(problem, 1);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("HIGH_LEVEL"))
                .andExpect(jsonPath("$.cur_question_id").value(1))
                .andExpect(jsonPath("$.cur_question").value(firstBaseQuestion))
                .andDo(document("system-design-interview-complete-requirements",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("stage").description("바뀐 단계. 항상 HIGH_LEVEL"),
                                fieldWithPath("cur_question_id").description("첫 설계 질문의 순번. 항상 1"),
                                fieldWithPath("cur_question").description("첫 설계 질문. 문제의 1번 기준 질문 그대로")
                        )
                ));
        List<SystemDesignQuestion> questions = readQuestions(systemDesignInterview);
        assertAll(
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(questions).extracting(SystemDesignQuestion::getQuestionOrder,
                                SystemDesignQuestion::getStage, SystemDesignQuestion::getContent)
                        .containsExactly(tuple(1, SystemDesignStage.HIGH_LEVEL, firstBaseQuestion)),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 역질문_없이도_끝낼_수_있다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("HIGH_LEVEL"))
                .andExpect(jsonPath("$.cur_question_id").value(1));
        assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.HIGH_LEVEL);
    }

    @Test
    void 이미_설계_단계면_409() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 설계 단계예요."))
                .andDo(document("system-design-interview-complete-requirements-conflict",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("요구사항 단계가 이미 끝났을 때의 오류 문구")
                        )
                ));
        assertAll(
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(readQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 다른_요청이_진행_락을_쥐고_있으면_409이고_질문이_생기지_않는다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요."))
                .andDo(document("system-design-interview-proceed-locked",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message")
                                        .description("같은 회원의 다른 요청(기존 면접 답변 포함)을 처리하는 중일 때의 오류 문구")
                        )
                ));
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(readQuestions(systemDesignInterview)).isEmpty()
        );
    }

    @Test
    void 없는_면접은_404() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId() + 999L)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, "not-a-number")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        assertAll(
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 다른_회원의_면접은_403이고_단계와_질문이_그대로다() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(owner,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(otherMember);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 면접만 볼 수 있어요."));
        assertAll(
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(readQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(otherMember), String.class)).isEmpty()
        );
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);

        // when & then
        mockMvc.perform(post(COMPLETE_REQUIREMENTS_URL, systemDesignInterview.getInterview().getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
        assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS);
    }

    // 면접 조회 응답의 모든 필드. 비어 있을 수 있는 배열의 원소 필드와 null이 오는 필드는 optional이다.
    private static ResponseFieldsSnippet interviewResponseFields() {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("interview_id").description("시스템 설계 면접 ID"),
                fieldWithPath("interview_type").description("면접 유형. 항상 SYSTEM_DESIGN"),
                fieldWithPath("interview_state").description("면접 상태 (IN_PROGRESS, FINISHED)"),
                fieldWithPath("difficulty").description("문제 난이도. 지금은 BASIC뿐"),
                fieldWithPath("topic").description("문제"),
                fieldWithPath("topic.title").description("문제 제목"),
                fieldWithPath("topic.description").description("문제 설명"),
                fieldWithPath("stage").description("진행 단계 (REQUIREMENTS, HIGH_LEVEL, DEEP_DIVE). 면접이 끝난 뒤에도 DEEP_DIVE"),
                fieldWithPath("requirement_questions").description("지금까지 한 요구사항 역질문과 면접관 답. 물은 순서"),
                fieldWithPath("requirement_questions[].question").type(JsonFieldType.STRING).optional()
                        .description("역질문"),
                fieldWithPath("requirement_questions[].answer").type(JsonFieldType.STRING).optional()
                        .description("면접관 답"),
                fieldWithPath("max_requirement_question_count").description("할 수 있는 역질문 수 (5)"),
                fieldWithPath("cur_question_id").type(JsonFieldType.NUMBER).optional()
                        .description("답이 아직 없는 현재 질문의 순번 (1~6). 요구사항 단계와 면접이 끝난 뒤에는 null"),
                fieldWithPath("cur_question").type(JsonFieldType.STRING).optional()
                        .description("현재 질문 문장. cur_question_id가 null이면 null"),
                fieldWithPath("prev_questions_and_answers").description("답이 저장된 질문. 순번 순서"),
                fieldWithPath("prev_questions_and_answers[].question_id").type(JsonFieldType.NUMBER).optional()
                        .description("질문 순번 (1~6)"),
                fieldWithPath("prev_questions_and_answers[].stage").type(JsonFieldType.STRING).optional()
                        .description("그 질문의 단계 (HIGH_LEVEL, DEEP_DIVE)"),
                fieldWithPath("prev_questions_and_answers[].question").type(JsonFieldType.STRING).optional()
                        .description("질문 문장"),
                fieldWithPath("prev_questions_and_answers[].answer").type(JsonFieldType.STRING).optional()
                        .description("저장된 답변"),
                fieldWithPath("prev_questions_and_answers[].diagram").type(JsonFieldType.OBJECT).optional()
                        .description("그 답변과 함께 낸 다이어그램")
        ));
        diagramFields("prev_questions_and_answers[].diagram.").forEach(field -> fields.add(field.optional()));
        fields.add(fieldWithPath("cur_question_count").description("답이 저장된 질문 수 (0~6). 역질문은 세지 않는다"));
        fields.add(fieldWithPath("max_question_count").description("설계 질문 수 (6)"));
        fields.add(fieldWithPath("diagram")
                .description("현재 다이어그램. 마지막으로 저장된 답변의 다이어그램이고, 답변 전에는 문제의 시작 다이어그램(없으면 빈 다이어그램)"));
        fields.addAll(diagramFields("diagram."));
        return responseFields(fields);
    }

    // 다이어그램 하위 필드. 블록·연결·속성 배열은 비어 있을 수 있어 원소 필드는 optional이다.
    private static List<FieldDescriptor> diagramFields(String prefix) {
        return List.of(
                fieldWithPath(prefix + "blocks").type(JsonFieldType.ARRAY).description("블록 목록 (30개까지)"),
                fieldWithPath(prefix + "blocks[].id").type(JsonFieldType.STRING).optional().description("블록 id"),
                fieldWithPath(prefix + "blocks[].kind").type(JsonFieldType.STRING).optional()
                        .description("블록 종류 (CLIENT, LOAD_BALANCER, API_SERVER, DB, CACHE, QUEUE, STORAGE, CDN, "
                                + "CUSTOM)"),
                fieldWithPath(prefix + "blocks[].label").type(JsonFieldType.STRING).optional().description("블록 이름"),
                fieldWithPath(prefix + "blocks[].props").type(JsonFieldType.ARRAY).optional()
                        .description("블록 속성 목록 (10개까지)"),
                fieldWithPath(prefix + "blocks[].props[].key").type(JsonFieldType.STRING).optional()
                        .description("속성 이름"),
                fieldWithPath(prefix + "blocks[].props[].value").type(JsonFieldType.STRING).optional()
                        .description("속성 값"),
                fieldWithPath(prefix + "blocks[].position").type(JsonFieldType.OBJECT).optional()
                        .description("화면 위치"),
                fieldWithPath(prefix + "blocks[].position.x").type(JsonFieldType.NUMBER).optional()
                        .description("x 좌표"),
                fieldWithPath(prefix + "blocks[].position.y").type(JsonFieldType.NUMBER).optional()
                        .description("y 좌표"),
                fieldWithPath(prefix + "connections").type(JsonFieldType.ARRAY).description("연결 목록 (60개까지)"),
                fieldWithPath(prefix + "connections[].id").type(JsonFieldType.STRING).optional().description("연결 id"),
                fieldWithPath(prefix + "connections[].source").type(JsonFieldType.STRING).optional()
                        .description("출발 블록 id"),
                fieldWithPath(prefix + "connections[].target").type(JsonFieldType.STRING).optional()
                        .description("도착 블록 id"),
                fieldWithPath(prefix + "connections[].label").type(JsonFieldType.STRING).optional()
                        .description("연결 이름. 없으면 키가 없다")
        );
    }

    // currentDiagram이 null이면 문제의 시작 다이어그램(없으면 빈 다이어그램)으로 둔다.
    private SystemDesignInterview saveSystemDesignInterview(Member member, SystemDesignProblem problem,
                                                            SystemDesignStage stage,
                                                            SystemDesignDiagram currentDiagram) {
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign().member(member).build());
        return systemDesignInterviewRepository.save(SystemDesignInterviewFixtureBuilder.builder()
                .interview(interview)
                .problem(problem)
                .stage(stage)
                .currentDiagram(currentDiagram)
                .build());
    }

    private SystemDesignQuestion saveQuestion(SystemDesignInterview systemDesignInterview, int questionOrder,
                                              String content) {
        SystemDesignProblemQuestion problemQuestion = problemQuestionRepository.findByProblemAndQuestionOrder(
                systemDesignInterview.getProblem(), questionOrder).orElseThrow();
        return questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .problemQuestion(problemQuestion)
                .content(content)
                .build());
    }

    private void saveRequirementQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .build());
    }

    private String readBaseQuestion(SystemDesignProblem problem, int questionOrder) {
        return problemQuestionRepository.findByProblemAndQuestionOrder(problem, questionOrder).orElseThrow()
                .getContent();
    }

    private SystemDesignStage readStage(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterviewRepository.findById(systemDesignInterview.getId()).orElseThrow().getStage();
    }

    private List<SystemDesignQuestion> readQuestions(SystemDesignInterview systemDesignInterview) {
        return questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }

    private List<SystemDesignRequirementQuestion> readRequirementQuestions(
            SystemDesignInterview systemDesignInterview) {
        return requirementQuestionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }

    private static Object awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private static String lockKeyOf(Member member) {
        return InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
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

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // 응답의 다이어그램은 저장된 정규화 JSON과 같아야 한다.
    private JsonNode readJson(SystemDesignDiagram diagram) throws Exception {
        return objectMapper.readTree(diagram.toJson());
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }
}
