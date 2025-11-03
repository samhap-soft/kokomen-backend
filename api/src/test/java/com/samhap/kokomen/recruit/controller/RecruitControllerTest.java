package com.samhap.kokomen.recruit.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Region;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RecruitControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 필터_조건_목록_반환() throws Exception {
        List<String> deadlineTypes = DeadlineType.getNames();
        List<String> educations = Education.getNames();
        List<String> employeeTypes = EmployeeType.getNames();
        List<String> employments = Employment.getNames();
        List<String> regions = Region.getNames();

        Map<String, List<String>> expected = Map.of(
                "deadline_type", deadlineTypes,
                "education", educations,
                "employee_type", employeeTypes,
                "employment", employments,
                "region", regions
        );

        String responseJson = objectMapper.writeValueAsString(expected);

        // when & then
        mockMvc.perform(get("/api/v1/recruits/filters"))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("recruit-filters",
                        responseFields(
                                fieldWithPath("deadline_type").description("마감 기한"),
                                fieldWithPath("education").description("학력"),
                                fieldWithPath("employee_type").description("고용 형태"),
                                fieldWithPath("employment").description("직무"),
                                fieldWithPath("region").description("지역")
                        )
                ));
    }
}
