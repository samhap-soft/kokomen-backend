package com.samhap.kokomen.resume.external.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.interview.external.dto.request.GptFunctionParameters;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

/**
 * 이력서 평가 tool 스키마가 중첩 object 없이 flat 필드로 구성되는지(중첩 XML 누수 방지),
 * 그리고 flat 와이어 응답이 기존 도메인 모델로 올바르게 매핑되는지 검증한다.
 */
class ResumeEvaluationFlatSchemaTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void Bedrock_평가_스키마는_중첩_object_없이_flat_필드로_구성된다() {
        ToolConfiguration config = ResumeBedrockRequestFactory.createEvaluationToolConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) DocumentJsonConverter.toJavaObject(
                config.tools().get(0).toolSpec().inputSchema().json());
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) json.get("properties");

        assertThat(properties).containsKey("technical_skills_score")
                .containsKey("technical_skills_reason")
                .containsKey("documentation_improvements")
                .containsKey("total_feedback")
                .doesNotContainKey("technical_skills");
        assertNoNestedObject(properties);
        assertThat((List<?>) json.get("required")).hasSize(21);
    }

    @Test
    void GPT_평가_스키마도_flat_필드로_구성된다() {
        ResumeEvaluationRequest request = new ResumeEvaluationRequest("이력서", "포폴", "백엔드", "공고", "3년차");
        GptFunctionParameters params = ResumeGptRequest.create(request, 0.2).tools().get(0).function().parameters();

        assertThat(params.properties()).containsKey("technical_skills_score")
                .containsKey("problem_solving_reason")
                .containsKey("total_feedback")
                .doesNotContainKey("technical_skills");
        assertNoNestedObject(params.properties());
        assertThat(params.required()).hasSize(21);
    }

    @Test
    void flat_와이어_응답은_기존_중첩_도메인모델로_매핑되고_종합점수는_가중평균으로_계산된다() throws Exception {
        String flatJson = """
                {
                  "technical_skills_reasoning": "무시되는 CoT",
                  "technical_skills_score": 90,
                  "technical_skills_reason": ["근거1", "근거2"],
                  "technical_skills_improvements": ["보완1"],
                  "project_experience_reasoning": "무시",
                  "project_experience_score": 80,
                  "project_experience_reason": ["근거"],
                  "project_experience_improvements": ["보완"],
                  "problem_solving_reasoning": "무시",
                  "problem_solving_score": 70,
                  "problem_solving_reason": ["근거"],
                  "problem_solving_improvements": ["보완"],
                  "career_growth_reasoning": "무시",
                  "career_growth_score": 60,
                  "career_growth_reason": ["근거"],
                  "career_growth_improvements": ["보완"],
                  "documentation_reasoning": "무시",
                  "documentation_score": 50,
                  "documentation_reason": ["근거"],
                  "documentation_improvements": ["보완"],
                  "total_feedback": "종합 총평"
                }
                """;

        // toLlmResponse()가 종합 점수(가중평균)까지 계산해 반환하므로 별도 호출이 필요 없다.
        ResumeEvaluationLlmResponse llm = objectMapper.readValue(flatJson, ResumeEvaluationFlatResponse.class)
                .toLlmResponse();

        assertThat(llm.technicalSkills().score()).isEqualTo(90);
        assertThat(llm.technicalSkills().reason()).containsExactly("근거1", "근거2");
        assertThat(llm.documentation().score()).isEqualTo(50);
        assertThat(llm.totalFeedback()).isEqualTo("종합 총평");
        // 90*0.30 + 80*0.25 + 70*0.20 + 60*0.15 + 50*0.10 = 75
        assertThat(llm.totalScore()).isEqualTo(75);
    }

    @SuppressWarnings("unchecked")
    private void assertNoNestedObject(Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> field = (Map<String, Object>) entry.getValue();
            assertThat(field.get("type"))
                    .as("필드 '%s'는 중첩 object가 아니어야 한다", entry.getKey())
                    .isNotEqualTo("object");
        }
    }
}
