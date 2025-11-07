package com.samhap.kokomen.recruit.schedular.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecruitmentDto {
    private String id;
    private String affiliate;
    private CompanyDto company;
    private String title;
    private String endDate;
    private String deadlineType;
    private Integer careerMin;
    private Integer careerMax;
    private List<String> region;
    private List<String> employeeType;
    private List<String> education;
    private List<String> depthOne;
    private List<String> depthTwo;
    private Integer views;

    @Override
    public String toString() {
        return "RecruitmentDto{" +
                "id='" + id + '\'' +
                ", affiliate='" + affiliate + '\'' +
                ", company=" + company +
                ", title='" + title + '\'' +
                ", endDate='" + endDate + '\'' +
                ", deadlineType='" + deadlineType + '\'' +
                ", careerMin=" + careerMin +
                ", careerMax=" + careerMax +
                ", region=" + region +
                ", employeeType=" + employeeType +
                ", education=" + education +
                ", depthOne=" + depthOne +
                ", depthTwo=" + depthTwo +
                ", views=" + views +
                '}';
    }
}
