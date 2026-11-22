package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import org.junit.jupiter.api.Test;

class SystemDesignQuestionTest {

    @Test
    void 단계와_순번은_기준_질문에서_가져온다() {
        // given
        SystemDesignInterview systemDesignInterview = SystemDesignInterviewFixtureBuilder.builder().build();
        SystemDesignProblemQuestion problemQuestion = SystemDesignProblemQuestionFixtureBuilder.builder()
                .stage(SystemDesignStage.DEEP_DIVE)
                .questionOrder(4)
                .content("리다이렉트 요청이 크게 늘면 어디가 먼저 병목이 되나요?")
                .build();

        // when
        SystemDesignQuestion question = new SystemDesignQuestion(systemDesignInterview, problemQuestion,
                "캐시를 두었다고 했는데, 캐시가 감당하지 못하면 어디가 먼저 병목이 되나요?");

        // then
        assertAll(
                () -> assertThat(question.getSystemDesignInterview()).isSameAs(systemDesignInterview),
                () -> assertThat(question.getProblemQuestion()).isSameAs(problemQuestion),
                () -> assertThat(question.getStage()).isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(question.getQuestionOrder()).isEqualTo(4),
                () -> assertThat(question.getContent()).isEqualTo("캐시를 두었다고 했는데, 캐시가 감당하지 못하면 어디가 먼저 병목이 되나요?")
        );
    }

    @Test
    void 질문_문장이_500자를_넘으면_잘라_저장한다() {
        // given
        String maxContent = "가".repeat(SystemDesignQuestion.MAX_CONTENT_LENGTH);

        // when
        SystemDesignQuestion truncated = SystemDesignQuestionFixtureBuilder.builder().content(maxContent + "나").build();
        SystemDesignQuestion exact = SystemDesignQuestionFixtureBuilder.builder().content(maxContent).build();

        // then
        assertAll(
                () -> assertThat(truncated.getContent()).isEqualTo(maxContent),
                () -> assertThat(exact.getContent()).isEqualTo(maxContent)
        );
    }
}
