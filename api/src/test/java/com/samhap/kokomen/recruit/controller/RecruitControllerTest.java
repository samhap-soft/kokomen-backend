package com.samhap.kokomen.recruit.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.recruit.AffiliateFixtureBuilder;
import com.samhap.kokomen.global.fixture.recruit.CompanyFixtureBuilder;
import com.samhap.kokomen.global.fixture.recruit.RecruitFixtureBuilder;
import com.samhap.kokomen.recruit.domain.Affiliate;
import com.samhap.kokomen.recruit.domain.Company;
import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Region;
import com.samhap.kokomen.recruit.repository.AffiliateRepository;
import com.samhap.kokomen.recruit.repository.CompanyRepository;
import com.samhap.kokomen.recruit.repository.RecruitRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RecruitControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecruitRepository recruitRepository;
    @Autowired
    private AffiliateRepository affiliateRepository;
    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void 필터_조건_목록을_반환한다() throws Exception {
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

    @Test
    void 공고_전체_조회_필터없음() throws Exception {
        Affiliate affiliate = affiliateRepository.save(AffiliateFixtureBuilder.builder().build());
        Company company = companyRepository.save(CompanyFixtureBuilder.builder().build());
        recruitRepository.save(
                RecruitFixtureBuilder.builder()
                        .affiliate(affiliate)
                        .company(company)
                        .build()
        );

        mockMvc.perform(get("/api/v1/recruits"))
                .andExpect(status().isOk())
                .andDo(document("recruit-list",
                        responseFields(
                                fieldWithPath("data[].id").description("공고 ID"),
                                fieldWithPath("data[].affiliate.name").description("제휴사 이름"),
                                fieldWithPath("data[].affiliate.image").description("제휴사 이미지"),
                                fieldWithPath("data[].company.id").description("회사 ID"),
                                fieldWithPath("data[].company.name").description("회사 이름"),
                                fieldWithPath("data[].company.image").description("회사 이미지"),
                                fieldWithPath("data[].title").description("공고 제목"),
                                fieldWithPath("data[].end_date").description("마감일"),
                                fieldWithPath("data[].deadline_type").description("마감 유형"),
                                fieldWithPath("data[].career_min").description("최소 경력"),
                                fieldWithPath("data[].career_max").description("최대 경력"),
                                fieldWithPath("data[].url").description("공고 URL"),
                                fieldWithPath("data[].region").description("지역 목록"),
                                fieldWithPath("data[].employee_type").description("고용 형태 목록"),
                                fieldWithPath("data[].education").description("학력 목록"),
                                fieldWithPath("data[].employment").description("직무 목록"),
                                fieldWithPath("current_page").description("현재 페이지 번호"),
                                fieldWithPath("total_pages").description("전체 페이지 수"),
                                fieldWithPath("has_next").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 공고_조회_단일_필터_지역() throws Exception {
        Affiliate affiliate = affiliateRepository.save(AffiliateFixtureBuilder.builder().build());
        Company company = companyRepository.save(CompanyFixtureBuilder.builder().build());
        recruitRepository.save(
                RecruitFixtureBuilder.builder()
                        .affiliate(affiliate)
                        .company(company)
                        .regions(Set.of(Region.SEOUL))
                        .build()
        );

        String responseJson = """
                {
                  "data" : [ {
                    "id" : "recruit-1",
                    "affiliate" : {
                      "name" : "V1",
                      "image" : "https://example.com/saramin.png"
                    },
                    "company" : {
                      "id" : "company-1",
                      "name" : "삼합소프트",
                      "image" : "https://example.com/company.png"
                    },
                    "title" : "백엔드 개발자 채용",
                    "end_date" : "2025-12-31T23:59:59",
                    "deadline_type" : "기한 설정",
                    "career_min" : 0,
                    "career_max" : 3,
                    "region" : [ "서울" ],
                    "employee_type" : [ "정규직" ],
                    "education" : [ "학사" ],
                    "employment" : [ "서버·백엔드" ],
                    "url" : "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=12345"
                  } ],
                  "current_page" : 0,
                  "total_pages" : 1,
                  "has_next" : false
                }
                """;

        mockMvc.perform(get("/api/v1/recruits")
                        .param("region", "서울"))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("recruit-list-filter-region"));
    }

    @Test
    void 공고_조회_다중_필터_지역과_고용형태() throws Exception {
        Affiliate affiliate = affiliateRepository.save(AffiliateFixtureBuilder.builder().build());
        Company company = companyRepository.save(CompanyFixtureBuilder.builder().build());
        recruitRepository.save(
                RecruitFixtureBuilder.builder()
                        .affiliate(affiliate)
                        .company(company)
                        .regions(Set.of(Region.SEOUL))
                        .employeeTypes(Set.of(EmployeeType.FULL_TIME))
                        .build()
        );
        recruitRepository.save(
                RecruitFixtureBuilder.builder()
                        .externalId("recruit-2")
                        .affiliate(affiliate)
                        .company(company)
                        .regions(Set.of(Region.SEOUL))
                        .employeeTypes(Set.of(EmployeeType.CONTRACT))
                        .build()
        );

        mockMvc.perform(get("/api/v1/recruits")
                        .param("region", "서울")
                        .param("employeeType", "정규직"))
                .andExpect(status().isOk())
                .andDo(document("recruit-list-filter-multiple"));
    }

    @Test
    void 공고_조회_경력_범위_필터() throws Exception {
        Affiliate affiliate = affiliateRepository.save(AffiliateFixtureBuilder.builder().build());
        Company company = companyRepository.save(CompanyFixtureBuilder.builder().build());
        recruitRepository.save(
                RecruitFixtureBuilder.builder()
                        .affiliate(affiliate)
                        .company(company)
                        .careerMin(0)
                        .careerMax(3)
                        .build()
        );
        recruitRepository.save(
                RecruitFixtureBuilder.builder()
                        .externalId("recruit-2")
                        .affiliate(affiliate)
                        .company(company)
                        .careerMin(5)
                        .careerMax(10)
                        .build()
        );

        mockMvc.perform(get("/api/v1/recruits")
                        .param("careerMin", "2")
                        .param("careerMax", "4"))
                .andExpect(status().isOk())
                .andDo(document("recruit-list-filter-career"));
    }

    @Test
    void 공고_조회_페이징() throws Exception {
        Affiliate affiliate = affiliateRepository.save(AffiliateFixtureBuilder.builder().build());
        Company company = companyRepository.save(CompanyFixtureBuilder.builder().build());
        for (int i = 1; i <= 15; i++) {
            recruitRepository.save(
                    RecruitFixtureBuilder.builder()
                            .externalId("recruit-" + i)
                            .affiliate(affiliate)
                            .company(company)
                            .build()
            );
        }

        mockMvc.perform(get("/api/v1/recruits")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andDo(document("recruit-list-pagination"));
    }

    @Test
    void 공고_조회_빈_결과() throws Exception {
        mockMvc.perform(get("/api/v1/recruits")
                        .param("region", "서울"))
                .andExpect(status().isOk())
                .andDo(document("recruit-list-empty"));
    }
}
