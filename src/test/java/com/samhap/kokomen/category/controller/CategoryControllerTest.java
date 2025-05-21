package com.samhap.kokomen.category.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void findCategories() throws Exception {
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
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson));
    }
}
