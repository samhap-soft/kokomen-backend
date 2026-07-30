package com.samhap.kokomen.resume.tool;

/**
 * 이력서 기반 질문 생성(구 플로우, Task 9에서 삭제 예정) 도구/함수 이름의 단일 소스.
 * GPT(function)와 Bedrock(tool)이 동일 이름을 쓰므로 한곳에서 관리한다.
 * 평가 도구 이름은 {@link ResumeAnalysisToolNames}로 이전됐다.
 */
public final class ResumeToolNames {

    public static final String QUESTION_GENERATION = "submit_resume_questions";

    private ResumeToolNames() {
    }
}
