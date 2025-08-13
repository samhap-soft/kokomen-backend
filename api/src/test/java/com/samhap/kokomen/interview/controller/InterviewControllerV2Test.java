package com.samhap.kokomen.interview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.TypecastResponse;
import com.samhap.kokomen.interview.external.dto.response.TypecastResult;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.InterviewFacadeService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class InterviewControllerV2Test extends BaseControllerTest {

    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private RedisService redisService;

    @Test
    void 인터뷰_진행_V2() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());

        String requestJson = """
                {
                  "answer": "절차지향 프로그래밍과 반대되는 개념입니다.",
                  "mode": "TEXT"
                }
                """;

        // when & then
        mockMvc.perform(post(
                        "/api/v2/interviews/{interview_id}/questions/{cur_question_id}/answers", interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isNoContent())
                .andDo(document("interview-proceedInterview-V2",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("cur_question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("answer").description("사용자가 작성한 답변"),
                                fieldWithPath("mode").description("인터뷰 모드(TEXT, VOICE)")
                        )
                ));
    }

    @Test
    void 인터뷰_폴링_응답_PENDING() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(), question2.getId());
        redisService.setValue(interviewProceedStateKey, LlmProceedState.PENDING.name(), Duration.ofSeconds(10));

        String responseJson = """
                {
                    "llm_proceed_state": "PENDING"
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v2/interviews/{interview_id}/questions/{cur_question_id}?mode=TEXT",
                        interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-getPolling-PENDING-V2",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("cur_question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("llm_proceed_state").description("LLM 진행 상태")
                        )
                ));
    }

    @Test
    void 인터뷰_폴링_응답_FAILED() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(), question2.getId());
        redisService.setValue(interviewProceedStateKey, LlmProceedState.FAILED.name(), Duration.ofSeconds(10));

        String responseJson = """
                {
                    "llm_proceed_state": "FAILED"
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v2/interviews/{interview_id}/questions/{cur_question_id}?mode=TEXT",
                        interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-getPolling-FAILED-V2",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("cur_question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("llm_proceed_state").description("LLM 진행 상태")
                        )
                ));
    }

    @Test
    void 종료된_인터뷰_폴링_응답_COMPLETED_FINISHED() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(), question2.getId());
        redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        String responseJson = """
                {
                    "llm_proceed_state": "COMPLETED",
                    "interview_state": "FINISHED"
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v2/interviews/{interview_id}/questions/{cur_question_id}?mode=TEXT",
                        interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-getPolling-COMPLETED-FINISHED-V2",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("cur_question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("llm_proceed_state").description("LLM 진행 상태"),
                                fieldWithPath("interview_state").description("인터뷰 상태")
                        )
                ));
    }

    @Test
    void 종료된_인터뷰_폴링_응답_COMPLETED_IN_PROGRESS_텍스트모드() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(AnswerRank.C).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("오상훈의 위 부피를 계산해주세요.").build());

        System.out.println(questionRepository.findTop2ByInterviewIdOrderByIdDesc(interview.getId()));

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(), question2.getId());
        redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        String responseJson = """
                {
                    "llm_proceed_state": "COMPLETED",
                    "interview_state": "IN_PROGRESS",
                    "cur_answer_rank": "C",
                    "next_question_id": 3,
                    "next_question": "오상훈의 위 부피를 계산해주세요."
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v2/interviews/{interview_id}/questions/{cur_question_id}?mode=TEXT",
                        interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-getPolling-COMPLETED-IN-PROGRESS-V2-text-mode",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("cur_question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("llm_proceed_state").description("LLM 진행 상태"),
                                fieldWithPath("interview_state").description("인터뷰 상태"),
                                fieldWithPath("cur_answer_rank").description("현재 답변 순위"),
                                fieldWithPath("next_question_id").description("다음 질문 ID"),
                                fieldWithPath("next_question").description("다음 질문 내용")
                        )
                ));
    }

    @Test
    void 종료된_인터뷰_폴링_응답_COMPLETED_IN_PROGRESS_음성모드() throws Exception {
        // given
        when(typecastClient.request(any())).thenReturn(new TypecastResponse(new TypecastResult("mock-url")));
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewMode(InterviewMode.VOICE).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(AnswerRank.C).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("오상훈의 위 부피를 계산해주세요.").build());

        System.out.println(questionRepository.findTop2ByInterviewIdOrderByIdDesc(interview.getId()));

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(), question2.getId());
        redisService.setValue(interviewProceedStateKey, LlmProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        String responseJson = """
                {
                    "llm_proceed_state": "COMPLETED",
                    "interview_state": "IN_PROGRESS",
                    "cur_answer_rank": "C",
                    "next_question_id": 3,
                    "next_question_voice_url": "mock-url"
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v2/interviews/{interview_id}/questions/{cur_question_id}?mode=VOICE",
                        interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-getPolling-COMPLETED-IN-PROGRESS-V2-voice-mode",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("cur_question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("llm_proceed_state").description("LLM 진행 상태"),
                                fieldWithPath("interview_state").description("인터뷰 상태"),
                                fieldWithPath("cur_answer_rank").description("현재 답변 순위"),
                                fieldWithPath("next_question_id").description("다음 질문 ID"),
                                fieldWithPath("next_question_voice_url").description("다음 질문 음성 URL (루트 질문인 경우 CDN URL이 가지만, 루트 질문이 아닌 경우 polling 필요할 수 있음)")
                        )
                ));
    }
}
