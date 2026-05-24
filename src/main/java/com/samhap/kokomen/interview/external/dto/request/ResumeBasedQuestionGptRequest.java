package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.resume.tool.ResumePromptFragments;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ResumeBasedQuestionGptRequest(
        String model,
        @JsonProperty("messages")
        List<ResumeBasedQuestionGptMessage> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice")
        ToolChoice toolChoice,
        Double temperature
) {

    public static final String QUESTION_GENERATION_FUNCTION_NAME = "submit_resume_questions";
    private static final String GPT_MODEL = "gpt-4.1-mini";

    private static final String SYSTEM_PROMPT = """
            <role>
            %s
            </role>

            <task>
            제공된 이력서, 포트폴리오, 직무 경력 정보를 분석하여 기술 면접에서 물어볼 핵심 질문들을 생성하라.
            </task>

            %s

            <output>
            반드시 제공된 함수(submit_resume_questions)를 호출하여 questions 배열을 제출하라.
            각 항목은 question(질문 내용)과 reason(질문 선정 이유)을 포함해야 한다.
            </output>
            """.formatted(
            ResumePromptFragments.PERSONA_INTERVIEWER,
            ResumePromptFragments.QUESTION_GENERATION_GUIDE
    );

    private static final String USER_PROMPT_TEMPLATE = """
            <resume>
            {{resume_text}}
            </resume>
            <portfolio>
            {{portfolio_text}}
            </portfolio>
            <job_career>
            {{job_career}}
            </job_career>
            """;

    public static ResumeBasedQuestionGptRequest create(
            String resumeText,
            String portfolioText,
            String jobCareer,
            double temperature
    ) {
        String userPrompt = USER_PROMPT_TEMPLATE
                .replace("{{resume_text}}", resumeText != null ? resumeText : "")
                .replace("{{portfolio_text}}", portfolioText != null ? portfolioText : "포트폴리오가 제공되지 않았습니다.")
                .replace("{{job_career}}", jobCareer != null ? jobCareer : "");

        List<ResumeBasedQuestionGptMessage> messages = List.of(
                new ResumeBasedQuestionGptMessage("system", SYSTEM_PROMPT),
                new ResumeBasedQuestionGptMessage("user", userPrompt)
        );

        return new ResumeBasedQuestionGptRequest(
                GPT_MODEL,
                messages,
                List.of(new Tool("function",
                        new GptFunction(QUESTION_GENERATION_FUNCTION_NAME, createQuestionGenerationParams()))),
                new ToolChoice("function", new ToolChoiceFunction(QUESTION_GENERATION_FUNCTION_NAME)),
                temperature
        );
    }

    private static GptFunctionParameters createQuestionGenerationParams() {
        Map<String, Object> questionItem = new LinkedHashMap<>();
        questionItem.put("type", "object");
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("question", Map.of(
                "type", "string",
                "description", "기술 면접에서 물어볼 질문 1문장"
        ));
        itemProps.put("reason", Map.of(
                "type", "string",
                "description", "이 질문을 선택한 이유"
        ));
        questionItem.put("properties", itemProps);
        questionItem.put("required", List.of("question", "reason"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", Map.of(
                "type", "array",
                "items", questionItem,
                "minItems", 5,
                "maxItems", 7,
                "description", "이력서/포트폴리오 기반 면접 질문 목록. 정확히 5-7개"
        ));

        return new GptFunctionParameters(
                "object",
                properties,
                List.of("questions")
        );
    }
}
