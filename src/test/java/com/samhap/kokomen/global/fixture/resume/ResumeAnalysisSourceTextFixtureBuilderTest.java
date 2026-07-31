package com.samhap.kokomen.global.fixture.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import org.junit.jupiter.api.Test;

class ResumeAnalysisSourceTextFixtureBuilderTest {

    @Test
    void 기본값은_analysis가_없고_이력서_원문_기본_텍스트를_담고_포트폴리오_원문은_없다() {
        // when
        ResumeAnalysisSourceText sourceText = ResumeAnalysisSourceTextFixtureBuilder.builder().build();

        // then
        assertThat(sourceText.getAnalysis()).isNull();
        assertThat(sourceText.getResumeContent()).isEqualTo("이력서 원문 텍스트입니다.");
        assertThat(sourceText.getPortfolioContent()).isNull();
    }

    @Test
    void analysis와_resumeContent와_portfolioContent를_지정하면_기본값을_덮어쓴다() {
        // given
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder().build();

        // when
        ResumeAnalysisSourceText sourceText = ResumeAnalysisSourceTextFixtureBuilder.builder()
                .analysis(analysis)
                .resumeContent("커스텀 이력서 원문")
                .portfolioContent("커스텀 포트폴리오 원문")
                .build();

        // then
        assertThat(sourceText.getAnalysis()).isSameAs(analysis);
        assertThat(sourceText.getResumeContent()).isEqualTo("커스텀 이력서 원문");
        assertThat(sourceText.getPortfolioContent()).isEqualTo("커스텀 포트폴리오 원문");
    }
}
