package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.interview.external.dto.request.GptFunction;
import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;
import com.samhap.kokomen.interview.external.dto.request.Tool;
import com.samhap.kokomen.interview.external.dto.request.ToolChoice;
import com.samhap.kokomen.interview.external.dto.request.ToolChoiceFunction;
import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import com.samhap.kokomen.resume.tool.ResumeAnalysisUserMessages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 신규 이력서 분석 평가 콜의 GPT 폴백 요청. 구 ResumeGptRequest는 0바이트 수정 대상이므로 별 클래스로 둔다.
 * Bedrock과 같은 dimensions(jdProvided) 루프·같은 required 순서로 렌더한다.
 * 전역 SNAKE_CASE 정책이 toolChoice → tool_choice 변환을 담당하므로 @JsonProperty를 쓰지 않는다.
 */
public record ResumeAnalysisEvaluationGptRequest(
        String model,
        List<ResumeGptMessage> messages,
        List<Tool> tools,
        ToolChoice toolChoice,
        Double temperature
) {

    private static final String GPT_MODEL = "gpt-4.1-mini";

    public static ResumeAnalysisEvaluationGptRequest create(ResumeAnalysisCommand command, double temperature) {
        String userPrompt = ResumeAnalysisUserMessages.evaluation(command.jdProvided(), command.resumeText(),
                command.portfolioText(), command.jobPosition(), command.jobDescription(), command.jobCareer());
        List<ResumeGptMessage> messages = List.of(
                new ResumeGptMessage("system", ResumeAnalysisSystemMessages.evaluation(command.jdProvided())),
                new ResumeGptMessage("user", userPrompt));

        return new ResumeAnalysisEvaluationGptRequest(
                GPT_MODEL,
                messages,
                List.of(new Tool("function", new GptFunction(ResumeAnalysisToolNames.EVALUATION,
                        createEvaluationParams(command.jdProvided())))),
                new ToolChoice("function", new ToolChoiceFunction(ResumeAnalysisToolNames.EVALUATION)),
                temperature);
    }

    public static GptFunctionParameters createEvaluationParams(boolean jdProvided) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ResumeAnalysisDimension dimension : ResumeAnalysisSchema.dimensions(jdProvided)) {
            putDimensionFields(properties, required, dimension);
        }
        properties.put("total_feedback", Map.of(
                "type", "string",
                "description", "종합 총평. 강점·개선·학습 방향 포함, 한 단락."));
        required.add("total_feedback");

        return new GptFunctionParameters("object", properties, required);
    }

    private static void putDimensionFields(Map<String, Object> properties, List<String> required,
                                           ResumeAnalysisDimension dimension) {
        String key = dimension.toolKey();
        properties.put(key + "_reasoning", Map.of(
                "type", "string",
                "description", "이 차원 점수 산정 전 사고 과정. 이 차원에 한정된 근거만 작성."));
        properties.put(key + "_score", Map.of(
                "type", "integer",
                "minimum", ResumeAnalysisSchema.SCORE_MIN,
                "maximum", ResumeAnalysisSchema.SCORE_MAX,
                "description", ResumeAnalysisSchema.scoreDescription(dimension)));
        properties.put(key + "_reason", bulletArraySchema("평가 이유 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
        properties.put(key + "_improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
        required.add(key + "_reasoning");
        required.add(key + "_score");
        required.add(key + "_reason");
        required.add(key + "_improvements");
    }

    private static Map<String, Object> bulletArraySchema(String description) {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", ResumeAnalysisSchema.BULLET_MIN_ITEMS,
                "maxItems", ResumeAnalysisSchema.BULLET_MAX_ITEMS,
                "description", description);
    }
}
