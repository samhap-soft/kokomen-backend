package com.samhap.kokomen.resume.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.interview.external.dto.request.GptFunction;
import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;
import com.samhap.kokomen.interview.external.dto.request.Tool;
import com.samhap.kokomen.interview.external.dto.request.ToolChoice;
import com.samhap.kokomen.interview.external.dto.request.ToolChoiceFunction;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.tool.ResumePromptFragments;
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

    public static final String EVALUATION_FUNCTION_NAME = "submit_resume_evaluation";
    private static final String GPT_MODEL = "gpt-4.1-mini";

    private static final String SYSTEM_PROMPT = """
            <role>
            %s
            </role>

            <task>
            제공된 채용 공고와 직무를 기반으로 제공된 이력서와 포트폴리오를 종합적으로 분석하여 객관적인 평가 및 점수를 산출하라.
            </task>

            %s

            %s

            %s

            %s

            <output>
            반드시 제공된 함수(submit_resume_evaluation)를 호출하여 다음 필드를 모두 제출하라.
            - technical_skills, project_experience, problem_solving, career_growth, documentation : 각 카테고리는 reasoning(점수 산정 전 사고 과정), score(0-100, score_anchors 기준), reason(평가 이유 항목 배열, 2-6개), improvements(보완 사항 항목 배열, 2-6개)
            - total_feedback : 강점·개선·학습 방향을 포함한 종합 총평 (한 단락)
            (종합 점수는 서버에서 가중평균으로 재계산하므로 별도 출력하지 않는다.)
            </output>
            """.formatted(
            ResumePromptFragments.PERSONA_RECRUITER,
            ResumePromptFragments.SECURITY_RULES,
            ResumePromptFragments.EVALUATION_CRITERIA,
            ResumePromptFragments.INDEPENDENCE_PRINCIPLE,
            ResumePromptFragments.SCORE_ANCHORS
    );

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
                new ResumeGptMessage("system", SYSTEM_PROMPT),
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

    private static GptFunctionParameters createEvaluationParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("technical_skills", categorySchema());
        properties.put("project_experience", categorySchema());
        properties.put("problem_solving", categorySchema());
        properties.put("career_growth", categorySchema());
        properties.put("documentation", categorySchema());
        properties.put("total_feedback", Map.of(
                "type", "string",
                "description", "종합 총평. 강점·개선·학습 방향 포함, 한 단락"
        ));

        return new GptFunctionParameters(
                "object",
                properties,
                List.of(
                        "technical_skills",
                        "project_experience",
                        "problem_solving",
                        "career_growth",
                        "documentation",
                        "total_feedback"
                )
        );
    }

    private static Map<String, Object> categorySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reasoning", Map.of(
                "type", "string",
                "description", "점수 산정 전 사고 과정. 카테고리에 한정된 근거만 작성"
        ));
        properties.put("score", Map.of(
                "type", "integer",
                "minimum", 0,
                "maximum", 100,
                "description", "0-100 점수. score_anchors 기준"
        ));
        properties.put("reason", bulletArraySchema("평가 이유 항목들. 각 항목은 한 문장"));
        properties.put("improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 한 문장"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("reasoning", "score", "reason", "improvements")
        );
    }

    private static Map<String, Object> bulletArraySchema(String description) {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 2,
                "maxItems", 6,
                "description", description
        );
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
