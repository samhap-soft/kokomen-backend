package com.samhap.kokomen.resume.domain;

public enum ResumeAnalysisDimension {

    PROBLEM_SOLVING("problem_solving"),
    PROJECT_EXPERIENCE("project_experience"),
    TECHNICAL_SKILLS("technical_skills"),
    SOFT_SKILLS("soft_skills"),
    JD_FIT("jd_fit"),
    ;

    private final String toolKey;

    ResumeAnalysisDimension(String toolKey) {
        this.toolKey = toolKey;
    }

    /**
     * 툴 스키마 필드 접두사와 응답 JSON 키의 단일 소스. 선언 순서가 곧 표시 순서다.
     */
    public String toolKey() {
        return toolKey;
    }
}
