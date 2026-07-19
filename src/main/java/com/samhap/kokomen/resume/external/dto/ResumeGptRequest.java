package com.samhap.kokomen.resume.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.interview.external.dto.request.GptFunction;
import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;
import com.samhap.kokomen.interview.external.dto.request.Tool;
import com.samhap.kokomen.interview.external.dto.request.ToolChoice;
import com.samhap.kokomen.interview.external.dto.request.ToolChoiceFunction;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.tool.ResumeSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeToolNames;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ResumeGptRequest(
        String model,
        @JsonProperty("messages")
        List<ResumeGptMessage> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice")
        ToolChoice toolChoice,
        Double temperature
) {

    public static final String EVALUATION_FUNCTION_NAME = ResumeToolNames.EVALUATION;
    private static final String GPT_MODEL = "gpt-4.1-mini";

    private static final String USER_PROMPT_TEMPLATE = """
            <resume>
            {{resume_text}}
            </resume>
            <portfolio>
            {{portfolio_text}}
            </portfolio>
            <target_position>
            {{job_position}}
            </target_position>
            <job_requirements>
            {{job_description}}
            </job_requirements>
            <job_career>
            {{job_career}}
            </job_career>
            """;

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    public static ResumeGptRequest create(ResumeEvaluationRequest request, double temperature) {
        Map<String, String> placeholderValues = Map.of(
                "resume_text", nullToEmpty(request.resume()),
                "portfolio_text", nullToEmpty(request.portfolio()),
                "job_position", nullToEmpty(request.jobPosition()),
                "job_description", nullToEmpty(request.jobDescription()),
                "job_career", nullToEmpty(request.jobCareer())
        );
        String userPrompt = PLACEHOLDER_PATTERN.matcher(USER_PROMPT_TEMPLATE)
                .replaceAll(match -> Matcher.quoteReplacement(
                        placeholderValues.getOrDefault(match.group(1), match.group(0))
                ));

        List<ResumeGptMessage> messages = List.of(
                new ResumeGptMessage("system", ResumeSystemMessages.evaluation()),
                new ResumeGptMessage("user", userPrompt)
        );

        return new ResumeGptRequest(
                GPT_MODEL,
                messages,
                List.of(new Tool("function", new GptFunction(EVALUATION_FUNCTION_NAME, createEvaluationParams()))),
                new ToolChoice("function", new ToolChoiceFunction(EVALUATION_FUNCTION_NAME)),
                temperature
        );
    }

    // 중첩 object는 XML 누수를 유발하므로 5개 카테고리를 flat 필드로 펼친다. 카테고리·경계는 ResumeEvaluationSchema 공용 사양 참조.
    private static GptFunctionParameters createEvaluationParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (String category : ResumeEvaluationSchema.CATEGORIES) {
            putCategoryFields(properties, required, category);
        }
        properties.put("total_feedback", Map.of(
                "type", "string",
                "description", "종합 총평. 강점·개선·학습 방향 포함, 한 단락"
        ));
        required.add("total_feedback");

        return new GptFunctionParameters("object", properties, required);
    }

    private static void putCategoryFields(Map<String, Object> properties, List<String> required, String category) {
        properties.put(category + "_reasoning", Map.of(
                "type", "string",
                "description", "이 카테고리 점수 산정 전 사고 과정. 카테고리에 한정된 근거만 작성"
        ));
        properties.put(category + "_score", Map.of(
                "type", "integer",
                "minimum", ResumeEvaluationSchema.SCORE_MIN,
                "maximum", ResumeEvaluationSchema.SCORE_MAX,
                "description", "0-100 점수. score_anchors 기준"
        ));
        properties.put(category + "_reason", bulletArraySchema("평가 이유 항목들. 각 항목은 정보 밀도 높은 1-2문장"));
        properties.put(category + "_improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 정보 밀도 높은 1-2문장"));
        required.add(category + "_reasoning");
        required.add(category + "_score");
        required.add(category + "_reason");
        required.add(category + "_improvements");
    }

    private static Map<String, Object> bulletArraySchema(String description) {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", ResumeEvaluationSchema.BULLET_MIN_ITEMS,
                "maxItems", ResumeEvaluationSchema.BULLET_MAX_ITEMS,
                "description", description
        );
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
