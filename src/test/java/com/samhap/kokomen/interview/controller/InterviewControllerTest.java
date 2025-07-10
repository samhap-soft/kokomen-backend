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
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.AnswerLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.GptResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.repository.InterviewLikeRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class InterviewControllerTest extends BaseControllerTest {

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
    private InterviewLikeRepository interviewLikeRepository;
    @Autowired
    private AnswerLikeRepository answerLikeRepository;

    @Test
    void 인터뷰_시작() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        String rootQuestionContent = "부팅 과정에 대해 설명해주세요.";
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content(rootQuestionContent).build());

        String requestJson = """
                {
                  "category": "OPERATING_SYSTEM",
                  "max_question_count": 3
                }
                """;

        String responseJson = """
                {
                	"interview_id": 1,
                	"question_id": 1,
                	"root_question": "%s"
                }
                """.formatted(rootQuestionContent);

        // when & then
        mockMvc.perform(post(
                        "/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-startInterview",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("category").description("인터뷰 카테고리"),
                                fieldWithPath("max_question_count").description("최대 질문 개수")
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID"),
                                fieldWithPath("question_id").description("생성된 질문 ID"),
                                fieldWithPath("root_question").description("루트 질문 내용")
                        )
                ));
    }

    @Test
    void 인터뷰_진행() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        String nextQuestion = "절차지향 프로그래밍이 뭔가요?";
        AnswerRank curAnswerRank = AnswerRank.D;

        String requestJson = """
                {
                  "answer": "절차지향 프로그래밍과 반대되는 개념입니다."
                }
                """;

        String responseJson = """
                {
                  "cur_answer_rank": "%s",
                  "next_question_id": 3,
                  "next_question": "%s"
                }
                """.formatted(curAnswerRank, nextQuestion);

        GptResponse gptResponse = GptResponseFixtureBuilder.builder()
                .answerRank(curAnswerRank)
                .nextQuestion(nextQuestion)
                .buildProceed();
        when(gptClient.requestToGpt(any())).thenReturn(gptResponse);

        BedrockResponse bedrockResponse = BedrockResponseFixtureBuilder.builder()
                .answerRank(curAnswerRank)
                .nextQuestion(nextQuestion)
                .buildProceed();
        when(bedrockClient.requestToBedrock(any())).thenReturn(bedrockResponse);

        // when & then
        mockMvc.perform(post(
                        "/api/v1/interviews/{interview_id}/questions/{question_id}/answers", interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-proceedInterview",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("question_id").description("질문 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("answer").description("사용자가 작성한 답변")
                        ),
                        responseFields(
                                fieldWithPath("cur_answer_rank").description("현재 답변 랭크"),
                                fieldWithPath("next_question_id").description("다음 질문 id"),
                                fieldWithPath("next_question").description("다음 질문 내용")
                        )
                ));
    }

    @Test
    void 인터뷰_좋아요_요청() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());

        // when & then
        mockMvc.perform(post("/api/v1/interviews/{interview_id}/like", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("interview-likeInterview",
                        pathParameters(
                                parameterWithName("interview_id").description("좋아요를 요청할 인터뷰 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }

    @Test
    void 인터뷰_현황_체크_IN_PROGRESS() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().content(rootQuestion.getContent()).interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("현재 새로운 질문").build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String responseJson = """
                {
                	"interview_state": "IN_PROGRESS",
                	"cur_question_id": %d,
                	"cur_question": "%s",
                	"cur_question_count": %d,
                	"max_question_count": %d,
                	"prev_questions_and_answers": [
                		{
                			"question_id": %d,
                			"question": "%s",
                			"answer_id": %d,
                			"answer": "%s"
                		}
                	]
                }
                """.formatted(
                question2.getId(), question2.getContent(), 2, interview.getMaxQuestionCount(),
                question1.getId(), question1.getContent(), answer1.getId(), answer1.getContent());

        // when & then
        mockMvc.perform(get(
                        "/api/v1/interviews/{interview_id}/check", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-checkInterview-inProgress",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID")
                        ),
                        responseFields(
                                fieldWithPath("interview_state").description("인터뷰 상태"),
                                fieldWithPath("cur_question_id").description("현재 질문 ID (면접이 IN_PROGRESS 인 경우에만)"),
                                fieldWithPath("cur_question").description("현재 질문 내용 (면접이 IN_PROGRESS 인 경우에만)"),
                                fieldWithPath("cur_question_count").description("현재까지 받은 질문 개수"),
                                fieldWithPath("max_question_count").description("최대 질문 개수"),
                                fieldWithPath("prev_questions_and_answers").description("이전 질문과 답변 목록"),
                                fieldWithPath("prev_questions_and_answers[].question_id").description("이전 질문 ID"),
                                fieldWithPath("prev_questions_and_answers[].question").description("이전 질문 내용"),
                                fieldWithPath("prev_questions_and_answers[].answer_id").description("이전 답변 ID"),
                                fieldWithPath("prev_questions_and_answers[].answer").description("이전 답변 내용")
                        )
                ));
    }

    @Test
    void 인터뷰_현황_체크_FINISHED() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("첫번째 질문").build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member).rootQuestion(rootQuestion).maxQuestionCount(3).interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().content(rootQuestion.getContent()).interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).content("첫번째 답변").build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("두번째 질문").build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).content("두번째 답변").build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("세번째 질문").build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).content("세번째 답변").build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String responseJson = """
                {
                	"interview_state": "FINISHED",
                	"cur_question_count": %d,
                	"max_question_count": %d,
                	"prev_questions_and_answers": [
                		{
                			"question_id": %d,
                			"question": "%s",
                			"answer_id": %d,
                			"answer": "%s"
                		},
                		{
                			"question_id": %d,
                			"question": "%s",
                			"answer_id": %d,
                			"answer": "%s"
                		},
                		{
                			"question_id": %d,
                			"question": "%s",
                			"answer_id": %d,
                			"answer": "%s"
                		}
                	]
                }
                """.formatted(
                interview.getMaxQuestionCount(), interview.getMaxQuestionCount(),
                question1.getId(), question1.getContent(), answer1.getId(), answer1.getContent(),
                question2.getId(), question2.getContent(), answer2.getId(), answer2.getContent(),
                question3.getId(), question3.getContent(), answer3.getId(), answer3.getContent());

        // when & then
        mockMvc.perform(get(
                        "/api/v1/interviews/{interview_id}/check", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-checkInterview-finished",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID")
                        ),
                        responseFields(
                                fieldWithPath("interview_state").description("인터뷰 상태"),
                                fieldWithPath("cur_question_count").description("현재까지 받은 질문 개수"),
                                fieldWithPath("max_question_count").description("최대 질문 개수"),
                                fieldWithPath("prev_questions_and_answers").description("이전 질문과 답변 목록"),
                                fieldWithPath("prev_questions_and_answers[].question_id").description("이전 질문 ID"),
                                fieldWithPath("prev_questions_and_answers[].question").description("이전 질문 내용"),
                                fieldWithPath("prev_questions_and_answers[].answer_id").description("이전 답변 ID"),
                                fieldWithPath("prev_questions_and_answers[].answer").description("이전 답변 내용")
                        )
                ));
    }

    @Test
    void 자신의_인터뷰_목록_조회() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion1 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("최단 경로 알고리즘에 대해 설명해주세요.").build());
        RootQuestion rootQuestion2 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("알고리즘의 시간복잡도는?").build());

        Interview inProgressInterview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion1).build());
        questionRepository.save(QuestionFixtureBuilder.builder().interview(inProgressInterview).content(rootQuestion1.getContent()).build());

        Interview finishedInterview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member).rootQuestion(rootQuestion2).maxQuestionCount(3).totalScore(20).interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview).content(rootQuestion2.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());
        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(finishedInterview).member(member).build());
        interviewRepository.increaseLikeCount(finishedInterview.getId());

        String responseJson = """
                [
                	{
                		"interview_id": %d,
                		"interview_state": "%s",
                		"interview_category": "%s",
                		"root_question": "%s",
                		"max_question_count": %d,
                		"cur_answer_count": %d,
                		"score": %s,
                		"interview_like_count": 1,
                		"interview_already_liked": true
                	},
                	{
                		"interview_id": %d,
                		"interview_state": "%s",
                		"interview_category": "%s",
                		"root_question": "%s",
                		"max_question_count": %d,
                		"cur_answer_count": %d
                	}
                ]
                """.formatted(
                finishedInterview.getId(), finishedInterview.getInterviewState(), finishedInterview.getRootQuestion().getCategory(),
                finishedInterview.getRootQuestion().getContent(), finishedInterview.getMaxQuestionCount(), 3, finishedInterview.getTotalScore(),
                inProgressInterview.getId(), inProgressInterview.getInterviewState(), inProgressInterview.getRootQuestion().getCategory(),
                inProgressInterview.getRootQuestion().getContent(), inProgressInterview.getMaxQuestionCount(), 0, inProgressInterview.getLikeCount()
        );

        // when & then
        mockMvc.perform(get("/api/v1/interviews/me")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,desc")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andExpect(jsonPath("$[0].created_at").exists())
                .andExpect(jsonPath("$[1].created_at").exists())
                .andDo(document("interview-findMyInterviews",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("state").description("면접 상태 쿼리 파라미터 " + Arrays.asList(InterviewState.values()) + " (선택사항)").optional(),
                                parameterWithName("page").description("페이지 번호 (기본값: 0)"),
                                parameterWithName("size").description("페이지 크기 (기본값: 10)"),
                                parameterWithName("sort").description("정렬 기준 (기본값: id,desc)")
                        ),
                        responseFields(
                                fieldWithPath("[].interview_id").description("면접 ID"),
                                fieldWithPath("[].interview_state").description("면접 상태 " + Arrays.asList(InterviewState.values())),
                                fieldWithPath("[].interview_category").description("면접 카테고리"),
                                fieldWithPath("[].created_at").description("생성 시간"),
                                fieldWithPath("[].root_question").description("루트 질문"),
                                fieldWithPath("[].max_question_count").description("최대 질문 개수"),
                                fieldWithPath("[].cur_answer_count").description("현재 답변 개수"),
                                fieldWithPath("[].score").description("점수 (면접이 FINISHED 인 경우에만)").optional(),
                                fieldWithPath("[].interview_like_count").description("면접 좋아요 수 (면접이 FINISHED 인 경우에만)").optional(),
                                fieldWithPath("[].interview_already_liked").description("면접에 이미 좋아요를 눌렀는지 여부 (면접이 FINISHED 인 경우에만)").optional()
                        )
                ));
    }

    @Test
    void 다른_사용자의_완료된_인터뷰_목록_조회_로그인_버전() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member readerMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", readerMember.getId());

        RootQuestion rootQuestion1 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("최단 경로 알고리즘에 대해 설명해주세요.").build());
        RootQuestion rootQuestion2 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("알고리즘의 시간복잡도는?").build());
        RootQuestion rootQuestion3 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("알고리즘의 공간복잡도는?").build());

        Interview inProgressInterview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion1).build());
        questionRepository.save(QuestionFixtureBuilder.builder().interview(inProgressInterview).content(rootQuestion1.getContent()).build());

        Interview finishedInterview1 = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member).rootQuestion(rootQuestion2).maxQuestionCount(3).totalScore(20).interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(finishedInterview1).content(rootQuestion2.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview1).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview1).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());

        Interview finishedInterview2 = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member).rootQuestion(rootQuestion3).maxQuestionCount(3).totalScore(20).interviewState(InterviewState.FINISHED).build());
        Question question4 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(finishedInterview2).content(rootQuestion3.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question4).build());
        Question question5 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview2).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question5).build());
        Question question6 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview2).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question6).build());
        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(finishedInterview2).member(readerMember).build());
        interviewRepository.increaseLikeCount(finishedInterview2.getId());

        String responseJson = """
                [
                	{
                		"interview_id": %d,
                		"interview_state": "%s",
                		"interview_category": "%s",
                		"root_question": "%s",
                		"max_question_count": %d,
                		"score": %s,
                		"interview_like_count": 1,
                		"interview_already_liked": true
                	},
                	{
                		"interview_id": %d,
                		"interview_state": "%s",
                		"interview_category": "%s",
                		"root_question": "%s",
                		"max_question_count": %d,
                		"score": %s,
                		"interview_like_count": 0,
                		"interview_already_liked": false
                	}
                ]
                """.formatted(
                finishedInterview2.getId(), finishedInterview2.getInterviewState(), finishedInterview2.getRootQuestion().getCategory(),
                finishedInterview2.getRootQuestion().getContent(), finishedInterview2.getMaxQuestionCount(), finishedInterview2.getTotalScore(),
                finishedInterview1.getId(), finishedInterview1.getInterviewState(), finishedInterview1.getRootQuestion().getCategory(),
                finishedInterview1.getRootQuestion().getContent(), finishedInterview1.getMaxQuestionCount(), finishedInterview1.getTotalScore()
        );

        // when & then
        mockMvc.perform(get("/api/v1/interviews")
                        .param("member_id", String.valueOf(member.getId()))
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,desc")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andExpect(jsonPath("$[0].created_at").exists())
                .andExpect(jsonPath("$[1].created_at").exists())
                .andDo(document("interview-findOtherMemberInterviews-authenticated",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("member_id").description("인터뷰이 멤버 id").optional(),
                                parameterWithName("page").description("페이지 번호 (기본값: 0)"),
                                parameterWithName("size").description("페이지 크기 (기본값: 10)"),
                                parameterWithName("sort").description("정렬 기준 (기본값: id,desc)")
                        ),
                        responseFields(
                                fieldWithPath("[].interview_id").description("면접 ID"),
                                fieldWithPath("[].interview_state").description("면접 상태(항상 FINISHED)"),
                                fieldWithPath("[].interview_category").description("면접 카테고리"),
                                fieldWithPath("[].created_at").description("생성 시간"),
                                fieldWithPath("[].root_question").description("루트 질문"),
                                fieldWithPath("[].max_question_count").description("최대 질문 개수"),
                                fieldWithPath("[].score").description("점수"),
                                fieldWithPath("[].interview_like_count").description("면접 좋아요 수"),
                                fieldWithPath("[].interview_already_liked").description("이미 좋아요를 눌렀는지 여부")
                        )
                ));
    }

    @Test
    void 다른_사용자의_완료된_인터뷰_목록_조회_비회원_버전() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        RootQuestion rootQuestion1 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("최단 경로 알고리즘에 대해 설명해주세요.").build());
        RootQuestion rootQuestion2 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("알고리즘의 시간복잡도는?").build());
        RootQuestion rootQuestion3 = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("알고리즘의 공간복잡도는?").build());

        Interview inProgressInterview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion1).build());
        questionRepository.save(QuestionFixtureBuilder.builder().interview(inProgressInterview).content(rootQuestion1.getContent()).build());

        Interview finishedInterview1 = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member).rootQuestion(rootQuestion2).maxQuestionCount(3).totalScore(20).interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(finishedInterview1).content(rootQuestion2.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview1).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview1).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());

        Interview finishedInterview2 = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member).rootQuestion(rootQuestion3).maxQuestionCount(3).totalScore(20).interviewState(InterviewState.FINISHED).build());
        Question question4 = questionRepository.save(
                QuestionFixtureBuilder.builder().interview(finishedInterview2).content(rootQuestion3.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question4).build());
        Question question5 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview2).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question5).build());
        Question question6 = questionRepository.save(QuestionFixtureBuilder.builder().interview(finishedInterview2).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question6).build());

        String responseJson = """
                [
                	{
                		"interview_id": %d,
                		"interview_state": "%s",
                		"interview_category": "%s",
                		"root_question": "%s",
                		"max_question_count": %d,
                		"score": %s,
                		"interview_like_count": %d,
                		"interview_already_liked": false
                	},
                	{
                		"interview_id": %d,
                		"interview_state": "%s",
                		"interview_category": "%s",
                		"root_question": "%s",
                		"max_question_count": %d,
                		"score": %s,
                		"interview_like_count": %d,
                		"interview_already_liked": false
                	}
                ]
                """.formatted(
                finishedInterview2.getId(), finishedInterview2.getInterviewState(), finishedInterview2.getRootQuestion().getCategory(),
                finishedInterview2.getRootQuestion().getContent(), finishedInterview2.getMaxQuestionCount(), finishedInterview2.getTotalScore(),
                finishedInterview2.getLikeCount(),
                finishedInterview1.getId(), finishedInterview1.getInterviewState(), finishedInterview1.getRootQuestion().getCategory(),
                finishedInterview1.getRootQuestion().getContent(), finishedInterview1.getMaxQuestionCount(), finishedInterview1.getTotalScore(),
                finishedInterview1.getLikeCount()
        );

        // when & then
        mockMvc.perform(get("/api/v1/interviews")
                        .param("member_id", String.valueOf(member.getId()))
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andExpect(jsonPath("$[0].created_at").exists())
                .andExpect(jsonPath("$[1].created_at").exists())
                .andDo(document("interview-findOtherMemberInterviews-unauthenticated",
                        queryParameters(
                                parameterWithName("member_id").description("인터뷰이 멤버 id").optional(),
                                parameterWithName("page").description("페이지 번호 (기본값: 0)"),
                                parameterWithName("size").description("페이지 크기 (기본값: 10)"),
                                parameterWithName("sort").description("정렬 기준 (기본값: id,desc)")
                        ),
                        responseFields(
                                fieldWithPath("[].interview_id").description("면접 ID"),
                                fieldWithPath("[].interview_state").description("면접 상태(항상 FINISHED)"),
                                fieldWithPath("[].interview_category").description("면접 카테고리"),
                                fieldWithPath("[].created_at").description("생성 시간"),
                                fieldWithPath("[].root_question").description("루트 질문"),
                                fieldWithPath("[].max_question_count").description("최대 질문 개수"),
                                fieldWithPath("[].score").description("점수"),
                                fieldWithPath("[].interview_like_count").description("면접 좋아요 수"),
                                fieldWithPath("[].interview_already_liked").description("이미 좋아요를 눌렀는지 여부")
                        )
                ));
    }

    @Test
    void 자신의_인터뷰_결과_조회() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        member.addScore(100);
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        Answer answer1 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.").answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        Answer answer2 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D).feedback("부족합니다.").build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        Answer answer3 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F).feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);
        member.addScore(-30);
        memberRepository.save(member);

        String responseJson = """
                {
                	"feedbacks": [
                		{
                			"question_id": 1,
                			"answer_id": 1,
                			"question": "자바의 특징은 무엇인가요?",
                			"answer": "자바는 객체지향 프로그래밍 언어입니다.",
                			"answer_rank": "C",
                			"answer_feedback": "부족합니다."
                		},
                		{
                			"question_id": 2,
                			"answer_id": 2,
                			"question": "객체지향의 특징을 설명해주세요.",
                			"answer": "객체가 각자 책임집니다.",
                			"answer_rank": "D",
                			"answer_feedback": "부족합니다."
                		},
                		{
                			"question_id": 3,
                			"answer_id": 3,
                			"question": "객체는 무엇인가요?",
                			"answer": "클래스의 인스턴스 입니다.",
                			"answer_rank": "F",
                			"answer_feedback": "부족합니다."
                		}
                	],
                	"total_score": -30,
                	"user_cur_score": 70,
                	"user_prev_score": 100
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v1/interviews/{interview_id}/my-result", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-findMyResult",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("feedbacks").description("피드백 목록"),
                                fieldWithPath("feedbacks[].question_id").description("질문 ID"),
                                fieldWithPath("feedbacks[].answer_id").description("답변 ID"),
                                fieldWithPath("feedbacks[].question").description("질문 내용"),
                                fieldWithPath("feedbacks[].answer").description("답변 내용"),
                                fieldWithPath("feedbacks[].answer_rank").description("답변 등급"),
                                fieldWithPath("feedbacks[].answer_feedback").description("답변 피드백"),
                                fieldWithPath("total_feedback").description("인터뷰 총 피드백"),
                                fieldWithPath("total_score").description("인터뷰 총 점수"),
                                fieldWithPath("user_cur_score").description("현재 사용자 점수"),
                                fieldWithPath("user_prev_score").description("이전 사용자 점수")
                        )
                ));
    }

    @Test
    void 다른_사용자의_완료된_인터뷰_결과_조회_로그인_버전() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member readerMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", readerMember.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        Answer answer1 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.").answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        Answer answer2 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D).feedback("부족합니다.").build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        Answer answer3 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F).feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);
        interviewRepository.increaseLikeCount(interview.getId());
        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview).member(readerMember).build());
        answerLikeRepository.save(AnswerLikeFixtureBuilder.builder().answer(answer1).member(readerMember).build());
        answerRepository.incrementLikeCount(answer1.getId());

        String responseJson = """
                {
                	"feedbacks": [
                		{
                			"question_id": 1,
                			"answer_id": 1,
                			"question": "자바의 특징은 무엇인가요?",
                			"answer": "자바는 객체지향 프로그래밍 언어입니다.",
                			"answer_rank": "C",
                			"answer_feedback": "부족합니다.",
                			"answer_like_count": 1,
                			"answer_already_liked": true
                		},
                		{
                			"question_id": 2,
                			"answer_id": 2,
                			"question": "객체지향의 특징을 설명해주세요.",
                			"answer": "객체가 각자 책임집니다.",
                			"answer_rank": "D",
                			"answer_feedback": "부족합니다.",
                			"answer_like_count": 0,
                			"answer_already_liked": false
                		},
                		{
                			"question_id": 3,
                			"answer_id": 3,
                			"question": "객체는 무엇인가요?",
                			"answer": "클래스의 인스턴스 입니다.",
                			"answer_rank": "F",
                			"answer_feedback": "부족합니다.",
                			"answer_like_count": 0,
                			"answer_already_liked": false
                		}
                	],
                	"total_score": -30,
                	"total_feedback": "제대로 좀 공부 해라.",
                	"interview_like_count": 1,
                	"interview_already_liked": true
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v1/interviews/{interview_id}/result", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-findOtherMemberResult-authenticated",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID")
                        ),
                        responseFields(
                                fieldWithPath("feedbacks").description("피드백 목록"),
                                fieldWithPath("feedbacks[].question_id").description("질문 ID"),
                                fieldWithPath("feedbacks[].answer_id").description("답변 ID"),
                                fieldWithPath("feedbacks[].question").description("질문 내용"),
                                fieldWithPath("feedbacks[].answer").description("답변 내용"),
                                fieldWithPath("feedbacks[].answer_rank").description("답변 등급"),
                                fieldWithPath("feedbacks[].answer_feedback").description("답변 피드백"),
                                fieldWithPath("feedbacks[].answer_like_count").description("답변 좋아요 수"),
                                fieldWithPath("feedbacks[].answer_already_liked").description("이미 답변에 좋아요를 눌렀는지 여부"),
                                fieldWithPath("total_feedback").description("인터뷰 총 피드백"),
                                fieldWithPath("total_score").description("인터뷰 총 점수"),
                                fieldWithPath("interview_like_count").description("인터뷰 좋아요 수"),
                                fieldWithPath("interview_already_liked").description("이미 인터뷰에 좋아요를 눌렀는지 여부")
                        )
                ));
    }

    @Test
    void 다른_사용자의_완료된_인터뷰_결과_조회_비회원_버전() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().content("자바의 특징은 무엇인가요?").build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content(rootQuestion.getContent()).build());
        Answer answer1 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question1).content("자바는 객체지향 프로그래밍 언어입니다.").answerRank(AnswerRank.C).feedback("부족합니다.").build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("객체지향의 특징을 설명해주세요.").build());
        Answer answer2 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question2).content("객체가 각자 책임집니다.").answerRank(AnswerRank.D).feedback("부족합니다.").build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).content("객체는 무엇인가요?").build());
        Answer answer3 = answerRepository.save(
                AnswerFixtureBuilder.builder().question(question3).content("클래스의 인스턴스 입니다.").answerRank(AnswerRank.F).feedback("부족합니다.").build());
        interview.evaluate("제대로 좀 공부 해라.", -30);
        interviewRepository.save(interview);

        String responseJson = """
                {
                	"feedbacks": [
                		{
                			"question_id": 1,
                			"answer_id": 1,
                			"question": "자바의 특징은 무엇인가요?",
                			"answer": "자바는 객체지향 프로그래밍 언어입니다.",
                			"answer_rank": "C",
                			"answer_feedback": "부족합니다.",
                			"answer_like_count": 0,
                			"answer_already_liked": false
                		},
                		{
                			"question_id": 2,
                			"answer_id": 2,
                			"question": "객체지향의 특징을 설명해주세요.",
                			"answer": "객체가 각자 책임집니다.",
                			"answer_rank": "D",
                			"answer_feedback": "부족합니다.",
                			"answer_like_count": 0,
                			"answer_already_liked": false
                		},
                		{
                			"question_id": 3,
                			"answer_id": 3,
                			"question": "객체는 무엇인가요?",
                			"answer": "클래스의 인스턴스 입니다.",
                			"answer_rank": "F",
                			"answer_feedback": "부족합니다.",
                			"answer_like_count": 0,
                			"answer_already_liked": false
                		}
                	],
                	"total_score": -30,
                	"total_feedback": "제대로 좀 공부 해라.",
                	"interview_like_count": 0,
                	"interview_already_liked": false
                }
                """;

        // when & then
        mockMvc.perform(get(
                        "/api/v1/interviews/{interview_id}/result", interview.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-findOtherMemberResult-unauthenticated",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID")
                        ),
                        responseFields(
                                fieldWithPath("feedbacks").description("피드백 목록"),
                                fieldWithPath("feedbacks[].question_id").description("질문 ID"),
                                fieldWithPath("feedbacks[].answer_id").description("답변 ID"),
                                fieldWithPath("feedbacks[].question").description("질문 내용"),
                                fieldWithPath("feedbacks[].answer").description("답변 내용"),
                                fieldWithPath("feedbacks[].answer_rank").description("답변 등급"),
                                fieldWithPath("feedbacks[].answer_feedback").description("답변 피드백"),
                                fieldWithPath("feedbacks[].answer_like_count").description("답변 좋아요 수"),
                                fieldWithPath("feedbacks[].answer_already_liked").description("이미 답변에 좋아요를 눌렀는지 여부"),
                                fieldWithPath("total_feedback").description("인터뷰 총 피드백"),
                                fieldWithPath("total_score").description("인터뷰 총 점수"),
                                fieldWithPath("interview_like_count").description("인터뷰 좋아요 수"),
                                fieldWithPath("interview_already_liked").description("이미 인터뷰에 좋아요를 눌렀는지 여부")
                        )
                ));
    }

    @Test
    void 인터뷰_좋아요_취소_요청() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1L).build());
        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview).member(member).build());

        // when & then
        mockMvc.perform(delete("/api/v1/interviews/{interview_id}/like", interview.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("interview-unlikeInterview",
                        pathParameters(
                                parameterWithName("interview_id").description("좋아요 취소를 요청할 인터뷰 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));
    }
}
