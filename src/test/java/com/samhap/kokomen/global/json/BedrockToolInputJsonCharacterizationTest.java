package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.AnswerRankResponse;
import com.samhap.kokomen.interview.external.dto.response.BedrockConverseResponse;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisEvaluationFlatResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Bedrock toolUse 입력(Document)을 운영 경로 그대로(DocumentJsonConverter → 주입받는 매퍼의 convertValue) DTO로 바꾼 결과를 고정한다.
 * 숫자는 모두 BigDecimal로 넘어가므로 정수 필드의 소수·문자열·null·누락과, 문자열 필드에 온 다른 타입을 지금처럼 받는지 본다.
 */
class BedrockToolInputJsonCharacterizationTest extends BaseTest {

    private static final String TOOL_NAME = "submit_evaluation";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BedrockConverseClient converseClient;

    @Test
    void 랭크와_피드백을_읽고_모르는_필드는_버린다() {
        BedrockConverseResponse response = new BedrockConverseResponse(document(Map.of(
                "rank", Document.fromString("A"), "feedback", Document.fromString("좋아요 😀"),
                "reasoning", Document.fromString("무시되는 필드"))));

        assertThat(response.extractAnswerFeedbackResponse(objectMapper))
                .isEqualTo(new AnswerFeedbackResponse("A", "좋아요 😀"));
    }

    @Test
    void 문자열_필드에_온_숫자와_boolean은_문자열로_받고_없거나_null이면_null이다() {
        assertAll(
                () -> assertThat(rankOf(Map.of("rank", Document.fromNumber(1)))).isEqualTo(new AnswerRankResponse("1")),
                () -> assertThat(rankOf(Map.of("rank", Document.fromNumber(new BigDecimal("2.50")))))
                        .isEqualTo(new AnswerRankResponse("2.50")),
                () -> assertThat(rankOf(Map.of("rank", Document.fromBoolean(true))))
                        .isEqualTo(new AnswerRankResponse("true")),
                () -> assertThat(rankOf(Map.of("rank", Document.fromNull()))).isEqualTo(new AnswerRankResponse(null)),
                () -> assertThat(rankOf(Map.of())).isEqualTo(new AnswerRankResponse(null))
        );
    }

    @Test
    void 문자열_필드에_배열이나_객체가_오면_타입_이름을_담은_ExternalApiException이다() {
        assertAll(
                () -> assertThatThrownBy(
                                () -> rankOf(Map.of("rank", Document.fromList(List.of(Document.fromString("A"))))))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("Bedrock toolUse 파싱 실패: type=AnswerRankResponse"),
                () -> assertThatThrownBy(() -> rankOf(Map.of("rank", document(Map.of("x", Document.fromNumber(1))))))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("Bedrock toolUse 파싱 실패: type=AnswerRankResponse")
        );
    }

    @Test
    void 종합_피드백은_snake_case_키만_읽는다() {
        BedrockConverseResponse response = new BedrockConverseResponse(document(Map.of(
                "strengths", Document.fromString("구조화"), "improvements", Document.fromString("예시"),
                "learning_direction", Document.fromString("심화"), "learningDirection", Document.fromString("무시"))));

        assertThat(response.extractTotalFeedbackResponse(objectMapper))
                .isEqualTo(new TotalFeedbackResponse("구조화", "예시", "심화"));
    }

    @Test
    void 정수_필드는_소수를_버리고_숫자_문자열을_받으며_null과_누락은_null이다() {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("problem_solving_score", Document.fromNumber(85));
        input.put("project_experience_score", Document.fromNumber(new BigDecimal("72.9")));
        input.put("technical_skills_score", Document.fromString("90"));
        input.put("soft_skills_score", Document.fromNull());
        input.put("jd_fit_score", Document.fromNumber(new BigDecimal("1E+2")));
        input.put("problem_solving_reason", Document.fromList(List.of(Document.fromString("근거"), Document.fromNull())));
        input.put("total_feedback", Document.fromNumber(new BigDecimal("3.0")));

        ResumeAnalysisEvaluationFlatResponse response = converseClient.parseToolInput(toolUse(document(input)),
                ResumeAnalysisEvaluationFlatResponse.class);

        assertAll(
                () -> assertThat(response.problemSolvingScore()).isEqualTo(85),
                () -> assertThat(response.projectExperienceScore()).isEqualTo(72),
                () -> assertThat(response.technicalSkillsScore()).isEqualTo(90),
                () -> assertThat(response.softSkillsScore()).isNull(),
                () -> assertThat(response.jdFitScore()).isEqualTo(100),
                () -> assertThat(response.problemSolvingReason()).containsExactly("근거", null),
                () -> assertThat(response.projectExperienceReason()).isNull(),
                () -> assertThat(response.totalFeedback()).isEqualTo("3.0")
        );
    }

