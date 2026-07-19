package com.samhap.kokomen.resume.tool;

/**
 * 이력서 도구/함수 이름의 단일 소스. GPT(function)와 Bedrock(tool)이 동일 이름을 쓰므로 한곳에서 관리한다.
 */
public final class ResumeToolNames {

    public static final String QUESTION_GENERATION = "submit_resume_questions";
    public static final String EVALUATION = "submit_resume_evaluation";

    private ResumeToolNames() {
    }
}
