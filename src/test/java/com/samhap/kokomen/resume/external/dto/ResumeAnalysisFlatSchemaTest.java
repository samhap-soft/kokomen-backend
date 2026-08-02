package com.samhap.kokomen.resume.external.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

/**
 * 이력서 분석 tool 스키마가 JD 유무에 따라 두 가지 필드 집합으로 갈리고, 중첩 object 없이 flat으로 구성되며,
 * Bedrock과 GPT가 완전히 같은 사양을 렌더하는지 검증한다.
 * ResumeAnalysisQuestionResult / ResumeAnalysisQuestionsFlatResponse는 이 테스트와 같은 패키지라 import하지 않는다.
 */
class ResumeAnalysisFlatSchemaTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void JD가_제공되면_Bedrock_평가_스키마의_required는_21개다() {
        Map<String, Object> schema = evaluationSchema(true);

        assertThat((List<?>) schema.get("required")).hasSize(21);
        assertThat(evaluationProperties(true)).hasSize(21);
        assertThat(ResumeAnalysisSchema.requiredFieldCount(true)).isEqualTo(21);
    }

    @Test
    void JD가_없으면_Bedrock_평가_스키마의_required는_17개다() {
        Map<String, Object> schema = evaluationSchema(false);

        assertThat((List<?>) schema.get("required")).hasSize(17);
        assertThat(evaluationProperties(false)).hasSize(17);
        assertThat(ResumeAnalysisSchema.requiredFieldCount(false)).isEqualTo(17);
    }

    @Test
    void JD가_없으면_properties에_jd_fit로_시작하는_키가_하나도_없다() {
        Map<String, Object> properties = evaluationProperties(false);

        assertThat(properties.keySet()).noneMatch(key -> key.startsWith("jd_fit"));
        assertThat(stringList(evaluationSchema(false).get("required")))
                .noneMatch(key -> key.startsWith("jd_fit"));
    }

    @Test
    void JD가_있으면_properties에_jd_fit_4개_필드가_존재한다() {
        Map<String, Object> properties = evaluationProperties(true);

        assertThat(properties).containsKey("jd_fit_reasoning")
                .containsKey("jd_fit_score")
                .containsKey("jd_fit_reason")
                .containsKey("jd_fit_improvements");
    }

    @Test
    void 평가_스키마는_JD_유무와_무관하게_중첩_object가_없다() {
        assertNoNestedObject(evaluationProperties(true));
        assertNoNestedObject(evaluationProperties(false));
        assertNoNestedObject(ResumeAnalysisEvaluationGptRequest.createEvaluationParams(true).properties());
        assertNoNestedObject(ResumeAnalysisEvaluationGptRequest.createEvaluationParams(false).properties());
    }

    @Test
    void GPT_평가_스키마도_jdProvided에_따라_required_개수가_같다() {
        GptFunctionParameters withJd = ResumeAnalysisEvaluationGptRequest.createEvaluationParams(true);
        GptFunctionParameters withoutJd = ResumeAnalysisEvaluationGptRequest.createEvaluationParams(false);

        assertThat(withJd.required()).hasSize(21);
        assertThat(withJd.properties()).hasSize(21);
        assertThat(withoutJd.required()).hasSize(17);
        assertThat(withoutJd.properties()).hasSize(17);
    }

    @Test
    void 평가_스키마의_required_집합은_Bedrock과_GPT가_완전히_동일하다() {
        for (boolean jdProvided : List.of(true, false)) {
            List<String> bedrockRequired = stringList(evaluationSchema(jdProvided).get("required"));
            GptFunctionParameters gptParams = ResumeAnalysisEvaluationGptRequest.createEvaluationParams(jdProvided);

            assertThat(bedrockRequired)
                    .as("jdProvided=%s의 required 순서·집합", jdProvided)
                    .containsExactlyElementsOf(gptParams.required());
            assertThat(evaluationProperties(jdProvided).keySet())
                    .as("jdProvided=%s의 properties 키 집합", jdProvided)
                    .containsExactlyInAnyOrderElementsOf(gptParams.properties().keySet());
        }
    }

    @Test
    void 점수_필드는_integer이고_최소0_최대100이다() {
        Map<String, Object> scoreField = field(evaluationProperties(true), "problem_solving_score");

        assertThat(scoreField.get("type")).isEqualTo("integer");
        assertThat(((BigDecimal) scoreField.get("minimum")).intValue()).isZero();
        assertThat(((BigDecimal) scoreField.get("maximum")).intValue()).isEqualTo(100);

        Map<String, Object> gptScoreField = castMap(
                ResumeAnalysisEvaluationGptRequest.createEvaluationParams(true).properties()
                        .get("problem_solving_score"));
        assertThat(gptScoreField.get("type")).isEqualTo("integer");
        assertThat(gptScoreField.get("minimum")).isEqualTo(0);
        assertThat(gptScoreField.get("maximum")).isEqualTo(100);
    }

    @Test
    void 근거_배열은_최소2개_최대6개다() {
        Map<String, Object> reasonField = field(evaluationProperties(true), "problem_solving_reason");

        assertThat(reasonField.get("type")).isEqualTo("array");
        assertThat(((BigDecimal) reasonField.get("minItems")).intValue()).isEqualTo(2);
        assertThat(((BigDecimal) reasonField.get("maxItems")).intValue()).isEqualTo(6);

        Map<String, Object> gptReasonField = castMap(
                ResumeAnalysisEvaluationGptRequest.createEvaluationParams(true).properties()
                        .get("problem_solving_improvements"));
        assertThat(gptReasonField.get("minItems")).isEqualTo(2);
        assertThat(gptReasonField.get("maxItems")).isEqualTo(6);
    }

    @Test
    void 질문_스키마는_최소5개_최대7개의_배열이다() {
        Map<String, Object> questions = bedrockQuestionsField();

        assertThat(questions.get("type")).isEqualTo("array");
        assertThat(((BigDecimal) questions.get("minItems")).intValue()).isEqualTo(5);
        assertThat(((BigDecimal) questions.get("maxItems")).intValue()).isEqualTo(7);
    }

    @Test
    void 질문_스키마의_minItems와_maxItems는_Bedrock과_GPT가_같다() {
        Map<String, Object> bedrockQuestions = bedrockQuestionsField();
        Map<String, Object> gptQuestions = castMap(
                ResumeAnalysisQuestionGptRequest.createQuestionParams().properties().get("questions"));

        assertThat(((BigDecimal) bedrockQuestions.get("minItems")).intValue())
                .isEqualTo((Integer) gptQuestions.get("minItems"));
        assertThat(((BigDecimal) bedrockQuestions.get("maxItems")).intValue())
                .isEqualTo((Integer) gptQuestions.get("maxItems"));
    }

    @Test
    void 질문과_이유_필드에는_maxLength가_설정되어_있다() {
        Map<String, Object> bedrockItemProperties = castMap(castMap(bedrockQuestionsField().get("items"))
                .get("properties"));

        assertThat(((BigDecimal) castMap(bedrockItemProperties.get("question")).get("maxLength")).intValue())
                .isEqualTo(300);
        assertThat(((BigDecimal) castMap(bedrockItemProperties.get("reason")).get("maxLength")).intValue())
                .isEqualTo(600);

        Map<String, Object> gptItemProperties = castMap(castMap(
                castMap(ResumeAnalysisQuestionGptRequest.createQuestionParams().properties().get("questions"))
                        .get("items")).get("properties"));
        assertThat(castMap(gptItemProperties.get("question")).get("maxLength")).isEqualTo(300);
        assertThat(castMap(gptItemProperties.get("reason")).get("maxLength")).isEqualTo(600);
    }

    @Test
    void 도구_이름은_평가와_질문이_서로_다르다() {
        assertThat(ResumeAnalysisToolNames.EVALUATION)
                .isEqualTo("submit_resume_analysis_evaluation");
        assertThat(ResumeAnalysisToolNames.QUESTION_GENERATION)
                .isEqualTo("submit_resume_analysis_questions")
                .isNotEqualTo(ResumeAnalysisToolNames.EVALUATION);
    }

    @Test
    void 구지표_이름은_신규_스키마에_존재하지_않는다() {
        for (boolean jdProvided : List.of(true, false)) {
            assertThat(evaluationProperties(jdProvided).keySet())
                    .as("jdProvided=%s", jdProvided)
                    .noneMatch(key -> key.startsWith("career_growth"))
                    .noneMatch(key -> key.startsWith("documentation"));
            assertThat(ResumeAnalysisEvaluationGptRequest.createEvaluationParams(jdProvided).properties().keySet())
                    .noneMatch(key -> key.startsWith("career_growth"))
                    .noneMatch(key -> key.startsWith("documentation"));
        }
    }

    @Test
    void JD포함_flat_응답은_5지표로_매핑되고_종합점수는_JD포함_가중치로_계산된다() throws Exception {
        ResumeAnalysisEvaluation evaluation = objectMapper
                .readValue(flatEvaluationJson(true), ResumeAnalysisEvaluationFlatResponse.class)
                .toEvaluation(true);

        assertThat(evaluation.problemSolving().score()).isEqualTo(90);
        assertThat(evaluation.problemSolving().reason()).containsExactly("근거1", "근거2");
        assertThat(evaluation.projectExperience().score()).isEqualTo(80);
        assertThat(evaluation.technicalSkills().score()).isEqualTo(70);
        assertThat(evaluation.softSkills().score()).isEqualTo(60);
        assertThat(evaluation.jdFit().score()).isEqualTo(50);
        assertThat(evaluation.totalFeedback()).isEqualTo("종합 총평");
        // 90*0.25 + 80*0.25 + 70*0.25 + 60*0.10 + 50*0.15 = 73.5 → 74
        assertThat(evaluation.totalScore()).isEqualTo(74);
    }

    @Test
    void JD미포함_flat_응답은_4지표로_매핑되고_JD적합성은_null이다() throws Exception {
        ResumeAnalysisEvaluation evaluation = objectMapper
                .readValue(flatEvaluationJson(false), ResumeAnalysisEvaluationFlatResponse.class)
                .toEvaluation(false);

        assertThat(evaluation.jdFit()).isNull();
        assertThat(evaluation.softSkills().score()).isEqualTo(60);
        // 90*0.30 + 80*0.30 + 70*0.30 + 60*0.10 = 78
        assertThat(evaluation.totalScore()).isEqualTo(78);
    }

    @Test
    void reasoning_필드는_무시된다() throws Exception {
        String json = """
                {
                  "problem_solving_reasoning": "무시되는 CoT",
                  "problem_solving_score": 90,
                  "problem_solving_reason": ["근거1", "근거2"],
                  "problem_solving_improvements": ["보완1", "보완2"],
                  "project_experience_reasoning": "무시",
                  "project_experience_score": 80,
                  "project_experience_reason": ["근거1", "근거2"],
                  "project_experience_improvements": ["보완1", "보완2"],
                  "technical_skills_reasoning": "무시",
                  "technical_skills_score": 70,
                  "technical_skills_reason": ["근거1", "근거2"],
                  "technical_skills_improvements": ["보완1", "보완2"],
                  "soft_skills_reasoning": "무시",
                  "soft_skills_score": 60,
                  "soft_skills_reason": ["근거1", "근거2"],
                  "soft_skills_improvements": ["보완1", "보완2"],
                  "total_feedback": "종합 총평",
                  "unknown_extra_field": "무시"
                }
                """;

        ResumeAnalysisEvaluation evaluation = objectMapper
                .readValue(json, ResumeAnalysisEvaluationFlatResponse.class)
                .toEvaluation(false);

        assertThat(evaluation.totalScore()).isEqualTo(78);
    }

    /**
     * 도구 스키마의 required는 모델에 대한 지시일 뿐 서버가 강제하는 계약이 아니므로, 구조적으로 유효한 JSON이
     * _score 필드를 누락할 수 있다. DimensionScore.score가 primitive int라 그대로 두면 언박싱 NPE가 나므로
     * toEvaluation이 ExternalApiException으로 통일한다. 이 테스트는 그 가드를 되돌리면 실패한다
     * (가드가 없으면 NullPointerException이 던져져 isInstanceOf(ExternalApiException) 단정이 깨진다).
     */
    @Test
    void 점수_필드가_누락된_응답은_NPE가_아니라_ExternalApiException으로_변환된다() throws Exception {
        String json = """
                {
                  "problem_solving_reason": ["근거1", "근거2"],
                  "problem_solving_improvements": ["보완1", "보완2"],
                  "project_experience_score": 80,
                  "project_experience_reason": ["근거1", "근거2"],
                  "project_experience_improvements": ["보완1", "보완2"],
                  "technical_skills_score": 70,
                  "technical_skills_reason": ["근거1", "근거2"],
                  "technical_skills_improvements": ["보완1", "보완2"],
                  "soft_skills_score": 60,
                  "soft_skills_reason": ["근거1", "근거2"],
                  "soft_skills_improvements": ["보완1", "보완2"],
                  "total_feedback": "종합 총평"
                }
                """;
        ResumeAnalysisEvaluationFlatResponse response = objectMapper
                .readValue(json, ResumeAnalysisEvaluationFlatResponse.class);

        assertThatThrownBy(() -> response.toEvaluation(false))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdProvided=false");
    }

    /**
     * 두 provider가 동일하게 행동하는 근거: Bedrock 클라이언트는 parseToolInput 후, GPT 클라이언트는
     * readValue 후 각각 같은 toEvaluation을 호출한다. 응답 변형 방어가 그 메서드 한 곳에 있으므로
     * provider별로 갈리지 않는다. GPT 경로는 ExternalApiException을 재던져 이 구체적 메시지를 보존한다.
     */
    @Test
    void JD가_제공됐는데_JD적합성_점수가_누락되면_ExternalApiException이_발생한다() throws Exception {
        ResumeAnalysisEvaluationFlatResponse response = objectMapper
                .readValue(flatEvaluationJson(false), ResumeAnalysisEvaluationFlatResponse.class);

        assertThatThrownBy(() -> response.toEvaluation(true))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(NullPointerException.class);
    }

    @Test
    void 질문_flat_응답은_질문과_이유_쌍으로_매핑된다() throws Exception {
        String json = """
                {
                  "questions": [
                    {"question": "질문 1", "reason": "이유 1"},
                    {"question": "질문 2", "reason": "이유 2"},
                    {"question": "질문 3", "reason": "이유 3"},
                    {"question": "질문 4", "reason": "이유 4"},
                    {"question": "질문 5", "reason": "이유 5"}
                  ]
                }
                """;

        ResumeAnalysisQuestionResult result = objectMapper
                .readValue(json, ResumeAnalysisQuestionsFlatResponse.class)
                .toResult();

        assertThat(result.questions()).hasSize(5);
        assertThat(result.questions().get(0).question()).isEqualTo("질문 1");
        assertThat(result.questions().get(4).reason()).isEqualTo("이유 5");
    }

    private String flatEvaluationJson(boolean jdProvided) {
        String base = """
                {
                  "problem_solving_reasoning": "사고 과정",
                  "problem_solving_score": 90,
                  "problem_solving_reason": ["근거1", "근거2"],
                  "problem_solving_improvements": ["보완1", "보완2"],
                  "project_experience_reasoning": "사고 과정",
                  "project_experience_score": 80,
                  "project_experience_reason": ["근거1", "근거2"],
                  "project_experience_improvements": ["보완1", "보완2"],
                  "technical_skills_reasoning": "사고 과정",
                  "technical_skills_score": 70,
                  "technical_skills_reason": ["근거1", "근거2"],
                  "technical_skills_improvements": ["보완1", "보완2"],
                  "soft_skills_reasoning": "사고 과정",
                  "soft_skills_score": 60,
                  "soft_skills_reason": ["근거1", "근거2"],
                  "soft_skills_improvements": ["보완1", "보완2"],
                """;
        String jdFit = """
                  "jd_fit_reasoning": "사고 과정",
                  "jd_fit_score": 50,
                  "jd_fit_reason": ["근거1", "근거2"],
                  "jd_fit_improvements": ["보완1", "보완2"],
                """;
        return base + (jdProvided ? jdFit : "") + """
                  "total_feedback": "종합 총평"
                }
                """;
    }

    private Map<String, Object> evaluationSchema(boolean jdProvided) {
        ToolConfiguration config = ResumeAnalysisBedrockRequestFactory.createEvaluationToolConfig(jdProvided);
        return castMap(DocumentJsonConverter.toJavaObject(
                config.tools().get(0).toolSpec().inputSchema().json()));
    }

    private Map<String, Object> evaluationProperties(boolean jdProvided) {
        return castMap(evaluationSchema(jdProvided).get("properties"));
    }

    private Map<String, Object> bedrockQuestionsField() {
        ToolConfiguration config = ResumeAnalysisBedrockRequestFactory.createQuestionGenerationToolConfig();
        Map<String, Object> schema = castMap(DocumentJsonConverter.toJavaObject(
                config.tools().get(0).toolSpec().inputSchema().json()));
        return castMap(castMap(schema.get("properties")).get("questions"));
    }

    private Map<String, Object> field(Map<String, Object> properties, String key) {
        return castMap(properties.get(key));
    }

    private List<String> stringList(Object required) {
        return ((List<?>) required).stream()
                .map(String::valueOf)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private void assertNoNestedObject(Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> field = castMap(entry.getValue());
            assertThat(field.get("type"))
                    .as("필드 '%s'는 중첩 object가 아니어야 한다", entry.getKey())
                    .isNotEqualTo("object");
        }
    }
}
