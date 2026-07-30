package com.samhap.kokomen.resume.tool;

/**
 * 이력서 기반 질문 생성(구 플로우, Task 9에서 삭제 예정) 시스템 메시지의 GPT·Bedrock 공용 단일 소스.
 * 평가 시스템 메시지는 {@link ResumeAnalysisSystemMessages}로 이전됐다.
 */
public final class ResumeSystemMessages {

    private ResumeSystemMessages() {
    }

    public static String questionGeneration() {
        return """
                <role>
                %s
                </role>

                <task>
                제공된 이력서, 포트폴리오, 직무 경력 정보를 분석하여 기술 면접에서 물어볼 핵심 질문들을 생성하라.
                </task>

                %s

                %s

                <output>
                제공된 도구를 호출하여 questions 배열을 제출하라. 각 항목은 question(질문 내용)과 reason(질문 선정 이유)을 포함해야 한다.
                </output>
                """.formatted(
                ResumePromptFragments.PERSONA_INTERVIEWER,
                ResumePromptFragments.QUESTION_GENERATION_GUIDE,
                ResumePromptFragments.QUESTION_PROBE_LENS);
    }
}
