package com.samhap.kokomen.interview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.GptResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class InterviewControllerTest extends BaseControllerTest {

    @Autowired
    protected AnswerRepository answerRepository;
    @Autowired
    protected InterviewRepository interviewRepository;
    @Autowired
    protected QuestionRepository questionRepository;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected RootQuestionRepository rootQuestionRepository;

    @Test
    void 인터뷰를_생성하면_루트_질문을_바탕으로_질문도_생성된다() throws Exception {
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
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-startInterview",
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
    void 인터뷰_답변을_전달하면_인터뷰에_대한_평가를_받고_다음_질문을_응답한다() throws Exception {
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

        // when & then
        mockMvc.perform(post(
                        "/api/v1/interviews/{interview_id}/questions/{question_id}/answers", interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-proceedInterview",
                        pathParameters(
                                parameterWithName("interview_id").description("인터뷰 ID"),
                                parameterWithName("question_id").description("질문 ID")
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
    void 인터뷰에_대한_최종_결과를_조회한다() throws Exception {
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
                	"user_prev_score": 100,
                	"user_prev_rank": "BRONZE",
                	"user_cur_rank": "BRONZE"
                }
                """;

        // when & then
        mockMvc.perform(get("/api/v1/interviews/{interview_id}/result", interview.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-findTotalFeedbacks",
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
                                fieldWithPath("total_feedback").description("인터뷰 총 피드백"),
                                fieldWithPath("total_score").description("인터뷰 총 점수"),
                                fieldWithPath("user_cur_score").description("현재 사용자 점수"),
                                fieldWithPath("user_prev_score").description("이전 사용자 점수"),
                                fieldWithPath("user_prev_rank").description("이전 사용자 랭크"),
                                fieldWithPath("user_cur_rank").description("현재 사용자 랭크")
                        )
                ));
    }
}
