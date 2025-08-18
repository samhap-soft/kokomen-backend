package com.samhap.kokomen.category.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseControllerTest;
import org.junit.jupiter.api.Test;

class CategoryControllerTest extends BaseControllerTest {

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
                Category.REACT.name(), Category.REACT.getTitle(),
                Category.REACT.getDescription(), Category.REACT.getImageUrl(),
                Category.FRONTEND.name(), Category.FRONTEND.getTitle(),
                Category.FRONTEND.getDescription(), Category.FRONTEND.getImageUrl(),
                Category.JAVASCRIPT_TYPESCRIPT.name(), Category.JAVASCRIPT_TYPESCRIPT.getTitle(),
                Category.JAVASCRIPT_TYPESCRIPT.getDescription(), Category.JAVASCRIPT_TYPESCRIPT.getImageUrl());

        // when & then
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson))
                .andDo(document("category-findCategories",
                        responseFields(
                                fieldWithPath("[].key").description("카테고리 영문 키값"),
                                fieldWithPath("[].title").description("카테고리 한글명"),
                                fieldWithPath("[].description").description("카테고리 설명"),
                                fieldWithPath("[].image_url").description("카테고리 이미지 URL")
                        )));
    }
}
