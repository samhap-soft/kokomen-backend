package com.samhap.kokomen.interview.docs;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.DocsTest;
import com.samhap.kokomen.global.fixture.interview.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

public class InterviewDocsTest extends DocsTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;

    // TODO: 로그인 기능 붙이면, 회원 못 찾을 때 발생하는 예외도 문서화
    @MethodSource("provideStartInterviewExceptionCase")
    @ParameterizedTest
    void 인터뷰_시작_예외_문서화(int maxQuestionCount, int docsNo) throws Exception {
        // given
        memberRepository.save(MemberFixtureBuilder.builder().build());
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // when & then
        mockMvc.perform(post(
                        "/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InterviewRequest(Category.OPERATING_SYSTEM, maxQuestionCount)))
                )
                .andDo(document("interview-startInterview-exception" + docsNo));
    }

    private static Stream<Arguments> provideStartInterviewExceptionCase() {
        return Stream.of(
                Arguments.of(2, 1) // maxQuestionCount가 유효하지 않은 경우
        );
    }

    @MethodSource("provideProceedInterviewExceptionCase")
    @ParameterizedTest
    void 인터뷰_진행_예외_문서화(Long interviewId, Long questionId, int docsNo) throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview proceedInterview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        questionRepository.save(QuestionFixtureBuilder.builder().interview(proceedInterview).content(rootQuestion.getContent()).build());
        Interview endInterview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question endQuestion1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(endInterview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(endQuestion1).build());
        Question endQuestion2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(endInterview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(endQuestion2).build());
        Question endQuestion3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(endInterview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(endQuestion3).build());

        // when & then
        mockMvc.perform(post(
                        "/api/v1/interviews/{interview_id}/questions/{question_id}/answers", interviewId, questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest("사용자 답변")))
                )
                .andDo(document("interview-proceedInterview-exception" + docsNo));
    }

    private static Stream<Arguments> provideProceedInterviewExceptionCase() {
        return Stream.of(
                Arguments.of(1000L, 2L, 1), // 존재하지 않는 인터뷰 ID
                Arguments.of(1L, 1000L, 2), // 현재 질문이 아닌 질문 ID
                Arguments.of(2L, 4L, 3) // 인터뷰가 종료된 상태
        );
    }

    @MethodSource("provideFindTotalFeedbacksExceptionCase")
    @ParameterizedTest
    void 인터뷰_최종_결과_조회_예외_문서화(Long interviewId, int docsNo) throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview endInterview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question endQuestion1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(endInterview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(endQuestion1).build());
        Question endQuestion2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(endInterview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(endQuestion2).build());
        Question endQuestion3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(endInterview).content(rootQuestion.getContent()).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(endQuestion3).build());

        // when & then
        mockMvc.perform(get("/api/v1/interviews/{interview_id}/result", interviewId))
                .andDo(document("interview-findTotalFeedbacks-exception" + docsNo));
    }

    private static Stream<Arguments> provideFindTotalFeedbacksExceptionCase() {
        return Stream.of(
                Arguments.of(1000L, 1) // 존재하지 않는 인터뷰 ID
        );
    }
}
