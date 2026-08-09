package com.samhap.kokomen.resume.tool;

/**
 * 이력서 분석 Bedrock tool 이름의 단일 소스. 평가·질문 두 콜이 여기서 정의한 이름을 그대로 쓴다.
 * 평가 tool은 같은 이름으로 jdProvided에 따라 두 가지 스키마를 보내므로, 파싱 실패 로그의 toolName만으로는
 * 어느 스키마였는지 구분되지 않는다(호출 로그의 jdProvided를 함께 본다).
 */
public final class ResumeAnalysisToolNames {

    public static final String EVALUATION = "submit_resume_analysis_evaluation";
    public static final String QUESTION_GENERATION = "submit_resume_analysis_questions";

    private ResumeAnalysisToolNames() {
    }
}
