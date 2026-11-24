package com.samhap.kokomen.interview.controller;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static com.samhap.kokomen.answer.domain.AnswerRank.C;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
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
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
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
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerCompletedResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignLlmBulkhead;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignProceedStateStore;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.payload.RequestFieldsSnippet;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.web.servlet.MvcResult;

class SystemDesignInterviewControllerTest extends BaseControllerTest {

    private static final String INTERVIEW_URL = "/api/v1/interviews/system-design/{interviewId}";
    private static final String COMPLETE_REQUIREMENTS_URL =
            "/api/v1/interviews/system-design/{interviewId}/requirements/complete";
    private static final String REQUIREMENT_QUESTIONS_URL =
            "/api/v1/interviews/system-design/{interviewId}/requirement-questions";
    private static final String SUBMIT_ANSWER_URL =
            "/api/v1/interviews/system-design/{interviewId}/questions/{questionId}/answers";
    private static final String ANSWER_RESULT_URL =
            "/api/v1/interviews/system-design/{interviewId}/questions/{questionId}";
    private static final String REPORT_URL = "/api/v1/interviews/system-design/{interviewId}/report";
    private static final String DEFAULT_REQUIREMENT_ANSWER = "등록되지 않은 내용이에요. 가정하고 진행하세요.";
    private static final String REQUIREMENT_QUESTION_JSON = """
            {
              "question": "하루에 단축 URL이 몇 개나 만들어지나요?"
            }
            """;
    // 블록 3개(속성 있는 블록 포함)와 연결 2개(이름 없는 연결 포함)를 그린 답변
    private static final String SUBMIT_ANSWER_JSON = """
            {
              "answer": "단축 키는 DB 자동 증가 값을 Base62로 바꿔 만들어요. 값이 겹치지 않으니 키 충돌이 없어요.",
              "diagram": {
                "blocks": [
                  {"id": "client", "kind": "CLIENT", "label": "Client", "props": [], "position": {"x": 0, "y": 80}},
                  {"id": "api", "kind": "API_SERVER", "label": "API 서버", "props": [{"key": "개수", "value": "3"}],
                   "position": {"x": 260, "y": 80}},
                  {"id": "db", "kind": "DB", "label": "MySQL", "props": [], "position": {"x": 520, "y": 80}}
                ],
                "connections": [
                  {"id": "c1", "source": "client", "target": "api", "label": "HTTPS"},
                  {"id": "c2", "source": "api", "target": "db"}
                ]
              }
            }
            """;

    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignInterviewTestSupport interviewTestSupport;
    @Autowired
    private SystemDesignAnswerService answerService;
    @Autowired
    private SystemDesignProceedStateStore stateStore;
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
    // 문제와 system_design_interview의 id, 질문·역질문의 PK와 순번이 우연히 같아진다. 다른 회원의 면접 기록(출제하지 않는 문제 2개 중
    // 하나로 한 시스템 설계 면접 1개와 그 숨은 요구사항·역질문·질문 1개씩, 일반 면접 1개)을 먼저 두어 id를 서로 어긋나게 한다.
    // 그래야 다른 테이블의 PK를 찾거나 내보내는 실수(역질문 남은 개수를 PK로 셈하기 등)가 테스트에서 드러난다.
    // 문제는 INACTIVE라 시작 API가 고르지 않는다.
    @BeforeEach
    void setUpOtherMemberInterviews() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
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
                .andExpect(jsonPath("$.cur_answer_proceed_state").doesNotExist())
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
                .andExpect(jsonPath("$.cur_answer_proceed_state").value("NONE"))
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
                                        .description("question이 없거나 null이거나, 공백뿐이거나, 앞뒤 공백을 지운 뒤 200자를 넘을 때의 "
                                                + "오류 문구")
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

