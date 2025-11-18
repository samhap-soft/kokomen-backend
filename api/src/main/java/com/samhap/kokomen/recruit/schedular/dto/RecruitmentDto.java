package com.samhap.kokomen.recruit.schedular.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("endDate")
    private String endDate;
    @JsonProperty("deadlineType")
    private String deadlineType;
    @JsonProperty("careerMin")
    private Integer careerMin;
    @JsonProperty("careerMax")
    private Integer careerMax;
    private List<String> region;
    @JsonProperty("employeeType")
    private List<String> employeeType;
    private List<String> education;
    @JsonProperty("depthOne")
    private List<String> depthOne;
    @JsonProperty("depthTwo")
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
