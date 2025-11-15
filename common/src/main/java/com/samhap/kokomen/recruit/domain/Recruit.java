package com.samhap.kokomen.recruit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Recruit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private Affiliate affiliate;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "deadline_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    private DeadlineType deadlineType;

    @Column(name = "career_min")
    private Integer careerMin;

    @Column(name = "career_max")
    private Integer careerMax;

    @Column(name = "url", nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ElementCollection
    @CollectionTable(
            name = "recruit_region",
            joinColumns = @JoinColumn(name = "recruit_id")
    )
    @Column(name = "region")
    @Enumerated(value = EnumType.STRING)
    private Set<Region> regions;

    @ElementCollection
    @CollectionTable(
            name = "recruit_employee_type",
            joinColumns = @JoinColumn(name = "recruit_id")
    )
    @Column(name = "employee_type")
    @Enumerated(value = EnumType.STRING)
    private Set<EmployeeType> employeeTypes;

    @ElementCollection
    @CollectionTable(
            name = "recruit_education",
            joinColumns = @JoinColumn(name = "recruit_id")
    )
    @Column(name = "education")
    @Enumerated(value = EnumType.STRING)
    private Set<Education> educations;

    @ElementCollection
    @CollectionTable(
            name = "recruit_employment",
            joinColumns = @JoinColumn(name = "recruit_id")
    )
    @Column(name = "employment")
    @Enumerated(value = EnumType.STRING)
    private Set<Employment> employments;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "apply_url", nullable = false)
    private String applyUrl;

    @Override
    public String toString() {
        return "Recruit{" +
                "id=" + id +
                ", externalId='" + externalId + '\'' +
                ", affiliate=" + affiliate +
                ", title='" + title + '\'' +
                ", endDate=" + endDate +
                ", deadlineType=" + deadlineType +
                ", careerMin=" + careerMin +
                ", careerMax=" + careerMax +
                ", url='" + url + '\'' +
                ", company=" + company +
                ", regions=" + regions +
                ", employeeTypes=" + employeeTypes +
                ", educations=" + educations +
                ", employments=" + employments +
                ", content='" + content + '\'' +
                ", applyUrl='" + applyUrl + '\'' +
                '}';
    }
}
