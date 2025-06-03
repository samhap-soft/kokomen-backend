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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.Choice;
import com.samhap.kokomen.interview.external.dto.response.GptFunctionCall;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.Message;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import com.samhap.kokomen.member.domain.Member;

class InterviewControllerTest extends BaseControllerTest {

    @Test
    void 인터뷰를_생성하면_루트_질문을_바탕으로_질문도_생성된다() throws Exception {
        // given
        Member member = memberRepository.save(new Member("NAK"));
        String rootQuestionContent = "부팅 과정에 대해 설명해주세요.";
        RootQuestion rootQuestion = rootQuestionRepository.save(new RootQuestion(rootQuestionContent));

        String requestJson = """
                {
                  "categories": ["OPERATING_SYSTEM"]
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
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("interview-startInterview",
                        requestFields(
                                fieldWithPath("categories").description("인터뷰 카테고리 목록")
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
        Member member = memberRepository.save(new Member("NAK"));
        Interview interview = interviewRepository.save(new Interview(member));
        RootQuestion rootQuestion = rootQuestionRepository.save(new RootQuestion("자바의 특징은 무엇인가요?"));
        Question question1 = questionRepository.save(new Question(interview, rootQuestion, rootQuestion.getContent()));
        answerRepository.save(new Answer(question1, "자바는 객체지향 프로그래밍 언어입니다.", AnswerRank.C, "부족합니다."));

        Question question2 = questionRepository.save(new Question(interview, rootQuestion, "객체지향의 특징을 설명해주세요."));
        String nextQuestion = "절차지향 프로그래밍이 뭔가요?";

        String requestJson = """
                {
                  "answer": "절차지향 프로그래밍과 반대되는 개념입니다."
                }
                """;

        String responseJson = """
                {
                  "question_id": 3,
                  "question": "%s",
                  "is_root": false
                }
                """.formatted(nextQuestion);

        String arguments = """
                {
                  "rank": "D",
                  "feedback": "똑바로 대답하세요.",
                  "next_question": "%s"
                }
                """.formatted(nextQuestion);
        when(gptClient.requestToGpt(any()))
                .thenReturn(new GptResponse(
                        List.of(new Choice(
                                new Message(
                                        List.of(new ToolCall(new GptFunctionCall(
                                                "generate_feedback",
                                                arguments
                                        )))
                                )
                        ))
                ));

        // when & then
        mockMvc.perform(post(
                        "/api/v1/interviews/{interview_id}/questions/{question_id}/answers", interview.getId(),
                        question2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
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
                                fieldWithPath("question_id").description("다음 질문 id"),
                                fieldWithPath("question").description("다음 질문 내용"),
                                fieldWithPath("is_root").description("루트 질문 여부")
                        )
                ));
    }

    @Test
    void 인터뷰에_대한_최종_결과를_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(new Member("NAK"));
        Interview interview = interviewRepository.save(new Interview(member));
        RootQuestion rootQuestion = rootQuestionRepository.save(new RootQuestion("자바의 특징은 무엇인가요?"));
        Question question1 = questionRepository.save(new Question(interview, rootQuestion, rootQuestion.getContent()));
        Answer answer1 = answerRepository.save(new Answer(question1, "자바는 객체지향 프로그래밍 언어입니다.", AnswerRank.C, "부족합니다."));
        Question question2 = questionRepository.save(new Question(interview, rootQuestion, "객체지향의 특징을 설명해주세요."));
        Answer answer2 = answerRepository.save(new Answer(question2, "객체가 각자 책임집니다.", AnswerRank.C, "부족합니다."));
        Question question3 = questionRepository.save(new Question(interview, rootQuestion, "객체는 무엇인가요?"));
        Answer answer3 = answerRepository.save(new Answer(question3, "클래스의 인스턴스 입니다.", AnswerRank.C, "부족합니다."));
        interview.evaluate("제대로 좀 공부 해라.", 10);
        interviewRepository.save(interview);
        member.updateScore(10);
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
                			"answer_rank": "C",
                			"answer_feedback": "부족합니다."
                		},
                		{
                			"question_id": 3,
                			"answer_id": 3,
                			"question": "객체는 무엇인가요?",
                			"answer": "클래스의 인스턴스 입니다.",
                			"answer_rank": "C",
                			"answer_feedback": "부족합니다."
                		}
                	],
                	"total_score": 10,
                	"user_cur_score": 10,
                	"user_prev_score": 0,
                	"user_prev_rank": "BRONZE",
                	"user_cur_rank": "BRONZE"
                }
                """;

        // when & then
        mockMvc.perform(get("/api/v1/interviews/{interview_id}/result", interview.getId()))
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
