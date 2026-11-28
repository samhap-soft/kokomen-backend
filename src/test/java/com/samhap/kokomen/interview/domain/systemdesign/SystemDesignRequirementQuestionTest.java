package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

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
    void 역질문_순번이_1부터_5_밖이면_거절한다() {
        assertAll(
                () -> assertThat(requirementQuestion(1).getQuestionOrder()).isEqualTo(1),
                () -> assertThat(requirementQuestion(SystemDesignRequirementQuestion.MAX_COUNT).getQuestionOrder())
                        .isEqualTo(5),
                () -> assertThatThrownBy(() -> requirementQuestion(0))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("역질문 순번은 1~5이어야 합니다: 0"),
                () -> assertThatThrownBy(() -> requirementQuestion(SystemDesignRequirementQuestion.MAX_COUNT + 1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("역질문 순번은 1~5이어야 합니다: 6")
        );
    }

    @Test
    void 역질문_답은_1000자까지_받고_넘으면_자르지_않고_거절한다() {
        String maxAnswer = "가".repeat(SystemDesignRequirementQuestion.MAX_ANSWER_LENGTH);

        assertAll(
                () -> assertThat(SystemDesignRequirementQuestionFixtureBuilder.builder().answer(maxAnswer).build()
                        .getAnswer()).isEqualTo(maxAnswer),
                () -> assertThatThrownBy(() -> SystemDesignRequirementQuestionFixtureBuilder.builder()
                        .answer(maxAnswer + "나")
                        .build())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("역질문 답은 1000자 이하여야 합니다: 1001자")
        );
    }

    @Test
    void 역질문을_만들면_정규화한_내용과_물은_요구사항_code와_답의_종류와_확정_항목을_복사해_담는다() {
        // given
        SystemDesignInterview systemDesignInterview = SystemDesignInterviewFixtureBuilder.builder().build();
        List<String> askedCodes = new ArrayList<>(List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"));
        List<SystemDesignRequirementItem> items = new ArrayList<>(List.of(new SystemDesignRequirementItem(
                "하루 생성량", "하루 평균 10만 개가 만들어진다.", SystemDesignRequirementSource.SEED)));

        // when
        SystemDesignRequirementQuestion question = new SystemDesignRequirementQuestion(systemDesignInterview, 1,
                "  하루에 몇 개나 만들어지나요?\u00A0", "하루 평균 10만 개 정도예요.", askedCodes,
                SystemDesignRequirementAnswerType.SEED, items);
        askedCodes.clear();
        items.clear();

        // then
        assertAll(
                () -> assertThat(question.getSystemDesignInterview()).isSameAs(systemDesignInterview),
                () -> assertThat(question.getContent()).isEqualTo("하루에 몇 개나 만들어지나요?"),
                () -> assertThat(question.getAnswer()).isEqualTo("하루 평균 10만 개 정도예요."),
                () -> assertThat(question.getAskedRequirementCodes())
                        .containsExactly("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"),
                () -> assertThat(question.getAnswerType()).isEqualTo(SystemDesignRequirementAnswerType.SEED),
                () -> assertThat(question.getRequirementItems()).extracting(SystemDesignRequirementItem::name)
                        .containsExactly("하루 생성량"),
                () -> assertThatThrownBy(() -> new SystemDesignRequirementQuestion(systemDesignInterview, 1, "\u3000",
                        "답", List.of(), SystemDesignRequirementAnswerType.ASSUMED, List.of()))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("질문은 1~200자로 적어 주세요.")
        );
    }

    // 새로 저장하는 역질문은 답의 종류와 확정 항목(없으면 빈 목록)이 늘 있다. NULL은 이 값을 저장하기 전에 만든 행에만 있다.
    @Test
    void 새_역질문은_답의_종류와_확정_항목이_있어야_한다() {
        SystemDesignInterview systemDesignInterview = SystemDesignInterviewFixtureBuilder.builder().build();

        assertAll(
                () -> assertThatThrownBy(() -> new SystemDesignRequirementQuestion(systemDesignInterview, 1, "질문",
                        "답", List.of(), null, List.of()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("새 역질문에는 답의 종류와 확정 항목이 있어야 합니다."),
                () -> assertThatThrownBy(() -> new SystemDesignRequirementQuestion(systemDesignInterview, 1, "질문",
                        "답", List.of(), SystemDesignRequirementAnswerType.ASSUMED, null))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("새 역질문에는 답의 종류와 확정 항목이 있어야 합니다.")
        );
    }

    // 다음 역질문의 확정한 요구사항 문맥이 이 목록을 쓴다. 확정 항목을 저장하기 전에 만든 행(NULL)은 건너뛴다.
    @Test
    void 확정_항목은_물은_순서로_모으고_저장하기_전에_만든_행은_건너뛴다() {
        // given
        SystemDesignRequirementItem customAlias = new SystemDesignRequirementItem("커스텀 별칭", "이번 범위에서 뺀다.",
                SystemDesignRequirementSource.ASSUMED);
        SystemDesignRequirementItem region = new SystemDesignRequirementItem("서비스 지역", "국내 사용자만 대상으로 한다.",
                SystemDesignRequirementSource.ASSUMED);
        SystemDesignRequirementItem retention = new SystemDesignRequirementItem("보관 기간", "5년 동안 열려야 한다.",
                SystemDesignRequirementSource.SEED);
        SystemDesignRequirementQuestion legacy = requirementQuestion(1, List.of("DAILY_CREATION_VOLUME"));
        ReflectionTestUtils.setField(legacy, "answerType", null);
        ReflectionTestUtils.setField(legacy, "requirementItems", null);
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                legacy,
                SystemDesignRequirementQuestionFixtureBuilder.builder().questionOrder(2)
                        .requirementItems(List.of(customAlias, region)).build(),
                SystemDesignRequirementQuestionFixtureBuilder.builder().questionOrder(3)
                        .requirementItems(List.of()).build(),
                SystemDesignRequirementQuestionFixtureBuilder.builder().questionOrder(4)
                        .requirementItems(List.of(retention)).build());

        // when & then
        assertAll(
                () -> assertThat(SystemDesignRequirementQuestion.collectRequirementItems(requirementQuestions))
                        .containsExactly(customAlias, region, retention),
                () -> assertThat(SystemDesignRequirementQuestion.collectRequirementItems(List.of(legacy))).isEmpty()
        );
    }

    // 면접자가 확인한 요구사항이다. 마지막 답변 채점의 확인 여부와 리포트의 놓친 요구사항이 이 값을 함께 쓴다.
    @Test
    void 물은_요구사항_code는_모든_역질문의_합집합이고_처음_물은_순서다() {
        // given
        List<SystemDesignRequirementQuestion> requirementQuestions = List.of(
                requirementQuestion(1, List.of("RETENTION_PERIOD")),
                requirementQuestion(2, List.of("DAILY_CREATION_VOLUME", "RETENTION_PERIOD")),
                requirementQuestion(3, List.of()));

        // when & then
        assertAll(
                () -> assertThat(SystemDesignRequirementQuestion.collectAskedRequirementCodes(requirementQuestions))
                        .containsExactly("RETENTION_PERIOD", "DAILY_CREATION_VOLUME"),
                () -> assertThat(SystemDesignRequirementQuestion.collectAskedRequirementCodes(List.of())).isEmpty()
        );
    }

    // 응답을 받지 못해 다시 보낸 역질문을 가려낸다. 다듬은 내용이 같고 저장한 지 60초 안(60초 포함)일 때만 재시도다.
    @Test
    void 저장한_지_60초_안에_다듬은_내용이_같은_질문이_오면_재시도다() {
        // given
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 26, 14, 0, 0);
        SystemDesignRequirementQuestion question = savedRequirementQuestion("하루에 몇 개나 만들어지나요?", savedAt);

        // when & then
        assertAll(
                () -> assertThat(SystemDesignRequirementQuestion.RETRY_WINDOW).isEqualTo(Duration.ofSeconds(60)),
                () -> assertThat(question.isRetriedBy("하루에 몇 개나 만들어지나요?", savedAt.plusSeconds(1))).isTrue(),
                () -> assertThat(question.isRetriedBy("하루에 몇 개나 만들어지나요?", savedAt.plusSeconds(60))).isTrue(),
                () -> assertThat(question.isRetriedBy("하루에 몇 개나 만들어지나요?",
                        savedAt.plusSeconds(60).plusNanos(1_000))).isFalse(),
                () -> assertThat(question.isRetriedBy("하루에 몇 개나 만들어지나요!", savedAt.plusSeconds(1))).isFalse(),
                () -> assertThat(question.isRetriedBy("하루에 몇 개나", savedAt.plusSeconds(1))).isFalse()
        );
    }

    // 저장 전(생성 시각 없음)에는 비교할 시각이 없으므로 재시도로 보지 않는다.
    @Test
    void 저장_시각이_없으면_같은_내용이어도_재시도가_아니다() {
        SystemDesignRequirementQuestion question = SystemDesignRequirementQuestionFixtureBuilder.builder()
                .content("하루에 몇 개나 만들어지나요?")
                .build();

        assertThat(question.isRetriedBy("하루에 몇 개나 만들어지나요?", LocalDateTime.now())).isFalse();
    }

    private static SystemDesignRequirementQuestion requirementQuestion(int questionOrder) {
        return SystemDesignRequirementQuestionFixtureBuilder.builder().questionOrder(questionOrder).build();
    }

    private static SystemDesignRequirementQuestion requirementQuestion(int questionOrder, List<String> askedCodes) {
        return SystemDesignRequirementQuestionFixtureBuilder.builder()
                .questionOrder(questionOrder)
                .askedRequirementCodes(askedCodes)
                .build();
    }

    // 생성 시각은 저장할 때 JPA 감사가 채운다. 저장하지 않고 시각만 정해 둔다.
    private static SystemDesignRequirementQuestion savedRequirementQuestion(String content, LocalDateTime savedAt) {
        SystemDesignRequirementQuestion question = SystemDesignRequirementQuestionFixtureBuilder.builder()
                .content(content)
                .build();
        ReflectionTestUtils.setField(question, "createdAt", savedAt);
        return question;
    }
}
