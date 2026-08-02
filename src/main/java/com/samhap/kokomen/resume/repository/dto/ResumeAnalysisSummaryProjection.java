package com.samhap.kokomen.resume.repository.dto;

import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import java.time.LocalDateTime;

/**
 * 목록 조회용 닫힌 프로젝션. job_description·total_feedback(TEXT)과 JSON 10컬럼을 끌고 오지 않는다.
 */
public interface ResumeAnalysisSummaryProjection {

    Long getId();

    ResumeAnalysisState getState();

    String getJobPosition();

    String getJobCareer();

    boolean isJdProvided();

    Integer getTotalScore();

    LocalDateTime getCreatedAt();
}
