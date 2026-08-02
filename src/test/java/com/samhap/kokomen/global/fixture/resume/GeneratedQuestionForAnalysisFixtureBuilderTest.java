package com.samhap.kokomen.global.fixture.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedQuestionForAnalysisFixtureBuilderTest {

    @Test
    void five는_질문_5개를_문항_순서_0부터_4까지_생성한다() {
        // given
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder().build();

        // when
        List<GeneratedQuestion> questions = GeneratedQuestionForAnalysisFixtureBuilder.five(analysis);

        // then
        assertThat(questions).hasSize(5);
        assertThat(questions).extracting(GeneratedQuestion::getQuestionOrder)
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void five가_생성한_질문의_본문과_사유는_문항_순서를_포함한다() {
        // given
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder().build();

        // when
        List<GeneratedQuestion> questions = GeneratedQuestionForAnalysisFixtureBuilder.five(analysis);

        // then
        assertThat(questions.get(2).getContent()).isEqualTo("이력서 기반 질문 2");
        assertThat(questions.get(2).getReason()).isEqualTo("질문 선정 이유 2");
    }

    @Test
    void build은_analysis를_그대로_연관시키고_문항_순서_기본값은_0이다() {
        // given
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder().build();

        // when
        GeneratedQuestion question = GeneratedQuestionForAnalysisFixtureBuilder.builder()
                .analysis(analysis)
                .build();

        // then
        assertThat(question.getAnalysis()).isSameAs(analysis);
        assertThat(question.getQuestionOrder()).isEqualTo(0);
    }

    @Test
    void content와_reason과_questionOrder를_지정하면_기본값을_덮어쓴다() {
        // given
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder().build();

        // when
        GeneratedQuestion question = GeneratedQuestionForAnalysisFixtureBuilder.builder()
                .analysis(analysis)
                .content("커스텀 질문")
                .reason("커스텀 이유")
                .questionOrder(9)
                .build();

        // then
        assertThat(question.getContent()).isEqualTo("커스텀 질문");
        assertThat(question.getReason()).isEqualTo("커스텀 이유");
        assertThat(question.getQuestionOrder()).isEqualTo(9);
    }
}
