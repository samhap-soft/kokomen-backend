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
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
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
    // 답변 평가 도구의 정본. 필드 순서는 reasoning → rank → feedback → next_question이다.
    private static final String ANSWER_TOOL = """
            {
              "name": "submit_system_design_answer",
              "description": "시스템 설계 면접 답변의 rank와 피드백, 다음 질문을 제출한다.",
              "inputSchema": {
                "json": {
                  "type": "object",
                  "properties": {
                    "reasoning": {
                      "type": "string",
                      "description": "답변 평가 근거(answer_analysis)와 다음 질문 설계 과정(question_planning). 700자 이내. \
            사용자에게 노출되지 않는다."
                    },
                    "rank": {
                      "type": "string",
                      "description": "가장 최근 답변에 대한 평가 등급. A, B, C, D, F 중 한 글자.",
                      "enum": ["A", "B", "C", "D", "F"]
                    },
                    "feedback": {
                      "type": "string",
                      "description": "가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다."
                    },
                    "next_question": {
                      "type": "string",
                      "description": "next_question_rules를 지켜 기준 질문을 바탕으로 만든 다음 질문. 한두 문장, 200자 이내."
                    }
                  },
                  "required": ["reasoning", "rank", "feedback", "next_question"]
                }
              }
            }
            """;
    // 증거 필드의 설명은 평가 포인트마다 같다.
    private static final String EVIDENCE_DESCRIPTION = "이 평가 포인트 설명의 요소마다 한 줄씩: "
            + "`[답변 번호] \"답변이나 다이어그램 줄의 원문 그대로 짧은 인용\"`. 확인되지 않은 요소는 `없음: 요소 이름`. 300자 이내.";
    // URL 단축기 문제로 그린 마지막 답변 평가 도구의 평가 포인트 판정 필드. feedback 뒤, 종합 평가 세 필드 앞에 문제의 평가 포인트
    // 순서로 붙고, 포인트마다 증거 필드가 판정 필드 바로 앞에 온다(END_FIELD_ORDER).
    private static final String URL_SHORTENER_POINT_FIELDS = """
            {
              "point_read_path_cache": {
                "type": "string",
                "description": "조회가 생성보다 훨씬 많다는 점을 근거로 리다이렉트 경로에 캐시를 두었다",
                "enum": ["MET", "NOT_MET"]
              },
              "point_key_generation_collision": {
                "type": "string",
                "description": "단축 키 생성 방식(카운터 인코딩·해시 등)과 키 충돌을 막거나 처리하는 방법을 설명했다",
                "enum": ["MET", "NOT_MET"]
              },
              "point_storage_estimation": {
                "type": "string",
                "description": "생성량·보관 기간·건당 크기로 전체 저장 용량을 추정하고 저장소 선택에 반영했다",
                "enum": ["MET", "NOT_MET"]
              },
              "point_redirect_availability": {
                "type": "string",
                "description": "DB나 캐시에 장애가 나도 이미 보낸 링크가 계속 열리도록 대비책(복제·장애 전환 등)을 설명했다",
                "enum": ["MET", "NOT_MET"]
              },
              "point_redirect_status_tradeoff": {
                "type": "string",
                "description": "301과 302 리다이렉트의 장단점을 비교하고 근거를 들어 하나를 골랐다",
                "enum": ["MET", "NOT_MET"]
              }
            }
            """;
    private static final List<String> URL_SHORTENER_EVIDENCE_FIELDS = List.of("point_read_path_cache_evidence",
            "point_key_generation_collision_evidence", "point_storage_estimation_evidence",
            "point_redirect_availability_evidence", "point_redirect_status_tradeoff_evidence");
    private static final List<String> END_FIELD_ORDER = List.of("reasoning", "rank", "feedback",
            "point_read_path_cache_evidence", "point_read_path_cache",
            "point_key_generation_collision_evidence", "point_key_generation_collision",
            "point_storage_estimation_evidence", "point_storage_estimation",
            "point_redirect_availability_evidence", "point_redirect_availability",
            "point_redirect_status_tradeoff_evidence", "point_redirect_status_tradeoff",
            "strengths", "improvements", "learning_direction");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 요구사항_도구는_요구사항마다_YES_NO_필드를_문제_순서로_만든다() throws Exception {
        // given
        List<Requirement> requirements = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS;

        // when
        ToolConfiguration toolConfiguration = BedrockToolSchemaRenderer.render(
                SystemDesignToolSchemas.requirementAnswer(requirements));

        // then
        JsonNode rendered = toJson(toolConfiguration);
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

    @Test
    void 답변_도구는_rank_enum과_feedback_next_question을_만든다() throws Exception {
        // when
        ToolConfiguration toolConfiguration = BedrockToolSchemaRenderer.render(SystemDesignToolSchemas.answer());

        // then
        JsonNode rendered = toJson(toolConfiguration);
        assertAll(
                () -> assertThat(rendered).isEqualTo(objectMapper.readTree(ANSWER_TOOL)),
                () -> assertThat(fieldNames(rendered.at("/inputSchema/json/properties")))
                        .containsExactly("reasoning", "rank", "feedback", "next_question"),
                () -> assertThat(textValues(rendered.at("/inputSchema/json/required")))
                        .containsExactly("reasoning", "rank", "feedback", "next_question"),
                () -> assertThat(toolConfiguration.tools()).hasSize(1),
                () -> assertThat(toolConfiguration.toolChoice().tool().name())
                        .isEqualTo("submit_system_design_answer")
        );
    }

    @Test
    void end_도구는_평가_포인트마다_증거_필드와_MET_NOT_MET_판정_필드를_만든다() throws Exception {
        // given
        List<EvaluationPoint> points = SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS;

        // when
        ToolConfiguration toolConfiguration = BedrockToolSchemaRenderer.render(SystemDesignToolSchemas.end(points));

        // then
        JsonNode rendered = toJson(toolConfiguration);
        JsonNode properties = rendered.at("/inputSchema/json/properties");
        JsonNode expectedPointFields = objectMapper.readTree(URL_SHORTENER_POINT_FIELDS);
        assertAll(
                () -> assertThat(rendered.get("name").asText()).isEqualTo("submit_system_design_end"),
                () -> assertThat(fieldNames(properties)).containsExactlyElementsOf(END_FIELD_ORDER),
                () -> assertThat(textValues(rendered.at("/inputSchema/json/required")))
                        .containsExactlyElementsOf(END_FIELD_ORDER),
                () -> assertThat(fieldNames(expectedPointFields)).allSatisfy(field ->
                        assertThat(properties.get(field)).isEqualTo(expectedPointFields.get(field))),
                () -> assertThat(URL_SHORTENER_EVIDENCE_FIELDS).allSatisfy(field -> assertAll(
                        () -> assertThat(properties.at("/" + field + "/type").asText()).isEqualTo("string"),
                        () -> assertThat(properties.at("/" + field + "/description").asText())
                                .isEqualTo(EVIDENCE_DESCRIPTION),
                        () -> assertThat(properties.get(field).has("enum")).isFalse()
                )),
                () -> assertThat(textValues(properties.at("/rank/enum"))).containsExactly("A", "B", "C", "D", "F"),
                () -> assertThat(List.of("reasoning", "feedback", "strengths", "improvements", "learning_direction"))
                        .allSatisfy(field -> assertAll(
                                () -> assertThat(properties.at("/" + field + "/type").asText()).isEqualTo("string"),
                                () -> assertThat(properties.at("/" + field + "/description").asText()).isNotBlank(),
                                () -> assertThat(properties.get(field).has("enum")).isFalse()
                        )),
                () -> assertThat(toolConfiguration.tools()).hasSize(1),
                () -> assertThat(toolConfiguration.toolChoice().tool().name()).isEqualTo("submit_system_design_end")
        );
    }

    // reasoning 글자 수 한도가 출력 길이를 묶는다. 마지막 답변 평가(L3)는 평가 포인트별 근거를 증거 필드에 쓰므로 reasoning에서
    // 뺐고, 그만큼 한도를 줄였다.
    @Test
    void reasoning_설명에_700자와_400자_한도가_있다() {
        ToolSchema answer = SystemDesignToolSchemas.answer();
        ToolSchema end = SystemDesignToolSchemas.end(SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS);

        assertAll(
                () -> assertThat(answer.fields().get(0).name()).isEqualTo("reasoning"),
                () -> assertThat(answer.fields().get(0).description()).contains("700자 이내"),
                () -> assertThat(end.fields().get(0))
                        .extracting(ToolField::name, ToolField::description)
                        .containsExactly("reasoning", "가장 최근 답변의 항목별 점수 근거와 종합 평가 정리. 400자 이내. "
                                + "사용자에게 노출되지 않는다.")
        );
    }

    @Test
    void 답변_도구와_end_도구의_모든_필드가_required다() {
        ToolSchema answer = SystemDesignToolSchemas.answer();
        ToolSchema end = SystemDesignToolSchemas.end(List.of(
                new EvaluationPoint("BURST_ABSORPTION", "요청이 몰릴 때 큐로 부하를 흡수했다"),
                new EvaluationPoint("DUPLICATE_ISSUE", "같은 사람이 두 번 받지 않게 막았다")));

        assertAll(
                () -> assertThat(answer.requiredFields()).isEqualTo(answer.fields()),
                () -> assertThat(end.fields()).extracting(ToolField::name).containsExactly("reasoning", "rank",
                        "feedback", "point_burst_absorption_evidence", "point_burst_absorption",
                        "point_duplicate_issue_evidence", "point_duplicate_issue", "strengths", "improvements",
                        "learning_direction"),
                () -> assertThat(end.requiredFields()).isEqualTo(end.fields())
        );
    }

    // 기본 로캘이 터키어여도 I가 점 없는 ı로 바뀌지 않아야 응답 파서가 같은 필드 이름을 찾는다.
    @Test
    void 평가_포인트_판정_필드와_증거_필드_이름은_code를_소문자로_바꿔_만든다() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertAll(
                    () -> assertThat(SystemDesignToolSchemas.pointFieldName("KEY_GENERATION_COLLISION"))
                            .isEqualTo("point_key_generation_collision"),
                    () -> assertThat(SystemDesignToolSchemas.pointFieldName("P2P_LIMIT")).isEqualTo("point_p2p_limit"),
                    () -> assertThat(SystemDesignToolSchemas.pointEvidenceFieldName("KEY_GENERATION_COLLISION"))
                            .isEqualTo("point_key_generation_collision_evidence")
            );
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private JsonNode toJson(ToolConfiguration toolConfiguration) {
        ToolSpecification toolSpec = toolConfiguration.tools().get(0).toolSpec();
        return objectMapper.valueToTree(Map.of(
                "name", toolSpec.name(),
                "description", toolSpec.description(),
                "inputSchema", Map.of("json", DocumentJsonConverter.toJavaObject(toolSpec.inputSchema().json()))));
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
