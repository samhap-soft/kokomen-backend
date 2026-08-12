package com.samhap.kokomen.category.controller;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.OnboardingSurveyFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.survey.CareerGoal;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.OnboardingSurveyRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultMatcher;

class CategoryControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private OnboardingSurveyRepository onboardingSurveyRepository;

    /**
     * content().json()은 LENIENT 모드라 배열 순서를 검사하지 않으므로, 순서는 인덱스별 jsonPath로 단정한다.
     */
    private static ResultMatcher[] keysInOrder(Category... categories) {
        return IntStream.range(0, categories.length)
                .mapToObj(index -> jsonPath("$[%d].key".formatted(index)).value(categories[index].name()))
                .toArray(ResultMatcher[]::new);
    }

    private static Category[] defaultOrder() {
        return Category.getCategories().toArray(Category[]::new);
    }

    @Test
    void 카테고리_목록을_조회한다() throws Exception {
        // given
        String expectedJson = """
                [
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  },
                  {
                    "key": "%s",
                    "title": "%s",
                    "description": "%s",
                    "image_url": "%s"
                  }
                ]
                """.formatted(
                Category.ALGORITHM_DATA_STRUCTURE.name(), Category.ALGORITHM_DATA_STRUCTURE.getTitle(),
                Category.ALGORITHM_DATA_STRUCTURE.getDescription(), Category.ALGORITHM_DATA_STRUCTURE.getImageUrl(),
                Category.DATABASE.name(), Category.DATABASE.getTitle(),
                Category.DATABASE.getDescription(), Category.DATABASE.getImageUrl(),
                Category.NETWORK.name(), Category.NETWORK.getTitle(),
                Category.NETWORK.getDescription(), Category.NETWORK.getImageUrl(),
                Category.OPERATING_SYSTEM.name(), Category.OPERATING_SYSTEM.getTitle(),
                Category.OPERATING_SYSTEM.getDescription(), Category.OPERATING_SYSTEM.getImageUrl(),
                Category.JAVA_SPRING.name(), Category.JAVA_SPRING.getTitle(),
                Category.JAVA_SPRING.getDescription(), Category.JAVA_SPRING.getImageUrl(),
                Category.INFRA.name(), Category.INFRA.getTitle(),
                Category.INFRA.getDescription(), Category.INFRA.getImageUrl(),
                Category.FRONTEND.name(), Category.FRONTEND.getTitle(),
                Category.FRONTEND.getDescription(), Category.FRONTEND.getImageUrl(),
                Category.REACT.name(), Category.REACT.getTitle(),
                Category.REACT.getDescription(), Category.REACT.getImageUrl(),
                Category.JAVASCRIPT_TYPESCRIPT.name(), Category.JAVASCRIPT_TYPESCRIPT.getTitle(),
                Category.JAVASCRIPT_TYPESCRIPT.getDescription(), Category.JAVASCRIPT_TYPESCRIPT.getImageUrl(),
                Category.PERSONALITY.name(), Category.PERSONALITY.getTitle(),
                Category.PERSONALITY.getDescription(), Category.PERSONALITY.getImageUrl());

        // when & then
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson))
                .andExpectAll(keysInOrder(defaultOrder()))
                .andDo(document("category-findCategories",
                        responseFields(
                                fieldWithPath("[].key").description("카테고리 영문 키값"),
                                fieldWithPath("[].title").description("카테고리 한글명"),
                                fieldWithPath("[].description").description("카테고리 설명"),
                                fieldWithPath("[].image_url").description("카테고리 이미지 URL")
                        )));
    }

    @Test
    void 온보딩_설문을_작성한_회원은_선호_순으로_정렬된_카테고리를_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        onboardingSurveyRepository.save(OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .careerGoal(CareerGoal.BACKEND)
                .techTopics(List.of(Category.JAVA_SPRING, Category.DATABASE, Category.JAVASCRIPT_TYPESCRIPT))
                .build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/categories")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpectAll(keysInOrder(
                        Category.DATABASE,
                        Category.JAVA_SPRING,
                        Category.JAVASCRIPT_TYPESCRIPT,
                        Category.ALGORITHM_DATA_STRUCTURE,
                        Category.NETWORK,
                        Category.OPERATING_SYSTEM,
                        Category.INFRA,
                        Category.FRONTEND,
                        Category.REACT,
                        Category.PERSONALITY
                ))
                .andDo(document("category-findCategories-personalized",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("[].key").description("카테고리 영문 키값"),
                                fieldWithPath("[].title").description("카테고리 한글명"),
                                fieldWithPath("[].description").description("카테고리 설명"),
                                fieldWithPath("[].image_url").description("카테고리 이미지 URL")
                        )));
    }

    @Test
    void 온보딩_설문을_작성하지_않은_회원은_기본_순서로_카테고리를_조회한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/categories")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpectAll(keysInOrder(defaultOrder()));
    }
}
