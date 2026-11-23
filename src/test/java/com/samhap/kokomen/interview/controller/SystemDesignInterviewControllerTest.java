package com.samhap.kokomen.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
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
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.web.servlet.MvcResult;

class SystemDesignInterviewControllerTest extends BaseControllerTest {

    private static final String INTERVIEW_URL = "/api/v1/interviews/system-design/{interviewId}";
    private static final String COMPLETE_REQUIREMENTS_URL =
            "/api/v1/interviews/system-design/{interviewId}/requirements/complete";

    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
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
        List<SystemDesignQuestion> questions = questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(
                systemDesignInterview);
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
                () -> assertThat(questionRepository.count()).isZero(),
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
                () -> assertThat(questionRepository.count()).isZero()
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
                () -> assertThat(questionRepository.count()).isZero(),
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

    // E2 응답의 모든 필드. 비어 있을 수 있는 배열의 원소 필드와 null이 오는 필드는 optional이다.
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
