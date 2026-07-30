package com.samhap.kokomen.resume.tool;

/**
 * 이력서 분석(신규 통합 플로우) 도구/함수 이름의 단일 소스. GPT(function)와 Bedrock(tool)이 동일 이름을 쓴다.
 * 구 {@code ResumeToolNames}와 이름을 공유하지 않는다: (1) 파싱 실패 로그가 toolName만 남기므로 구/신 장애를
 * 로그로 분리할 수 없고, (2) 신규는 jdProvided에 따라 같은 이름으로 두 가지 스키마를 보내므로 구 이름과
 * 겹치면 "같은 도구명, 세 가지 스키마"가 되어 추적이 불가능해진다.
 */
public final class ResumeAnalysisToolNames {

    public static final String EVALUATION = "submit_resume_analysis_evaluation";
    public static final String QUESTION_GENERATION = "submit_resume_analysis_questions";

    private ResumeAnalysisToolNames() {
    }
}
