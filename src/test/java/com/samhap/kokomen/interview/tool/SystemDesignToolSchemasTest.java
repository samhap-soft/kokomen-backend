package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.external.bedrock.BedrockToolSchemaRenderer;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

class SystemDesignToolSchemasTest {

    // URL 단축기 문제로 그린 역질문 답 도구의 정본. inputSchema.json 최상위 키의 순서는 렌더러가 정하지 않으므로
    // 전체는 구조로 비교하고, 순서가 뜻을 갖는 properties와 required는 따로 순서를 본다.
    private static final String URL_SHORTENER_TOOL = """
            {
              "name": "submit_requirement_answer",
              "description": "면접자의 요구사항 역질문이 숨은 요구사항의 각 항목을 묻는지 판정하고 면접관의 답을 제출한다.",
              "inputSchema": {
                "json": {
                  "type": "object",
                  "properties": {
                    "reasoning": {
                      "type": "string",
                      "description": "역질문이 묻는 내용과 항목별 판정 근거. 200자 이내. 사용자에게 노출되지 않는다."
                    },
                    "requirement_daily_creation_volume": {
                      "type": "string",
                      "description": "역질문이 '하루 생성량' 항목을 묻는지. 물으면 YES, 아니면 NO.",
                      "enum": ["YES", "NO"]
                    },
                    "requirement_read_write_ratio": {
                      "type": "string",
                      "description": "역질문이 '조회와 생성 비율' 항목을 묻는지. 물으면 YES, 아니면 NO.",
                      "enum": ["YES", "NO"]
                    },
                    "requirement_retention_period": {
                      "type": "string",
                      "description": "역질문이 '보관 기간' 항목을 묻는지. 물으면 YES, 아니면 NO.",
                      "enum": ["YES", "NO"]
                    },
                    "requirement_short_key_length": {
                      "type": "string",
                      "description": "역질문이 '단축 키 길이' 항목을 묻는지. 물으면 YES, 아니면 NO.",
                      "enum": ["YES", "NO"]
                    },
                    "requirement_click_statistics": {
                      "type": "string",
                      "description": "역질문이 '클릭 통계' 항목을 묻는지. 물으면 YES, 아니면 NO.",
                      "enum": ["YES", "NO"]
                    },
                    "answer": {
                      "type": "string",
                      "description": "면접관의 답. 해요체 1~3문장, 300자 이내. YES인 항목이 없으면 정해진 문구."
                    }
                  },
                  "required": [
                    "reasoning",
                    "requirement_daily_creation_volume",
                    "requirement_read_write_ratio",
                    "requirement_retention_period",
                    "requirement_short_key_length",
                    "requirement_click_statistics",
                    "answer"
                  ]
                }
              }
            }
            """;
    private static final List<String> FIELD_ORDER = List.of("reasoning", "requirement_daily_creation_volume",
            "requirement_read_write_ratio", "requirement_retention_period", "requirement_short_key_length",
            "requirement_click_statistics", "answer");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 요구사항_도구는_요구사항마다_YES_NO_필드를_문제_순서로_만든다() throws Exception {
        // given
        List<Requirement> requirements = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS;

        // when
        ToolConfiguration toolConfiguration = BedrockToolSchemaRenderer.render(
                SystemDesignToolSchemas.requirementAnswer(requirements));

        // then
        ToolSpecification toolSpec = toolConfiguration.tools().get(0).toolSpec();
        JsonNode rendered = objectMapper.valueToTree(Map.of(
                "name", toolSpec.name(),
                "description", toolSpec.description(),
                "inputSchema", Map.of("json", DocumentJsonConverter.toJavaObject(toolSpec.inputSchema().json()))));
        assertAll(
                () -> assertThat(rendered).isEqualTo(objectMapper.readTree(URL_SHORTENER_TOOL)),
                () -> assertThat(fieldNames(rendered.at("/inputSchema/json/properties")))
                        .containsExactlyElementsOf(FIELD_ORDER),
                () -> assertThat(textValues(rendered.at("/inputSchema/json/required")))
                        .containsExactlyElementsOf(FIELD_ORDER),
                () -> assertThat(toolConfiguration.tools()).hasSize(1),
                () -> assertThat(toolConfiguration.toolChoice().tool().name()).isEqualTo("submit_requirement_answer")
        );
    }

    // 기본 로캘이 터키어여도 I가 점 없는 ı로 바뀌지 않아야 응답 파서가 같은 필드 이름을 찾는다.
    @Test
    void 요구사항_필드_이름은_code를_소문자로_바꿔_requirement_를_붙인다() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertAll(
                    () -> assertThat(SystemDesignToolSchemas.requirementFieldName("CLICK_STATISTICS"))
                            .isEqualTo("requirement_click_statistics"),
                    () -> assertThat(SystemDesignToolSchemas.requirementFieldName("P2P_LIMIT"))
                            .isEqualTo("requirement_p2p_limit")
            );
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void reasoning_설명에_200자_한도가_있다() {
        ToolSchema schema = SystemDesignToolSchemas.requirementAnswer(
                SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS);

        assertThat(schema.fields().get(0))
                .extracting(ToolField::name, ToolField::description)
                .containsExactly("reasoning", "역질문이 묻는 내용과 항목별 판정 근거. 200자 이내. 사용자에게 노출되지 않는다.");
    }

    // 스키마의 required는 모델에 대한 지시일 뿐이라 서버가 응답을 다시 검사한다. 그래도 모든 필드를 요구해야 모델이 항목마다 판정을 낸다.
    @Test
    void 모든_필드가_required다() {
        List<Requirement> requirements = List.of(
                new Requirement("PEAK_TRAFFIC", "순간 트래픽 규모", "10초 안에 약 30만 명이 발급을 시도해요."),
                new Requirement("COUPON_STOCK", "쿠폰 수량", "쿠폰은 이벤트마다 5만 장을 준비해요."));

        ToolSchema schema = SystemDesignToolSchemas.requirementAnswer(requirements);

        assertAll(
                () -> assertThat(schema.fields()).extracting(ToolField::name)
                        .containsExactly("reasoning", "requirement_peak_traffic", "requirement_coupon_stock", "answer"),
                () -> assertThat(schema.fields()).allMatch(ToolField::required),
                () -> assertThat(schema.requiredFields()).isEqualTo(schema.fields())
        );
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }
}
