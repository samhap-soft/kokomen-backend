package com.samhap.kokomen.answer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.answer.AnswerLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

class AnswerControllerTest extends BaseControllerTest {

    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private AnswerLikeRepository answerLikeRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 답변_좋아요_요청() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).likeCount(0).build());

        // when & then
        mockMvc.perform(post("/api/v1/answers/{answer_id}/like", answer.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("answer-likeAnswer",
                        pathParameters(
                                parameterWithName("answer_id").description("좋아요를 요청할 답변 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));

        assertAll(
                () -> assertThat(answerLikeRepository.existsByMemberIdAndAnswerId(member.getId(), answer.getId())).isTrue(),
                () -> assertThat(answerRepository.findById(answer.getId()).get().getLikeCount()).isEqualTo(1)
        );
    }

    @Test
    void 답변에_메모_생성() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).likeCount(0).build());

        String requestBody = """
                {
                    "visibility": "PUBLIC",
                    "content": "메모 내용입니다."
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/answers/{answer_id}/memo", answer.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andDo(document("answer-createMemo",
                        pathParameters(
                                parameterWithName("answer_id").description("메모를 생성할 답변 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("visibility").description("공개 범위 (PUBLIC, PRIVATE, FRIENDS)"),
                                fieldWithPath("content").description("메모 내용")
                        ),
                        responseFields(
                                fieldWithPath("answer_memo_id").description("생성된 메모 ID")
                        )
                ));
    }

    @Test
    void 답변_좋아요_취소_요청() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).likeCount(1).build());
        answerLikeRepository.save(AnswerLikeFixtureBuilder.builder().answer(answer).member(member).build());

        // when & then
        mockMvc.perform(delete("/api/v1/answers/{answer_id}/like", answer.getId())
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("answer-unlikeAnswer",
                        pathParameters(
                                parameterWithName("answer_id").description("좋아요를 요청할 답변 ID")
                        ),
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        )
                ));

        assertAll(
                () -> assertThat(answerLikeRepository.existsByMemberIdAndAnswerId(member.getId(), answer.getId())).isFalse(),
                () -> assertThat(answerRepository.findById(answer.getId()).get().getLikeCount()).isEqualTo(0)
        );
    }
}
