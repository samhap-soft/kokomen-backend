package com.samhap.kokomen.interview.external.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.external.bedrock.BedrockToolSchemaRenderer;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import com.samhap.kokomen.interview.tool.InterviewToolSchemas;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

/**
 * Stage 3 계약 테스트: 단일 소스({@link InterviewToolSchemas})와 두 provider 렌더러가 동일 필드 집합을 유지하고,
 * 2콜 구조(진행 단계 feedback 유무)와 rank enum 을 각 provider 형식으로 올바르게 실어 보내는지 검증한다.
 */
class InterviewToolSchemaRenderingTest {

    // ---------- 중립 스키마 (단일 소스) ----------

    @Test
    void 진행_스키마는_feedbackInline이면_feedback_필드를_포함한다() {
        ToolSchema schema = InterviewToolSchemas.proceed(true);

        assertThat(schema.name()).isEqualTo("submit_interview_proceed");
        assertThat(fieldNames(schema)).containsExactly("reasoning", "rank", "feedback", "next_question");
    }

    @Test
    void 진행_스키마는_feedbackInline이_아니면_feedback_필드를_제외한다() {
        assertThat(fieldNames(InterviewToolSchemas.proceed(false)))
                .containsExactly("reasoning", "rank", "next_question");
    }

    @Test
    void 진행_스키마의_rank는_A_F_enum을_가진다() {
        ToolField rank = InterviewToolSchemas.proceed(false).fields().stream()
                .filter(field -> field.name().equals("rank"))
                .findFirst()
                .orElseThrow();

        assertThat(rank.enumValues()).containsExactly("A", "B", "C", "D", "F");
    }

    @Test
    void 종료_스키마는_여섯_필드를_가진다() {
        assertThat(fieldNames(InterviewToolSchemas.end()))
                .containsExactly("reasoning", "rank", "feedback", "strengths", "improvements", "learning_direction");
    }

    @Test
    void 답변피드백_스키마는_feedback_필드만_가진다() {
        assertThat(fieldNames(InterviewToolSchemas.answerFeedback())).containsExactly("feedback");
    }

    // ---------- GPT 렌더러 ----------

    @Test
    void GPT_진행_도구는_함수명과_필수필드와_rank_enum을_그대로_싣는다() {
        List<Tool> tools = GptToolRenderer.renderTools(InterviewToolSchemas.proceed(true));
        GptFunction function = tools.get(0).function();

        assertThat(function.name()).isEqualTo("submit_interview_proceed");
        assertThat(function.parameters().required())
                .containsExactly("reasoning", "rank", "feedback", "next_question");

        FunctionParamProperty rank = (FunctionParamProperty) function.parameters().properties().get("rank");
        assertThat(rank.enumValues()).containsExactly("A", "B", "C", "D", "F");
        FunctionParamProperty reasoning = (FunctionParamProperty) function.parameters().properties().get("reasoning");
        assertThat(reasoning.description()).isNotBlank();
    }

    @Test
    void GPT_진행_도구는_feedbackInline이_아니면_feedback_프로퍼티가_없다() {
        List<Tool> tools = GptToolRenderer.renderTools(InterviewToolSchemas.proceed(false));
        GptFunction function = tools.get(0).function();

        assertThat(function.parameters().required()).doesNotContain("feedback");
        assertThat(function.parameters().properties()).doesNotContainKey("feedback");
    }

    @Test
    void GPT_도구_선택은_도구명과_일치한다() {
        assertThat(GptToolRenderer.renderToolChoice(InterviewToolSchemas.end()).function().name())
                .isEqualTo("submit_interview_end");
    }

    // ---------- Bedrock 렌더러 ----------

    @Test
    @SuppressWarnings("unchecked")
    void Bedrock_진행_도구는_feedback_없이_flat_스키마로_렌더된다() {
        ToolConfiguration config = BedrockToolSchemaRenderer.render(InterviewToolSchemas.proceed(false));

        assertThat(config.tools().get(0).toolSpec().name()).isEqualTo("submit_interview_proceed");

        Map<String, Object> json = (Map<String, Object>) DocumentJsonConverter.toJavaObject(
                config.tools().get(0).toolSpec().inputSchema().json());
        assertThat((List<Object>) json.get("required")).containsExactly("reasoning", "rank", "next_question");

        Map<String, Object> properties = (Map<String, Object>) json.get("properties");
        Map<String, Object> rank = (Map<String, Object>) properties.get("rank");
        assertThat((List<Object>) rank.get("enum")).containsExactly("A", "B", "C", "D", "F");
        assertThat(properties).doesNotContainKey("feedback");
    }

    @Test
    @SuppressWarnings("unchecked")
    void Bedrock_종료_도구는_여섯_필드를_모두_렌더한다() {
        ToolConfiguration config = BedrockToolSchemaRenderer.render(InterviewToolSchemas.end());

        assertThat(config.tools().get(0).toolSpec().name()).isEqualTo("submit_interview_end");
        Map<String, Object> json = (Map<String, Object>) DocumentJsonConverter.toJavaObject(
                config.tools().get(0).toolSpec().inputSchema().json());
        Map<String, Object> properties = (Map<String, Object>) json.get("properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrder(
                "reasoning", "rank", "feedback", "strengths", "improvements", "learning_direction");
        assertThat((List<Object>) json.get("required")).hasSize(6);
    }

    @Test
    @SuppressWarnings("unchecked")
    void Bedrock_답변피드백_도구는_feedback_필드만_렌더한다() {
        ToolConfiguration config = BedrockToolSchemaRenderer.render(InterviewToolSchemas.answerFeedback());

        assertThat(config.tools().get(0).toolSpec().name()).isEqualTo("submit_answer_feedback");
        Map<String, Object> json = (Map<String, Object>) DocumentJsonConverter.toJavaObject(
                config.tools().get(0).toolSpec().inputSchema().json());
        Map<String, Object> properties = (Map<String, Object>) json.get("properties");
        assertThat(properties.keySet()).containsExactly("feedback");
    }

    private List<String> fieldNames(ToolSchema schema) {
        return schema.fields().stream()
                .map(ToolField::name)
                .toList();
    }
}
