package com.samhap.kokomen.global.external.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BedrockToolSchemaRendererTest {

    // 문자열 필드, enum 필드, 원소가 객체인 배열 필드를 하나씩 둔 도구. 배열 원소의 속성도 같은 규칙(문자열·enum)으로 그린다.
    private static final ToolSchema SCHEMA = new ToolSchema("submit_sample", "예시 도구를 제출한다.", List.of(
            ToolField.required("reasoning", "판정 근거."),
            ToolField.requiredEnum("verdict", "판정.", List.of("YES", "NO")),
            ToolField.requiredObjectArray("items", "확정한 항목. 0~3개.", List.of(
                    ToolField.required("name", "짧은 이름."),
                    ToolField.requiredEnum("source", "출처.", List.of("SEED", "ASSUMED"))))));

    private static final String RENDERED_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "reasoning": {"type": "string", "description": "판정 근거."},
                "verdict": {"type": "string", "description": "판정.", "enum": ["YES", "NO"]},
                "items": {
                  "type": "array",
                  "description": "확정한 항목. 0~3개.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string", "description": "짧은 이름."},
                      "source": {"type": "string", "description": "출처.", "enum": ["SEED", "ASSUMED"]}
                    },
                    "required": ["name", "source"]
                  }
                }
              },
              "required": ["reasoning", "verdict", "items"]
            }
            """;

    private final ObjectMapper objectMapper = Jackson2CompatibleJsonMapper.builder().build();

    // 키 순서는 렌더러가 정하지 않는 곳(최상위·원소 스키마의 type·properties·required)이 있어 전체는 구조로 비교하고,
    // 순서가 뜻을 갖는 properties와 required는 따로 순서를 본다.
    @Test
    void 객체_배열_필드는_원소_객체의_속성과_required를_담은_array로_그린다() throws Exception {
        // when
        ToolConfiguration toolConfiguration = BedrockToolSchemaRenderer.render(SCHEMA);

        // then
        JsonNode inputSchema = inputSchema(toolConfiguration);
        assertAll(
                () -> assertThat(inputSchema).isEqualTo(objectMapper.readTree(RENDERED_INPUT_SCHEMA)),
                () -> assertThat(fieldNames(inputSchema.at("/properties")))
                        .containsExactly("reasoning", "verdict", "items"),
                () -> assertThat(fieldNames(inputSchema.at("/properties/items")))
                        .containsExactly("type", "description", "items"),
                () -> assertThat(fieldNames(inputSchema.at("/properties/items/items/properties")))
                        .containsExactly("name", "source"),
                () -> assertThat(textValues(inputSchema.at("/properties/items/items/required")))
                        .containsExactly("name", "source")
        );
    }

    @Test
    void 도구_이름과_설명을_싣고_그_도구를_반드시_부르게_한다() {
        // when
        ToolConfiguration toolConfiguration = BedrockToolSchemaRenderer.render(SCHEMA);

        // then
        assertAll(
                () -> assertThat(toolConfiguration.tools()).hasSize(1),
                () -> assertThat(toolConfiguration.tools().get(0).toolSpec().name()).isEqualTo("submit_sample"),
                () -> assertThat(toolConfiguration.tools().get(0).toolSpec().description())
                        .isEqualTo("예시 도구를 제출한다."),
                () -> assertThat(toolConfiguration.toolChoice().tool().name()).isEqualTo("submit_sample")
        );
    }

    @Test
    void 객체_배열_필드는_원소_속성을_복사해_담는다() {
        // given
        List<ToolField> itemFields = new ArrayList<>(List.of(ToolField.required("name", "짧은 이름.")));

        // when
        ToolField field = ToolField.requiredObjectArray("items", "확정한 항목.", itemFields);
        itemFields.clear();

        // then
        assertAll(
                () -> assertThat(field.isObjectArray()).isTrue(),
                () -> assertThat(field.required()).isTrue(),
                () -> assertThat(field.enumValues()).isNull(),
                () -> assertThat(field.itemFields()).extracting(ToolField::name).containsExactly("name"),
                () -> assertThat(ToolField.required("reasoning", "판정 근거.").isObjectArray()).isFalse(),
                () -> assertThat(ToolField.requiredEnum("verdict", "판정.", List.of("YES", "NO")).isObjectArray())
                        .isFalse()
        );
    }

    private JsonNode inputSchema(ToolConfiguration toolConfiguration) {
        return objectMapper.valueToTree(DocumentJsonConverter.toJavaObject(
                toolConfiguration.tools().get(0).toolSpec().inputSchema().json()));
    }

    private static List<String> fieldNames(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }

    private static List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asString()));
        return values;
    }
}
