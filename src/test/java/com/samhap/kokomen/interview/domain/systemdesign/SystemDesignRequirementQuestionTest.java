package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SystemDesignRequirementQuestionTest {

    @Test
    void 역질문은_정규화한_뒤_200자까지_받는다() {
        String maxContent = "가".repeat(SystemDesignRequirementQuestion.MAX_CONTENT_LENGTH);

        assertAll(
                () -> assertThat(SystemDesignRequirementQuestion.normalizeContent("  하루 생성량은요?\u3000"))
                        .isEqualTo("하루 생성량은요?"),
                () -> assertThat(SystemDesignRequirementQuestion.normalizeContent("가")).isEqualTo("가"),
                () -> assertThat(SystemDesignRequirementQuestion.normalizeContent(maxContent)).isEqualTo(maxContent),
                () -> assertThat(SystemDesignRequirementQuestion.normalizeContent("\u3000 " + maxContent + "\n"))
                        .isEqualTo(maxContent)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidContents")
    void 역질문이_null이거나_공백뿐이거나_201자면_거절한다(String content) {
        assertThatThrownBy(() -> SystemDesignRequirementQuestion.normalizeContent(content))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("질문은 1~200자로 적어 주세요.");
    }

    static Stream<String> invalidContents() {
        return Stream.of(null, "", "   ", "\u3000\u3000", "\t\n",
                "가".repeat(SystemDesignRequirementQuestion.MAX_CONTENT_LENGTH + 1));
    }

    @Test
    void 역질문이_5개면_더_받지_않는다() {
        assertAll(
                () -> assertThatCode(() -> SystemDesignRequirementQuestion.validateCount(0)).doesNotThrowAnyException(),
                () -> assertThatCode(() -> SystemDesignRequirementQuestion.validateCount(4)).doesNotThrowAnyException(),
                () -> assertThatThrownBy(() -> SystemDesignRequirementQuestion.validateCount(5))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요."),
                () -> assertThatThrownBy(() -> SystemDesignRequirementQuestion.validateCount(6))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요.")
        );
    }

    @Test
    void 역질문을_만들면_정규화한_내용과_물은_요구사항_code를_복사해_담는다() {
        // given
        SystemDesignInterview systemDesignInterview = SystemDesignInterviewFixtureBuilder.builder().build();
        List<String> askedCodes = new ArrayList<>(List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"));

        // when
        SystemDesignRequirementQuestion question = new SystemDesignRequirementQuestion(systemDesignInterview,
                "  하루에 몇 개나 만들어지나요?\u00A0", "하루 평균 10만 개 정도예요.", askedCodes);
        askedCodes.clear();

        // then
        assertAll(
                () -> assertThat(question.getSystemDesignInterview()).isSameAs(systemDesignInterview),
                () -> assertThat(question.getContent()).isEqualTo("하루에 몇 개나 만들어지나요?"),
                () -> assertThat(question.getAnswer()).isEqualTo("하루 평균 10만 개 정도예요."),
                () -> assertThat(question.getAskedRequirementCodes())
                        .containsExactly("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"),
                () -> assertThatThrownBy(() -> new SystemDesignRequirementQuestion(systemDesignInterview, "\u3000",
                        "답", List.of()))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("질문은 1~200자로 적어 주세요.")
        );
    }
}
