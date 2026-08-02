package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.interview.external.dto.request.GptFunction;
import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;
import com.samhap.kokomen.interview.external.dto.request.Tool;
import com.samhap.kokomen.interview.external.dto.request.ToolChoice;
import com.samhap.kokomen.interview.external.dto.request.ToolChoiceFunction;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import com.samhap.kokomen.resume.tool.ResumeAnalysisUserMessages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 신규 이력서 분석 질문 콜의 GPT 폴백 요청. minItems/maxItems/maxLength는 ResumeAnalysisSchema를 참조해
 * Bedrock 스키마와 매직넘버가 갈리지 않게 한다.
 */
public record ResumeAnalysisQuestionGptRequest(
        String model,
        List<ResumeGptMessage> messages,
        List<Tool> tools,
        ToolChoice toolChoice,
        Double temperature
) {

    private static final String GPT_MODEL = "gpt-4.1-mini";

    public static ResumeAnalysisQuestionGptRequest create(ResumeAnalysisQuestionCallCommand command,
                                                          double temperature) {
        String userPrompt = ResumeAnalysisUserMessages.questionGeneration(command.resumeText(),
                command.portfolioText(), command.jobPosition(), command.jobCareer(), command.evaluationResult());
        List<ResumeGptMessage> messages = List.of(
                new ResumeGptMessage("system", ResumeAnalysisSystemMessages.questionGeneration()),
                new ResumeGptMessage("user", userPrompt));

        return new ResumeAnalysisQuestionGptRequest(
                GPT_MODEL,
                messages,
                List.of(new Tool("function", new GptFunction(ResumeAnalysisToolNames.QUESTION_GENERATION,
                        createQuestionParams()))),
                new ToolChoice("function", new ToolChoiceFunction(ResumeAnalysisToolNames.QUESTION_GENERATION)),
                temperature);
    }

    public static GptFunctionParameters createQuestionParams() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("question", Map.of(
                "type", "string",
                "maxLength", ResumeAnalysisSchema.QUESTION_MAX_LENGTH,
                "description", "질문 내용. " + ResumeAnalysisSchema.QUESTION_MAX_LENGTH + "자 이내."));
        itemProperties.put("reason", Map.of(
                "type", "string",
                "maxLength", ResumeAnalysisSchema.QUESTION_REASON_MAX_LENGTH,
                "description", "질문 선정 이유. " + ResumeAnalysisSchema.QUESTION_REASON_MAX_LENGTH + "자 이내."));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", itemProperties,
                        "required", List.of("question", "reason")),
                "minItems", ResumeAnalysisSchema.QUESTION_MIN_ITEMS,
                "maxItems", ResumeAnalysisSchema.QUESTION_MAX_ITEMS,
                "description", "이력서/포트폴리오 기반 면접 질문 목록. 정확히 5-7개."));

        return new GptFunctionParameters("object", properties, List.of("questions"));
    }
}
