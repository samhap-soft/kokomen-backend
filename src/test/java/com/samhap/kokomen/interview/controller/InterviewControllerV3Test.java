package com.samhap.kokomen.interview.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionState;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.JsonFieldType;

class InterviewControllerV3Test extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private TokenRepository tokenRepository;

    @Test
    void 루트_질문_리스트_조회() throws Exception {
        // given
        rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder()
                        .category(Category.ALGORITHM_DATA_STRUCTURE)
                        .rootQuestionState(RootQuestionState.ACTIVE)
                        .content("이진 탐색의 시간 복잡도를 설명해주세요.")
                        .questionOrder(1)
                        .build()
        );
        rootQuestionRepository.save(
                RootQuestion.forCode(Category.ALGORITHM_DATA_STRUCTURE, "Two Sum",
                        "정수 배열 nums와 정수 target이 주어집니다. 두 원소를 더해 target이 되는 인덱스를 반환하세요.")
        );

        // when & then
        mockMvc.perform(get("/api/v3/interview/questions")
                        .param("category", "ALGORITHM_DATA_STRUCTURE")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                // 일반 + 라이브 코테 문제가 모두 조회된다
                .andExpect(jsonPath("$", hasSize(2)))
                // 일반 질문은 GENERAL 타입 (title은 non_null 정책상 응답에서 생략됨)
                .andExpect(jsonPath("$[?(@.question_type == 'GENERAL')]", hasSize(1)))
                // 라이브 코테 문제는 CODE 타입으로 구분되고 제목이 전달된다
                .andExpect(jsonPath("$[?(@.question_type == 'CODE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.question_type == 'CODE')].title", contains("Two Sum")))
                .andDo(document("interview-v3-getRootQuestions",
                        queryParameters(
                                parameterWithName("category").description("질문 카테고리")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("루트 질문 ID"),
                                fieldWithPath("[].question_type").description(
                                        "질문 타입 (GENERAL: 일반 질문, CODE: 라이브 코딩테스트)"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).optional()
                                        .description("코딩테스트 문제 제목 (CODE 타입만 존재, GENERAL은 null)"),
                                fieldWithPath("[].content").description("루트 질문 내용 (CODE 타입은 마크다운 문제 설명)")
                        )
                ));
    }

    @Test
    void 커스텀_인터뷰_시작_텍스트_모드() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder()
                        .rootQuestionState(RootQuestionState.ACTIVE)
                        .build()
        );

        String requestJson = """
                {
                  "rootQuestionId": %d,
                  "maxQuestionCount": 5,
                  "mode": "TEXT"
                }
                """.formatted(rootQuestion.getId());

        // when & then
        mockMvc.perform(post("/api/v3/interview/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andDo(document("interview-v3-createCustomInterview-textMode",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("rootQuestionId").description("선택한 루트 질문 ID"),
                                fieldWithPath("maxQuestionCount").description("최대 질문 수"),
                                fieldWithPath("mode").description("인터뷰 모드 (TEXT, VOICE)")
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID"),
                                fieldWithPath("question_id").description("첫 번째 질문 ID"),
                                fieldWithPath("root_question").description("루트 질문 내용")
                        )
                ));
    }

    @Test
    void 커스텀_인터뷰_시작_보이스_모드() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder()
                        .rootQuestionState(RootQuestionState.ACTIVE)
                        .build()
        );

        String requestJson = """
                {
                  "rootQuestionId": %d,
                  "maxQuestionCount": 5,
                  "mode": "VOICE"
                }
                """.formatted(rootQuestion.getId());

        // when & then
        mockMvc.perform(post("/api/v3/interview/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isCreated())
                .andDo(document("interview-v3-createCustomInterview-voiceMode",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("rootQuestionId").description("선택한 루트 질문 ID"),
                                fieldWithPath("maxQuestionCount").description("최대 질문 수"),
                                fieldWithPath("mode").description("인터뷰 모드 (TEXT, VOICE)")
                        ),
                        responseFields(
                                fieldWithPath("interview_id").description("생성된 인터뷰 ID"),
                                fieldWithPath("question_id").description("첫 번째 질문 ID"),
                                fieldWithPath("root_question_voice_url").description("루트 질문 음성 url")
                        )
                ));
    }
}
