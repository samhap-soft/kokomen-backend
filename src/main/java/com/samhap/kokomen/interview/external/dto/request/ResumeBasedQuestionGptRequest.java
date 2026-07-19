package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.resume.tool.ResumeSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeToolNames;
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

    public static final String QUESTION_GENERATION_FUNCTION_NAME = ResumeToolNames.QUESTION_GENERATION;
    private static final String GPT_MODEL = "gpt-4.1-mini";

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
                new ResumeBasedQuestionGptMessage("system", ResumeSystemMessages.questionGeneration()),
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
