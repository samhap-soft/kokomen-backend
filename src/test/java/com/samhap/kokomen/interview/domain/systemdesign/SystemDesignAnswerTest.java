package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SystemDesignAnswerTest {

    @Test
    void 답변은_정규화한_뒤_3000자까지_받는다() {
        String maxContent = "가".repeat(SystemDesignAnswer.MAX_CONTENT_LENGTH);

        assertAll(
                () -> assertThat(SystemDesignAnswer.normalizeContent(" 캐시를 둡니다.\n")).isEqualTo("캐시를 둡니다."),
                () -> assertThat(SystemDesignAnswer.normalizeContent(maxContent)).isEqualTo(maxContent),
                () -> assertThat(SystemDesignAnswer.normalizeContent("\u00A0" + maxContent + "\uFEFF"))
                        .isEqualTo(maxContent)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidContents")
    void 답변은_3000자까지_받고_null이거나_공백뿐이거나_3001자면_거절한다(String content) {
        assertThatThrownBy(() -> SystemDesignAnswer.normalizeContent(content))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("답변은 1~3,000자로 적어 주세요.");
    }

    static Stream<String> invalidContents() {
        return Stream.of(null, "", "  ", "\u00A0", "\u00A0\u3000\uFEFF",
                "가".repeat(SystemDesignAnswer.MAX_CONTENT_LENGTH + 1));
    }

    @Test
    void 피드백이_2000자를_넘으면_잘라_저장한다() {
        // given
        String maxFeedback = "나".repeat(SystemDesignAnswer.MAX_FEEDBACK_LENGTH);

        // when
        SystemDesignAnswer truncated = SystemDesignAnswerFixtureBuilder.builder().feedback(maxFeedback + "더").build();
        SystemDesignAnswer exact = SystemDesignAnswerFixtureBuilder.builder().feedback(maxFeedback).build();

        // then
        assertAll(
                () -> assertThat(truncated.getFeedback()).isEqualTo(maxFeedback),
                () -> assertThat(exact.getFeedback()).isEqualTo(maxFeedback)
        );
    }

    @Test
    void 답변을_만들면_정규화한_내용과_다이어그램과_등급을_담는다() {
        // given
        SystemDesignQuestion question = SystemDesignQuestionFixtureBuilder.builder().build();
        SystemDesignDiagram diagram = SystemDesignDiagramFixture.withCache();

        // when
        SystemDesignAnswer answer = new SystemDesignAnswer(question, "\u3000API 서버 앞에 캐시를 둡니다. ", diagram,
                AnswerRank.A, "근거가 분명해요.");

        // then
        assertAll(
                () -> assertThat(answer.getQuestion()).isSameAs(question),
                () -> assertThat(answer.getContent()).isEqualTo("API 서버 앞에 캐시를 둡니다."),
                () -> assertThat(answer.getDiagram()).isEqualTo(diagram),
                () -> assertThat(answer.getAnswerRank()).isEqualTo(AnswerRank.A),
                () -> assertThat(answer.getFeedback()).isEqualTo("근거가 분명해요."),
                () -> assertThatThrownBy(() -> new SystemDesignAnswer(question, " ", diagram, AnswerRank.A, "피드백"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("답변은 1~3,000자로 적어 주세요.")
        );
    }
}