    @Test
    void 정수_필드에_범위를_넘는_수나_boolean이나_숫자가_아닌_문자열이_오면_도구_이름을_담은_ExternalApiException이다() {
        assertAll(
                () -> assertThatThrownBy(
                                () -> evaluationOf("problem_solving_score", Document.fromNumber(3_000_000_000L)))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("Bedrock toolUse 파싱 실패: toolName=" + TOOL_NAME),
                () -> assertThatThrownBy(() -> evaluationOf("problem_solving_score", Document.fromBoolean(true)))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("Bedrock toolUse 파싱 실패: toolName=" + TOOL_NAME),
                () -> assertThatThrownBy(() -> evaluationOf("problem_solving_score", Document.fromString("높음")))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("Bedrock toolUse 파싱 실패: toolName=" + TOOL_NAME),
                () -> assertThatThrownBy(() -> evaluationOf("problem_solving_reason", Document.fromString("한 줄")))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("Bedrock toolUse 파싱 실패: toolName=" + TOOL_NAME)
        );
    }

    @Test
    void 문자열_목록_필드에_중첩_배열이_오면_뒤의_필드를_버리지_않고_도구_이름을_담은_ExternalApiException이다() {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("problem_solving_reason", Document.fromList(List.of(
                Document.fromList(List.of(Document.fromString("근거 1"), Document.fromString("근거 2"))))));
        input.put("problem_solving_score", Document.fromNumber(85));
        input.put("total_feedback", Document.fromString("종합"));

        assertThatThrownBy(() -> converseClient.parseToolInput(toolUse(document(input)),
                ResumeAnalysisEvaluationFlatResponse.class))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("Bedrock toolUse 파싱 실패: toolName=" + TOOL_NAME);
    }

    @Test
    void 주입받는_매퍼는_문자열_목록_속성에_중첩_배열이_오면_읽지_못한다() {
        assertAll(
                () -> assertThatThrownBy(() -> objectMapper.readValue(
                        "{\"problem_solving_reason\":[[\"근거\"]],\"total_feedback\":\"종합\"}",
                        ResumeAnalysisEvaluationFlatResponse.class))
                        .isInstanceOf(MismatchedInputException.class),
                () -> assertThatThrownBy(() -> objectMapper.readValue(
                        "{\"problem_solving_reason\":[\"근거\",[\"더\"]],\"total_feedback\":\"종합\"}",
                        ResumeAnalysisEvaluationFlatResponse.class))
                        .isInstanceOf(MismatchedInputException.class)
        );
    }

    @Test
    void 주입받는_매퍼는_문자열_목록_속성에_빈_문자열이나_공백_문자열이_오면_값_오류로_거절한다() {
        assertAll(
                () -> assertThatThrownBy(() -> objectMapper.readValue(
                        "{\"problem_solving_reason\":\"\",\"total_feedback\":\"종합\"}",
                        ResumeAnalysisEvaluationFlatResponse.class))
                        .isInstanceOf(InvalidFormatException.class),
                () -> assertThatThrownBy(() -> objectMapper.readValue(
                        "{\"problem_solving_reason\":\"  \",\"total_feedback\":\"종합\"}",
                        ResumeAnalysisEvaluationFlatResponse.class))
                        .isInstanceOf(InvalidFormatException.class)
        );
    }

    @Test
    void 트리로_읽은_입력은_숫자와_문자열과_중첩을_지금_표기대로_보여준다() {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("rank", Document.fromString("A"));
        input.put("score", Document.fromNumber(new BigDecimal("1.50")));
        input.put("count", Document.fromNumber(3));
        input.put("big", Document.fromNumber(new BigDecimal("12345678901234567890.0100")));
        input.put("emoji", Document.fromString("😀 \"따옴표\""));
        input.put("nested", document(Map.of("list", Document.fromList(List.of(Document.fromNumber(1),
                Document.fromString("x"), Document.fromNull(), Document.fromBoolean(true))))));

        JsonNode node = converseClient.parseToolInput(toolUse(document(input)), JsonNode.class);

        assertAll(
                () -> assertThat(node.toString()).isEqualTo("""
                        {"rank":"A","score":1.5,"count":3,"big":12345678901234567890.01,\
                        "emoji":"😀 \\"따옴표\\"","nested":{"list":[1,"x",null,true]}}"""),
                () -> assertThat(node.get("rank").isTextual()).isTrue(),
                () -> assertThat(node.get("score").isTextual()).isFalse(),
                () -> assertThat(node.get("missing")).isNull()
        );
    }

    private AnswerRankResponse rankOf(Map<String, Document> input) {
        return new BedrockConverseResponse(document(input)).extractAnswerRankResponse(objectMapper);
    }

    private ResumeAnalysisEvaluationFlatResponse evaluationOf(String field, Document value) {
        return converseClient.parseToolInput(toolUse(document(Map.of(field, value))),
                ResumeAnalysisEvaluationFlatResponse.class);
    }

    private static Document document(Map<String, Document> values) {
        return Document.fromMap(new LinkedHashMap<>(values));
    }

    private static ToolUseBlock toolUse(Document input) {
        return ToolUseBlock.builder().toolUseId("tool-use-1").name(TOOL_NAME).input(input).build();
    }
}
