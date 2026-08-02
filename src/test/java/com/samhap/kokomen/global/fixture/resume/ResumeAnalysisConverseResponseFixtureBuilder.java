package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * SDK 레벨 목용 해피패스 응답. BedrockConverseClient를 실물로 두고 BedrockRuntimeClient만 목으로 잡으면
 * extractToolUse·parseToolInput·appendCachePoint가 실제 코드로 검증된다.
 * 필드명은 스네이크 케이스로 담는다 — 운영 ObjectMapper의 SNAKE_CASE 정책이 와이어 DTO로 매핑한다.
 */
public class ResumeAnalysisConverseResponseFixtureBuilder {

    public static ResumeAnalysisConverseResponseFixtureBuilder builder() {
        return new ResumeAnalysisConverseResponseFixtureBuilder();
    }

    public ConverseResponse buildEvaluation(boolean jdProvided) {
        Map<String, Document> input = new LinkedHashMap<>();
        putDimension(input, "problem_solving", 90);
        putDimension(input, "project_experience", 80);
        putDimension(input, "technical_skills", 70);
        putDimension(input, "soft_skills", 60);
        if (jdProvided) {
            putDimension(input, "jd_fit", 50);
        }
        input.put("total_feedback", Document.fromString("종합 총평"));
        return toolUseResponse(ResumeAnalysisToolNames.EVALUATION, input);
    }

    public ConverseResponse buildQuestions() {
        List<Document> questions = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> Document.fromMap(Map.of(
                        "question", Document.fromString("질문 " + i),
                        "reason", Document.fromString("이유 " + i))))
                .map(Document.class::cast)
                .toList();
        return toolUseResponse(ResumeAnalysisToolNames.QUESTION_GENERATION,
                Map.of("questions", Document.fromList(questions)));
    }

    /**
     * maxTokens에 걸려 tool_use를 끝내지 못한 응답. extractToolUse가 실제로 던지는 메시지를 워커의 잘림 판정과
     * 맞춰보려면 이 응답을 실물 BedrockConverseClient에 통과시켜야 한다.
     */
    public ConverseResponse buildTruncated() {
        return ConverseResponse.builder()
                .stopReason(StopReason.MAX_TOKENS)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("{\"problem_solving_reasoning\": \"사고 과"))
                                .build())
                        .build())
                .build();
    }

    private void putDimension(Map<String, Document> input, String key, int score) {
        input.put(key + "_reasoning", Document.fromString("사고 과정"));
        input.put(key + "_score", Document.fromNumber(score));
        input.put(key + "_reason", Document.fromList(List.of(
                Document.fromString("근거1"), Document.fromString("근거2"))));
        input.put(key + "_improvements", Document.fromList(List.of(
                Document.fromString("보완1"), Document.fromString("보완2"))));
    }

    private ConverseResponse toolUseResponse(String toolName, Map<String, Document> input) {
        return ConverseResponse.builder()
                .stopReason(StopReason.TOOL_USE)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                        .toolUseId("tool-use-1")
                                        .name(toolName)
                                        .input(Document.fromMap(input))
                                        .build()))
                                .build())
                        .build())
                .build();
    }
}