    // 요청 본문이 없거나 JSON 형식이 틀리면 본문을 읽는 단계에서 기존 공용 400이 나간다. 경로 값·소유자·단계 검사보다 먼저다.
    @Test
    void 역질문은_본문이_없으면_JSON_형식_400() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL, null);
        MockHttpSession session = loginSession(member);

        // when & then
        for (Object interviewId : List.of(systemDesignInterview.getInterview().getId(), "not-a-number")) {
            mockMvc.perform(post(REQUIREMENT_QUESTIONS_URL, interviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("잘못된 요청 형식입니다. JSON 형식을 확인해주세요."));
        }
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
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
    void 역질문은_없는_면접이면_404() throws Exception {
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
    void 역질문은_다른_회원의_면접이면_403() throws Exception {
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

    @Test
    void 설계_답변을_제출한다() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any())).willReturn(new SystemDesignAnswerEvaluation(B,
                "단축 키를 만드는 방식과 충돌 처리가 분명해요.", "방금 설명한 키 생성기가 내려가면 어떻게 되나요?"));
        MockHttpSession session = loginSession(member);

        // when
        mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 2)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isAccepted())
                .andExpect(content().string(""))
                .andDo(document("system-design-interview-submit-answer",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID"),
                                parameterWithName("questionId").description("답하는 질문의 순번 (1~6). 지금 질문이어야 한다")
                        ),
                        submitAnswerRequestFields()
                ));

        // then
        awaitLockReleased(member);
        assertAll(
                () -> assertThat(answerService.findAnswerResult(interviewId, member.getId(), 2))
                        .isEqualTo(new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED,
                                InterviewState.IN_PROGRESS, SystemDesignStage.HIGH_LEVEL, B, 3,
                                "방금 설명한 키 생성기가 내려가면 어떻게 되나요?")),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(5)
        );
    }

    @Test
    void 요구사항_단계에서_답하면_409() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, systemDesignInterview.getInterview().getId(), 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("요구사항 정리를 먼저 끝내 주세요."))
                .andDo(document("system-design-interview-submit-answer-requirements-stage",
                        responseFields(
                                fieldWithPath("message").description("요구사항 단계에서 답변을 냈을 때의 오류 문구")
                        )
                ));
        verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any());
    }

    // 질문 ID는 면접 안 순번이다. 숫자가 아니거나 지난 질문이거나 질문 PK를 보내면 지금 질문이 아니다.
    @Test
    void 현재_질문이_아니면_409() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        Long currentQuestionPk = questionRepository.findBySystemDesignInterviewAndQuestionOrder(
                systemDesignInterview, 2).orElseThrow().getId();
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("지금 질문이 아니에요."))
                .andDo(document("system-design-interview-submit-answer-not-current-question",
                        responseFields(
                                fieldWithPath("message")
                                        .description("지금 질문이 아닌 순번(지난 질문, 숫자가 아닌 값 포함)에 답했을 때의 오류 문구")
                        )
                ));
        for (String questionId : List.of("not-a-number", "99999999999", "-1", "3", String.valueOf(currentQuestionPk))) {
            mockMvc.perform(post("/api/v1/interviews/system-design/" + interviewId + "/questions/" + questionId
                            + "/answers")
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SUBMIT_ANSWER_JSON)
                    )
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("지금 질문이 아니에요."));
        }
        verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any());
    }

    @Test
    void 끝난_면접에_답하면_409() throws Exception {
        // given
        Member member = saveMemberWithTokens(0, 0);
        SystemDesignInterview finishedInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, B, B, B, B, B));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, finishedInterview.getInterview().getId(), 6)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 끝난 면접이에요."))
                .andDo(document("system-design-interview-submit-answer-finished",
                        responseFields(
                                fieldWithPath("message")
                                        .description("끝난 면접에 답변을 냈을 때의 오류 문구. 토큰이 없어도 이 문구가 먼저다")
                        )
                ));
    }

    // 다이어그램 규칙마다 처음 어긴 규칙 하나의 문구로 400이다. 답변·토큰·락보다 먼저 막혀 채점하지 않는다.
    @Test
    void 다이어그램_규칙을_어기면_규칙마다_정해진_문구로_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(member);
        Map<String, Object> diagramWithUnknownKind = diagram(List.of(block("client", "CLIENT"), block("api", "SERVER")),
                List.of());
        Map<String, Object> blockWithoutPosition = block("client", "CLIENT");
        blockWithoutPosition.remove("position");
        Map<String, Object> diagramWithoutPosition = diagram(List.of(blockWithoutPosition), List.of());
        Map<String, Object> answerWithoutDiagram = new LinkedHashMap<>();
        answerWithoutDiagram.put("answer", "API 서버 앞에 캐시를 둬요.");

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(diagramWithUnknownKind))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("블록 종류를 알 수 없어요."))
                .andDo(document("system-design-interview-submit-answer-invalid-diagram",
                        responseFields(
                                fieldWithPath("message").description("처음 어긴 다이어그램 규칙의 오류 문구")
                        )
                ));
        Map<String, String> expectedMessageByBody = new LinkedHashMap<>();
        expectedMessageByBody.put(objectMapper.writeValueAsString(answerWithoutDiagram), "다이어그램 형식이 맞지 않아요.");
        expectedMessageByBody.put(answerJson(diagram(Collections.singletonList(null), List.of())),
                "다이어그램 형식이 맞지 않아요.");
        expectedMessageByBody.put(answerJson(diagram(blocks(31), List.of())), "블록은 30개까지 그릴 수 있어요.");
        expectedMessageByBody.put(answerJson(diagram(blocks(2), connections(61))), "연결은 60개까지 그릴 수 있어요.");
        expectedMessageByBody.put(answerJson(diagram(List.of(block("client", "CLIENT")),
                List.of(connection("c1", "client", "cache")))), "연결이 없는 블록을 가리켜요.");
        expectedMessageByBody.put(answerJson(diagram(List.of(block("client", "CLIENT")),
                List.of(connection("c1", "client", "client")))), "블록을 자기 자신과 연결할 수 없어요.");
        expectedMessageByBody.put(answerJson(diagramWithoutPosition), "블록 위치가 올바르지 않아요.");
        for (Map.Entry<String, String> entry : expectedMessageByBody.entrySet()) {
            mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 1)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(entry.getKey())
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(entry.getValue()));
        }
        assertAll(
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any()),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(6)
        );
    }

    @Test
    void 다이어그램_JSON_타입이_틀리면_전역_400_문구다() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, systemDesignInterview.getInterview().getId(), 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer": "API 서버 앞에 캐시를 둬요.", "diagram": {"blocks": {}, "connections": []}}
                                """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 요청 형식입니다. JSON 형식을 확인해주세요."));
    }

    // 에디터가 붙이는 값처럼 모르는 필드가 섞여 와도 받아서 버리고, 다이어그램 타입의 필드만 저장·응답한다.
    @Test
    void 모르는_필드는_버리고_정규화한_다이어그램을_저장한다() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        Long interviewId = systemDesignInterview.getInterview().getId();
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any())).willReturn(
                new SystemDesignAnswerEvaluation(B, "조회 경로에 캐시를 둔 근거가 분명해요.", null));
        MockHttpSession session = loginSession(member);

        // when
        mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": "API 서버 앞에 캐시를 둬요.",
                                  "draft_id": "local-17",
                                  "diagram": {
                                    "zoom": 1.5,
                                    "blocks": [
                                      {"id": "client", "kind": "CLIENT", "label": "Client", "selected": true,
                                       "props": [{"key": "개수", "value": "1", "color": "red"}],
                                       "position": {"x": 0, "y": 80, "z": 3}, "style": {"width": 120}},
                                      {"id": "api", "kind": "API_SERVER", "label": "API 서버", "props": [],
                                       "position": {"x": 260.5, "y": -40}}
                                    ],
                                    "connections": [
                                      {"id": "c1", "source": "client", "target": "api", "animated": true}
                                    ]
                                  }
                                }
                                """)
                )
                .andExpect(status().isAccepted());
        awaitLockReleased(member);

        // then
        MockHttpSession refreshedSession = loginSession(member);
        MvcResult result = mockMvc.perform(get(INTERVIEW_URL, interviewId)
                        .header("Cookie", "JSESSIONID=" + refreshedSession.getId())
                        .session(refreshedSession)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cur_question_count").value(1))
                .andReturn();
        assertThat(readBody(result).get("diagram")).isEqualTo(objectMapper.readTree("""
                {"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[{"key":"개수","value":"1"}],\
                "position":{"x":0.0,"y":80.0}},{"id":"api","kind":"API_SERVER","label":"API 서버","props":[],\
                "position":{"x":260.5,"y":-40.0}}],"connections":[{"id":"c1","source":"client","target":"api"}]}
                """));
    }

    @Test
    void 답변이_비었거나_3000자를_넘으면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(" \n ", minimalDiagram()))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("답변은 1~3,000자로 적어 주세요."))
                .andDo(document("system-design-interview-submit-answer-invalid-length",
                        responseFields(
                                fieldWithPath("message")
                                        .description("답변이 없거나, 앞뒤 공백을 지운 뒤 비었거나, 3,000자를 넘을 때의 오류 문구")
                        )
                ));
        for (String answer : Arrays.asList(null, "", "가".repeat(3_001))) {
            mockMvc.perform(post(SUBMIT_ANSWER_URL, interviewId, 1)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerJson(answer, minimalDiagram()))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("답변은 1~3,000자로 적어 주세요."));
        }
        verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any());
    }

    @Test
    void 답변_제출은_토큰이_없으면_400() throws Exception {
        // given
        Member member = saveMemberWithTokens(0, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, systemDesignInterview.getInterview().getId(), 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("토큰 갯수가 부족합니다."))
                .andDo(document("system-design-interview-submit-answer-not-enough-tokens",
                        responseFields(
                                fieldWithPath("message").description("FREE와 PAID 토큰을 합쳐 1개도 없을 때의 오류 문구")
                        )
                ));
        assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty();
    }

    @Test
    void 답변_제출은_다른_요청이_진행_락을_쥐고_있으면_409() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, systemDesignInterview.getInterview().getId(), 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요."))
                .andDo(document("system-design-interview-submit-answer-locked",
                        responseFields(
                                fieldWithPath("message")
                                        .description("채점 중인 답변을 다시 냈거나 같은 회원의 다른 진행 요청을 처리하고 있을 때의 오류 문구")
                        )
                ));
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any())
        );
    }

    @Test
    void 답변_제출은_없는_면접이면_404() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview categoryInterview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        for (String interviewId : List.of(String.valueOf(categoryInterview.getId() + 999L), "not-a-number",
                String.valueOf(categoryInterview.getId()))) {
            mockMvc.perform(post("/api/v1/interviews/system-design/" + interviewId + "/questions/1/answers")
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SUBMIT_ANSWER_JSON)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        }
    }

    @Test
    void 답변_제출은_다른_회원의_면접이면_403() throws Exception {
        // given
        Member owner = saveMemberWithTokens(6, 0);
        Member otherMember = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(owner,
                List.of());
        MockHttpSession session = loginSession(otherMember);

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, systemDesignInterview.getInterview().getId(), 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 면접만 볼 수 있어요."));
        verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any());
    }

    @Test
    void 답변_제출은_로그인하지_않으면_401() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());

        // when & then
        mockMvc.perform(post(SUBMIT_ANSWER_URL, systemDesignInterview.getInterview().getId(), 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_ANSWER_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
    }

    // 채점 중에 새로고침하면 진행 락이 잡혀 있어도 면접 조회는 락을 기다리지 않고 200으로 같은 현재 질문과 채점 중 상태를 준다.
    @Test
    void 채점_중에_새로고침해도_면접_조회는_200이고_LLM_PENDING이다() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        redisService.acquireLockWithValue(lockKeyOf(member), "grading-worker", Duration.ofMinutes(5));
        stateStore.markPending(interviewId, 2);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(INTERVIEW_URL, interviewId)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cur_question_id").value(2))
                .andExpect(jsonPath("$.cur_question_count").value(1))
                .andExpect(jsonPath("$.cur_answer_proceed_state").value("LLM_PENDING"));
        assertThat(redisService.get(lockKeyOf(member), String.class)).contains("grading-worker");
    }

    @Test
    void 채점_중이면_LLM_PENDING() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        stateStore.markPending(interviewId, 2);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(ANSWER_RESULT_URL, interviewId, 2)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceed_state").value("LLM_PENDING"))
                .andDo(document("system-design-interview-answer-result-pending",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID"),
                                parameterWithName("questionId").description("답변을 낸 질문의 순번 (1~6)")
                        ),
                        responseFields(
                                fieldWithPath("proceed_state").description("LLM_PENDING: 채점 중. 1초 뒤 다시 조회한다")
                        )
                ));
    }

    @Test
    void 채점이_실패했으면_LLM_FAILED() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        stateStore.markFailed(interviewId, 2);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(ANSWER_RESULT_URL, interviewId, 2)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceed_state").value("LLM_FAILED"))
                .andDo(document("system-design-interview-answer-result-failed",
                        responseFields(
                                fieldWithPath("proceed_state").description(
                                        "LLM_FAILED: 채점에 실패했거나 제출 기록이 없다(5분이 지난 경우 포함). 답변이 저장되지 않았고 "
                                                + "토큰도 차감되지 않았으니 같은 질문에 다시 내면 된다")
                        )
                ));
    }

    @Test
    void 채점이_끝나면_결과와_다음_질문을_준다() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, A, C));
        Long interviewId = systemDesignInterview.getInterview().getId();
        // 답변이 저장돼 있으면 Redis 값과 상관없이 완료다.
        stateStore.markFailed(interviewId, 3);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(ANSWER_RESULT_URL, interviewId, 3)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceed_state").value("COMPLETED"))
                .andExpect(jsonPath("$.interview_state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.stage").value("DEEP_DIVE"))
                .andExpect(jsonPath("$.cur_answer_rank").value("C"))
                .andExpect(jsonPath("$.next_question_id").value(4))
                .andExpect(jsonPath("$.next_question").value("다듬은 4번 질문이에요."))
                .andDo(document("system-design-interview-answer-result-completed",
                        responseFields(answerResultCompletedFields())
                ));
        // 지난 질문을 다시 조회해도 그 답변을 처리한 결과(그때의 다음 질문과 단계)를 준다.
        mockMvc.perform(get(ANSWER_RESULT_URL, interviewId, 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("HIGH_LEVEL"))
                .andExpect(jsonPath("$.cur_answer_rank").value("B"))
                .andExpect(jsonPath("$.next_question_id").value(2))
                .andExpect(jsonPath("$.next_question").value("다듬은 2번 질문이에요."));
    }

    @Test
    void 마지막_답변이면_FINISHED와_null_다음_질문을_준다() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, A, C, B, B, A));
        MockHttpSession session = loginSession(member);

        // when
        MvcResult result = mockMvc.perform(get(ANSWER_RESULT_URL, systemDesignInterview.getInterview().getId(), 6)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceed_state").value("COMPLETED"))
                .andExpect(jsonPath("$.interview_state").value("FINISHED"))
                .andExpect(jsonPath("$.stage").value("DEEP_DIVE"))
                .andExpect(jsonPath("$.cur_answer_rank").value("A"))
                .andExpect(jsonPath("$.next_question_id").value(nullValue()))
                .andExpect(jsonPath("$.next_question").value(nullValue()))
                .andDo(document("system-design-interview-answer-result-finished",
                        responseFields(answerResultCompletedFields())
                ))
                .andReturn();

        // then
        JsonNode body = readBody(result);
        assertAll(
                () -> assertThat(body.has("next_question_id")).isTrue(),
                () -> assertThat(body.get("next_question_id").isNull()).isTrue(),
                () -> assertThat(body.has("next_question")).isTrue(),
                () -> assertThat(body.get("next_question").isNull()).isTrue()
        );
    }

    @Test
    void 없는_질문은_404() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(ANSWER_RESULT_URL, interviewId, 3)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("질문을 찾을 수 없어요."))
                .andDo(document("system-design-interview-answer-result-not-found",
                        responseFields(
                                fieldWithPath("message")
                                        .description("그 순번의 질문이 아직 없을 때(숫자가 아닌 값 포함)의 오류 문구")
                        )
                ));
        for (String questionId : List.of("not-a-number", "0", "7")) {
            mockMvc.perform(get("/api/v1/interviews/system-design/" + interviewId + "/questions/" + questionId)
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("질문을 찾을 수 없어요."));
        }
    }

    // 질문 ID가 숫자가 아니면 경로 값 오류라 소유자·면접 검사보다 먼저 없는 질문(404)으로 답한다.
    @Test
    void 숫자가_아닌_질문_ID로_폴링하면_소유자_검사보다_먼저_404() throws Exception {
        // given
        Member owner = saveMemberWithTokens(6, 0);
        Member otherMember = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(owner,
                List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        MockHttpSession session = loginSession(otherMember);

        // when & then
        for (Long requestedInterviewId : List.of(interviewId, interviewId + 999L)) {
            mockMvc.perform(get("/api/v1/interviews/system-design/" + requestedInterviewId + "/questions/abc")
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("질문을 찾을 수 없어요."));
        }
    }

    @Test
    void 폴링은_로그인하지_않으면_401() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));

        // when & then
        mockMvc.perform(get(ANSWER_RESULT_URL, systemDesignInterview.getInterview().getId(), 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
    }

    @Test
    void 다른_회원의_면접을_폴링하면_403() throws Exception {
        // given
        Member owner = saveMemberWithTokens(6, 0);
        Member otherMember = saveMemberWithTokens(6, 0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(owner,
                List.of(B));
        MockHttpSession session = loginSession(otherMember);

        // when & then
        mockMvc.perform(get(ANSWER_RESULT_URL, systemDesignInterview.getInterview().getId(), 1)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 면접만 볼 수 있어요."));
    }

    @Test
    void 없는_면접이나_기존_면접_id로_폴링하면_404() throws Exception {
        // given
        Member member = saveMemberWithTokens(6, 0);
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview categoryInterview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        for (String interviewId : List.of(String.valueOf(categoryInterview.getId() + 999L), "not-a-number",
                String.valueOf(categoryInterview.getId()))) {
            mockMvc.perform(get("/api/v1/interviews/system-design/" + interviewId + "/questions/1")
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        }
    }

    @Test
    void 끝난_면접의_리포트를_조회한다() throws Exception {
        // given
        interviewTestSupport.saveOtherMemberReportRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().score(150).build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of(
                List.of("DAILY_CREATION_VOLUME"),
                List.of("READ_WRITE_RATIO", "RETENTION_PERIOD"),
                List.of()));
        assertThat(interviewTestSupport.readReportIds(systemDesignInterview)).doesNotHaveDuplicates();
        Interview interview = systemDesignInterview.getInterview();
        MockHttpSession session = loginSession(member);

        // when
        MvcResult result = mockMvc.perform(get(REPORT_URL, interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview_id").value(interview.getId()))
                .andExpect(jsonPath("$.topic.title").value(SystemDesignProblemTestSupport.TITLE))
                .andExpect(jsonPath("$.topic.description").value(SystemDesignProblemTestSupport.DESCRIPTION))
                .andExpect(jsonPath("$.difficulty").value("BASIC"))
                .andExpect(jsonPath("$.total_score").value(65))
                .andExpect(jsonPath("$.total_feedback").value(interview.getTotalFeedback()))
                .andExpect(jsonPath("$.difficulty_comment").value(SystemDesignProblemTestSupport.DIFFICULTY_COMMENT))
                .andExpect(jsonPath("$.evaluation_points", hasSize(5)))
                .andExpect(jsonPath("$.evaluation_points[0].is_met").value(true))
                .andExpect(jsonPath("$.evaluation_points[*].is_met", contains(true, true, false, true, false)))
                .andExpect(jsonPath("$.evaluation_points[0].met").doesNotExist())
                .andExpect(jsonPath("$.missed_requirements", contains("단축 키 길이", "클릭 통계")))
                .andExpect(jsonPath("$.requirement_questions", hasSize(3)))
                .andExpect(jsonPath("$.requirement_questions[0].question").value("하루 생성량에 대해 알려 주세요."))
                .andExpect(jsonPath("$.requirement_questions[0].answer")
                        .value("새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요."))
                .andExpect(jsonPath("$.feedbacks[0].question_id").value(1))
                .andExpect(jsonPath("$.feedbacks[*].question_id", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.feedbacks[*].stage", contains("HIGH_LEVEL", "HIGH_LEVEL", "HIGH_LEVEL",
                        "DEEP_DIVE", "DEEP_DIVE", "DEEP_DIVE")))
                .andExpect(jsonPath("$.feedbacks[*].answer_rank", contains("B", "B", "C", "A", "B", "C")))
                .andExpect(jsonPath("$.feedbacks[0].answer_feedback").isString())
                .andExpect(jsonPath("$.final_diagram.connections[1].label").doesNotExist())
                .andExpect(jsonPath("$.user_cur_score").value(150))
                .andExpect(jsonPath("$.user_prev_score").value(100))
                .andExpect(jsonPath("$.user_cur_rank").doesNotExist())
                .andExpect(jsonPath("$.user_prev_rank").doesNotExist())
                .andDo(document("system-design-interview-find-report",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        reportResponseFields()
                ))
                .andReturn();

        // then
        JsonNode body = readBody(result);
        assertAll(
                () -> assertThat(body.get("evaluation_points").get(0).get("is_met").isBoolean()).isTrue(),
                () -> assertThat(body.get("feedbacks").get(0).get("diagram"))
                        .isEqualTo(readJson(SystemDesignDiagramFixture.urlShortenerStarter())),
                () -> assertThat(body.get("feedbacks").get(5).get("diagram"))
                        .isEqualTo(readJson(SystemDesignDiagramFixture.withCache())),
                () -> assertThat(body.get("final_diagram")).isEqualTo(readJson(SystemDesignDiagramFixture.withCache()))
        );
    }

    @Test
    void 끝나지_않은_면접은_409() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview designing = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B, A, C));
        SystemDesignInterview requirements = saveSystemDesignInterview(member, problemTestSupport.saveProblem(),
                SystemDesignStage.REQUIREMENTS, null);
        MockHttpSession session = loginSession(member);

        // when & then
        mockMvc.perform(get(REPORT_URL, designing.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("면접이 끝나야 리포트를 볼 수 있어요."))
                .andDo(document("system-design-interview-find-report-not-finished",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interviewId").description("시스템 설계 면접 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("면접이 아직 끝나지 않았을 때의 오류 문구")
                        )
                ));
        mockMvc.perform(get(REPORT_URL, requirements.getInterview().getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("면접이 끝나야 리포트를 볼 수 있어요."));
    }

    @Test
    void 없는_면접과_기존_면접_id는_404() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview finishedCategoryInterview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED)
                .totalScore(30)
                .build());
        MockHttpSession session = loginSession(member);

        // when & then
        for (String interviewId : List.of(String.valueOf(finishedCategoryInterview.getId() + 999L), "not-a-number",
                String.valueOf(finishedCategoryInterview.getId()))) {
            mockMvc.perform(get("/api/v1/interviews/system-design/" + interviewId + "/report")
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("면접을 찾을 수 없어요."));
        }
    }

    // 소유자를 끝났는지보다 먼저 본다. 다른 회원의 면접이면 진행 중이어도 409가 아니라 403이다.
    @Test
    void 리포트는_다른_회원의_면접이면_403() throws Exception {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview finished = interviewTestSupport.saveFinishedInterview(owner, List.of());
        SystemDesignInterview inProgress = interviewTestSupport.saveInterviewWithAnswers(owner, List.of(B));
        MockHttpSession session = loginSession(otherMember);

        // when & then
        for (SystemDesignInterview systemDesignInterview : List.of(finished, inProgress)) {
            mockMvc.perform(get(REPORT_URL, systemDesignInterview.getInterview().getId())
                            .header("Cookie", "JSESSIONID=" + session.getId())
                            .session(session)
                    )
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("본인의 면접만 볼 수 있어요."));
        }
    }

    @Test
    void 리포트는_로그인하지_않으면_401() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveFinishedInterview(member, List.of());

        // when & then
        mockMvc.perform(get(REPORT_URL, systemDesignInterview.getInterview().getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"));
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
        fields.add(fieldWithPath("cur_answer_proceed_state").type(JsonFieldType.STRING).optional()
                .description("현재 질문에 낸 답변의 채점 상태 (NONE: 내지 않았거나 기록이 사라짐, LLM_PENDING: 채점 중, "
                        + "LLM_FAILED: 채점 실패라 같은 질문에 다시 낼 수 있음). 현재 질문이 없는 요구사항 단계와 면접이 끝난 뒤에는 키가 없다"));
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

    // 리포트 응답의 모든 필드. 비어 있을 수 있는 역질문 배열의 원소 필드는 optional이다. 순위 필드는 없다.
    private static ResponseFieldsSnippet reportResponseFields() {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("interview_id").description("시스템 설계 면접 ID"),
                fieldWithPath("topic").description("문제"),
                fieldWithPath("topic.title").description("문제 제목"),
                fieldWithPath("topic.description").description("문제 설명"),
                fieldWithPath("difficulty").description("문제 난이도. 지금은 BASIC뿐"),
                fieldWithPath("total_score").description("리포트 점수 (0~100). 평가 포인트 충족률과 답변 6개의 등급 평균"
                        + "(A 1.0, B 0.75, C 0.5, D 0.25, F 0)을 반씩 반영해 반올림한다. 회원 점수(랭킹)에 더하는 점수와 다르다"),
                fieldWithPath("total_feedback").description("마지막 답변 채점이 낸 종합 피드백(강점·보완점·학습 방향을 이은 문장)"),
                fieldWithPath("difficulty_comment").description("문제의 난이도 기준 코멘트"),
                fieldWithPath("evaluation_points").description("평가 포인트. 문제에 정한 순서"),
                fieldWithPath("evaluation_points[].description").description("평가 포인트"),
                fieldWithPath("evaluation_points[].is_met").description("마지막 답변 채점이 판정한 충족 여부"),
                fieldWithPath("missed_requirements").type(JsonFieldType.ARRAY)
                        .description("요구사항 역질문으로 확인하지 않은 숨은 요구사항의 이름. 문제에 정한 순서이고, 역질문을 하지 않았으면 "
                                + "모두 들어간다. 모두 확인했으면 빈 배열"),
                fieldWithPath("requirement_questions").description("요구사항 역질문과 면접관 답. 물은 순서. 역질문을 하지 않았으면 빈 배열"),
                fieldWithPath("requirement_questions[].question").type(JsonFieldType.STRING).optional()
                        .description("역질문"),
                fieldWithPath("requirement_questions[].answer").type(JsonFieldType.STRING).optional()
                        .description("면접관 답"),
                fieldWithPath("feedbacks").description("설계 질문별 답변과 피드백. 질문 순번 순서"),
                fieldWithPath("feedbacks[].question_id").description("질문 순번 (1~6)"),
                fieldWithPath("feedbacks[].stage").description("그 질문의 단계 (HIGH_LEVEL, DEEP_DIVE)"),
                fieldWithPath("feedbacks[].question").description("질문 문장"),
                fieldWithPath("feedbacks[].answer").description("답변"),
                fieldWithPath("feedbacks[].diagram").description("그 답변과 함께 낸 다이어그램")
        ));
        fields.addAll(diagramFields("feedbacks[].diagram."));
        fields.add(fieldWithPath("feedbacks[].answer_rank").description("답변 등급 (A, B, C, D, F)"));
        fields.add(fieldWithPath("feedbacks[].answer_feedback").description("답변 피드백"));
        fields.add(fieldWithPath("final_diagram").description("마지막 답변의 다이어그램"));
        fields.addAll(diagramFields("final_diagram."));
        fields.add(fieldWithPath("user_cur_score").description("면접자의 지금 회원 점수(랭킹 점수). 다른 면접을 마치면 바뀐다"));
        fields.add(fieldWithPath("user_prev_score")
                .description("user_cur_score에서 이번 면접의 등급 점수 합(A 20, B 10, C 0, D -10, F -20)을 뺀 값"));
        return responseFields(fields);
    }

    // 답변 제출 요청의 모든 필드. 배열이 비어 있을 수 있어 원소 필드는 optional이다.
    private static RequestFieldsSnippet submitAnswerRequestFields() {
        return requestFields(
                fieldWithPath("answer").description("답변. 앞뒤 공백을 지운 뒤 1~3,000자이고 지운 값을 저장한다"),
                fieldWithPath("diagram").description("답변과 함께 내는 현재 다이어그램 전체. 모르는 필드는 버린다"),
                fieldWithPath("diagram.blocks").type(JsonFieldType.ARRAY).description("블록 목록 (30개까지)"),
                fieldWithPath("diagram.blocks[].id").type(JsonFieldType.STRING).optional()
                        .description("블록 id. 1~64자이고 블록끼리 겹치지 않는다"),
                fieldWithPath("diagram.blocks[].kind").type(JsonFieldType.STRING).optional()
                        .description("블록 종류 (CLIENT, LOAD_BALANCER, API_SERVER, DB, CACHE, QUEUE, STORAGE, CDN, "
                                + "CUSTOM). 대소문자를 구분한다"),
                fieldWithPath("diagram.blocks[].label").type(JsonFieldType.STRING).optional()
                        .description("블록 이름 (1~50자)"),
                fieldWithPath("diagram.blocks[].props").type(JsonFieldType.ARRAY).optional()
                        .description("블록 속성 목록 (10개까지). 생략하지 않고 없으면 빈 배열로 보낸다"),
                fieldWithPath("diagram.blocks[].props[].key").type(JsonFieldType.STRING).optional()
                        .description("속성 이름 (30자까지)"),
                fieldWithPath("diagram.blocks[].props[].value").type(JsonFieldType.STRING).optional()
                        .description("속성 값 (100자까지)"),
                fieldWithPath("diagram.blocks[].position").type(JsonFieldType.OBJECT).optional()
                        .description("화면 위치. 채점에는 쓰지 않는다"),
                fieldWithPath("diagram.blocks[].position.x").type(JsonFieldType.NUMBER).optional()
                        .description("x 좌표 (유한한 숫자)"),
                fieldWithPath("diagram.blocks[].position.y").type(JsonFieldType.NUMBER).optional()
                        .description("y 좌표 (유한한 숫자)"),
                fieldWithPath("diagram.connections").type(JsonFieldType.ARRAY).description("연결 목록 (60개까지)"),
                fieldWithPath("diagram.connections[].id").type(JsonFieldType.STRING).optional()
                        .description("연결 id. 1~64자이고 연결끼리 겹치지 않는다(블록 id와는 겹쳐도 된다)"),
                fieldWithPath("diagram.connections[].source").type(JsonFieldType.STRING).optional()
                        .description("출발 블록 id. 같은 다이어그램의 블록이어야 한다"),
                fieldWithPath("diagram.connections[].target").type(JsonFieldType.STRING).optional()
                        .description("도착 블록 id. 같은 다이어그램의 블록이고 출발 블록과 달라야 한다"),
                fieldWithPath("diagram.connections[].label").type(JsonFieldType.STRING).optional()
                        .description("연결 이름 (30자까지). 없어도 된다")
        );
    }

    // 채점이 끝난 답변 결과의 모든 필드. 마지막 답변이면 next_question_id와 next_question이 null이다(키는 있다).
    private static List<FieldDescriptor> answerResultCompletedFields() {
        return List.of(
                fieldWithPath("proceed_state").description("COMPLETED: 답변이 저장됐다. 토큰 1개가 차감됐다"),
                fieldWithPath("interview_state")
                        .description("이 답변을 처리한 뒤의 면접 상태. 6번째 답변이면 FINISHED, 아니면 IN_PROGRESS"),
                fieldWithPath("stage").description("다음 질문의 단계 (HIGH_LEVEL, DEEP_DIVE). 6번째 답변이면 DEEP_DIVE"),
                fieldWithPath("cur_answer_rank").description("이 답변의 등급 (A, B, C, D, F)"),
                fieldWithPath("next_question_id").type(JsonFieldType.NUMBER).optional()
                        .description("다음 질문의 순번. 6번째 답변이면 null"),
                fieldWithPath("next_question").type(JsonFieldType.STRING).optional()
                        .description("다음 질문 문장. 6번째 답변이면 null")
        );
    }

    private String answerJson(Map<String, Object> diagram) throws Exception {
        return answerJson("API 서버 앞에 캐시를 둬요.", diagram);
    }

    private String answerJson(String answer, Map<String, Object> diagram) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        body.put("diagram", diagram);
        return objectMapper.writeValueAsString(body);
    }

    private static Map<String, Object> minimalDiagram() {
        return diagram(List.of(block("client", "CLIENT")), List.of());
    }

    private static Map<String, Object> diagram(List<?> blocks, List<?> connections) {
        Map<String, Object> diagram = new LinkedHashMap<>();
        diagram.put("blocks", blocks);
        diagram.put("connections", connections);
        return diagram;
    }

    private static Map<String, Object> block(String id, String kind) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", id);
        block.put("kind", kind);
        block.put("label", "블록 " + id);
        block.put("props", List.of());
        block.put("position", Map.of("x", 0, "y", 0));
        return block;
    }

    private static List<Map<String, Object>> blocks(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> block("block-" + index, "API_SERVER"))
                .toList();
    }

    private static Map<String, Object> connection(String id, String source, String target) {
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("id", id);
        connection.put("source", source);
        connection.put("target", target);
        return connection;
    }

    // blocks(2)의 두 블록을 오가는 연결
    private static List<Map<String, Object>> connections(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> connection("connection-" + index, "block-1", "block-2"))
                .toList();
    }

    private void awaitLockReleased(Member member) {
        await().atMost(Duration.ofSeconds(5))
                .until(() -> redisService.get(lockKeyOf(member), String.class).isEmpty());
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
