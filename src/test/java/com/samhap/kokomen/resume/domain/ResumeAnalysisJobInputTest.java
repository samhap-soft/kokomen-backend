package com.samhap.kokomen.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeAnalysisJobInputTest {

    @Test
    void 채용_공고가_있으면_hasJobDescription은_true다() {
        ResumeAnalysisJobInput jobInput = new ResumeAnalysisJobInput("백엔드 개발자", "Java, Spring 경험자", "3년");

        assertThat(jobInput.hasJobDescription()).isTrue();
    }

    @Test
    void 채용_공고가_null이면_hasJobDescription은_false다() {
        ResumeAnalysisJobInput jobInput = new ResumeAnalysisJobInput("백엔드 개발자", null, "3년");

        assertThat(jobInput.hasJobDescription()).isFalse();
    }

    @Test
    void 채용_공고가_공백만_있으면_hasJobDescription은_false다() {
        ResumeAnalysisJobInput jobInput = new ResumeAnalysisJobInput("백엔드 개발자", "   \n\t ", "3년");

        assertThat(jobInput.hasJobDescription()).isFalse();
    }
}
