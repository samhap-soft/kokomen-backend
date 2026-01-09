package com.samhap.kokomen.global.fixture.recruit;

import com.samhap.kokomen.recruit.domain.Affiliate;
import com.samhap.kokomen.recruit.domain.Company;
import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Recruit;
import com.samhap.kokomen.recruit.domain.Region;
import java.time.LocalDateTime;
import java.util.Set;

public class RecruitFixtureBuilder {

    private Long id;
    private String externalId;
    private Affiliate affiliate;
    private String title;
    private LocalDateTime endDate;
    private DeadlineType deadlineType;
    private Integer careerMin;
    private Integer careerMax;
    private String url;
    private Company company;
    private Set<Region> regions;
    private Set<EmployeeType> employeeTypes;
    private Set<Education> educations;
    private Set<Employment> employments;

    public static RecruitFixtureBuilder builder() {
        return new RecruitFixtureBuilder();
    }

    public RecruitFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public RecruitFixtureBuilder externalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public RecruitFixtureBuilder affiliate(Affiliate affiliate) {
        this.affiliate = affiliate;
        return this;
    }

    public RecruitFixtureBuilder title(String title) {
        this.title = title;
        return this;
    }

    public RecruitFixtureBuilder endDate(LocalDateTime endDate) {
        this.endDate = endDate;
        return this;
    }

    public RecruitFixtureBuilder deadlineType(DeadlineType deadlineType) {
        this.deadlineType = deadlineType;
        return this;
    }

    public RecruitFixtureBuilder careerMin(Integer careerMin) {
        this.careerMin = careerMin;
        return this;
    }

    public RecruitFixtureBuilder careerMax(Integer careerMax) {
        this.careerMax = careerMax;
        return this;
    }

    public RecruitFixtureBuilder url(String url) {
        this.url = url;
        return this;
    }

    public RecruitFixtureBuilder company(Company company) {
        this.company = company;
        return this;
    }

    public RecruitFixtureBuilder regions(Set<Region> regions) {
        this.regions = regions;
        return this;
    }

    public RecruitFixtureBuilder employeeTypes(Set<EmployeeType> employeeTypes) {
        this.employeeTypes = employeeTypes;
        return this;
    }

    public RecruitFixtureBuilder educations(Set<Education> educations) {
        this.educations = educations;
        return this;
    }

    public RecruitFixtureBuilder employments(Set<Employment> employments) {
        this.employments = employments;
        return this;
    }

    public Recruit build() {
        return new Recruit(
                id,
                externalId != null ? externalId : "recruit-1",
                affiliate != null ? affiliate : AffiliateFixtureBuilder.builder().build(),
                title != null ? title : "백엔드 개발자 채용",
                endDate != null ? endDate : LocalDateTime.of(2099, 12, 31, 23, 59, 59),
                deadlineType != null ? deadlineType : DeadlineType.DEADLINE_SET,
                careerMin != null ? careerMin : 0,
                careerMax != null ? careerMax : 3,
                url != null ? url : "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=12345",
                company != null ? company : CompanyFixtureBuilder.builder().build(),
                regions != null ? regions : Set.of(Region.SEOUL),
                employeeTypes != null ? employeeTypes : Set.of(EmployeeType.FULL_TIME),
                educations != null ? educations : Set.of(Education.BACHELOR),
                employments != null ? employments : Set.of(Employment.SERVER_BACKEND),
                null,
                null
        );
    }
}
