package com.samhap.kokomen.global.fixture.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionResult;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisSchema;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

class ResumeAnalysisLlmResponseFixtureTest {

    @Test
    void 평가_ConverseResponse는_TOOL_USE와_평가_도구명을_가진다() {
        // when
        ConverseResponse response = ResumeAnalysisConverseResponseFixtureBuilder.builder()
                .buildEvaluation(true);

        // then — 차원 5개 × 4키 + total_feedback 1키 = requiredFieldCount(true) = 21
        assertThat(response.stopReason()).isEqualTo(StopReason.TOOL_USE);
        ToolUseBlock toolUse = response.output().message().content().get(0).toolUse();
        assertThat(toolUse.name()).isEqualTo(ResumeAnalysisToolNames.EVALUATION);
        assertThat(toolUse.input().asMap()).hasSize(ResumeAnalysisSchema.requiredFieldCount(true));
        assertThat(toolUse.input().asMap()).containsKeys("jd_fit_score", "jd_fit_reason", "jd_fit_improvements");
    }

    @Test
    void JD_미제공_평가_ConverseResponse에는_jd_fit_필드가_없다() {
        // when
        ConverseResponse response = ResumeAnalysisConverseResponseFixtureBuilder.builder()
                .buildEvaluation(false);

        // then
        Map<String, Document> input = response.output().message().content().get(0).toolUse().input().asMap();
        assertThat(input).hasSize(ResumeAnalysisSchema.requiredFieldCount(false));
        assertThat(input).doesNotContainKeys("jd_fit_reasoning", "jd_fit_score", "jd_fit_reason",
                "jd_fit_improvements");
        assertThat(input).containsKey("total_feedback");
    }

    @Test
    void 질문_ConverseResponse는_질문_5개를_담는다() {
        // when
        ConverseResponse response = ResumeAnalysisConverseResponseFixtureBuilder.builder()
                .buildQuestions();

        // then
        ToolUseBlock toolUse = response.output().message().content().get(0).toolUse();
        assertThat(toolUse.name()).isEqualTo(ResumeAnalysisToolNames.QUESTION_GENERATION);
        assertThat(toolUse.input().asMap().get("questions").asList()).hasSize(5);
    }

    @Test
    void 평가_값객체_픽스처는_JD_유무에_따라_총점이_다르다() {
        // when
        ResumeAnalysisEvaluation jdProvided = ResumeAnalysisEvaluationFixture.of(true);
        ResumeAnalysisEvaluation jdAbsent = ResumeAnalysisEvaluationFixture.of(false);

        // then — 90/80/70/60/50 × JD_PROVIDED = 74, 90/80/70/60 × JD_ABSENT = 78
        assertThat(jdProvided.jdFit()).isNotNull();
        assertThat(jdProvided.totalScore()).isEqualTo(74);
        assertThat(jdAbsent.jdFit()).isNull();
        assertThat(jdAbsent.totalScore()).isEqualTo(78);
    }

    @Test
    void 질문_결과_픽스처는_질문_5개를_순서대로_담는다() {
        // when
        ResumeAnalysisQuestionResult result = ResumeAnalysisQuestionResultFixture.five();

        // then
        assertThat(result.questions()).hasSize(5);
        assertThat(result.questions().get(0).question()).isEqualTo("질문 1");
        assertThat(result.questions().get(4).reason()).isEqualTo("이유 5");
    }
}
