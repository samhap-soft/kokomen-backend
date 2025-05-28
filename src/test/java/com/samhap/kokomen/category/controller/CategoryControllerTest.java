package com.samhap.kokomen.category.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.JsonFieldType.ARRAY;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import org.junit.jupiter.api.Test;

class CategoryControllerTest extends BaseControllerTest {

    @Test
    void 카테고리_목록을_조회한다() throws Exception {
        // given
        String expectedJson = """
                {
                  "categories": [
                    "ALGORITHM",
                    "DATA_STRUCTURE",
                    "DATABASE",
                    "NETWORK",
                    "OPERATING_SYSTEM"
                  ]
                }
                """;

        // when & then
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson))
                .andDo(document("category",
                        responseFields(fieldWithPath("categories").type(ARRAY).description("카테고리 목록"))));
    }
}
