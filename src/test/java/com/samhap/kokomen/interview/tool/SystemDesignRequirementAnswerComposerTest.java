package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class SystemDesignRequirementAnswerComposerTest {

    private static final List<Requirement> REQUIREMENTS = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS;
    private static final String DEFAULT_ANSWER = "등록되지 않은 내용이에요. 가정하고 진행하세요.";
    private static final String DAILY_CREATION_VOLUME_ANSWER = "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.";
    private static final String READ_WRITE_RATIO_ANSWER = "단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요.";
    private static final String RETENTION_PERIOD_ANSWER = "단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void YES인_요구사항의_code를_문제_순서로_돌려준다() {
        // given: 모델이 필드를 문제 순서와 다르게 적어도 결과는 문제 순서다
        ObjectNode toolInput = objectMapper.createObjectNode()
                .put("reasoning", "보관 기간과 하루 생성량을 함께 묻는다.")
                .put("requirement_retention_period", "YES")
                .put("requirement_click_statistics", "NO")
                .put("requirement_daily_creation_volume", "YES")
                .put("requirement_short_key_length", "NO")
                .put("requirement_read_write_ratio", "NO")
                .put("answer", "하루 평균 10만 개 정도 만들어지고, 만든 날부터 5년 동안 열려야 해요.");

        // when
        List<String> askedCodes = SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS, toolInput);

        // then
        assertThat(askedCodes).containsExactly("DAILY_CREATION_VOLUME", "RETENTION_PERIOD");
    }

    @Test
    void 모두_NO면_빈_목록이다() {
        assertThat(SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS, verdicts("NO", "NO", "NO",
                "NO", "NO"))).isEmpty();
    }

    @Test
    void 요구사항_필드가_하나라도_없으면_실패다() {
        // given
        ObjectNode toolInput = verdicts("YES", "NO", "NO", "NO", "NO");
        toolInput.remove("requirement_short_key_length");

        // when & then
        assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS, toolInput))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_short_key_length");
    }

    @ParameterizedTest
    @MethodSource("invalidVerdicts")
    void 판정_값이_YES나_NO가_아니면_실패다(JsonNode invalidVerdict) {
        // given
        ObjectNode toolInput = verdicts("NO", "NO", "NO", "NO", "NO");
        toolInput.set("requirement_read_write_ratio", invalidVerdict);

        // when & then
        assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS, toolInput))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_read_write_ratio");
    }

    static Stream<JsonNode> invalidVerdicts() {
        return Stream.of(TextNode.valueOf("yes"), TextNode.valueOf("MAYBE"), TextNode.valueOf(""),
                TextNode.valueOf(" YES"), IntNode.valueOf(1), BooleanNode.TRUE, NullNode.getInstance());
    }

    @Test
    void 도구_입력이_객체가_아니면_실패다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS,
                        null))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessageContaining("field=requirement_daily_creation_volume"),
                () -> assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS,
                        TextNode.valueOf("YES")))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessageContaining("field=requirement_daily_creation_volume")
        );
    }

    @Test
    void 모르는_필드는_무시한다() {
        // given
        ObjectNode toolInput = verdicts("NO", "YES", "NO", "NO", "NO");
        toolInput.put("requirement_unknown", "YES");
        toolInput.put("confidence", 0.9);

        // when & then
        assertThat(SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS, toolInput))
                .containsExactly("READ_WRITE_RATIO");
    }

    @Test
    void 물은_요구사항이_없으면_LLM_답과_상관없이_기본_문구다() {
        assertAll(
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, List.of(),
                        "하루 평균 10만 개 정도예요.")).isEqualTo(DEFAULT_ANSWER),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, List.of(), ""))
                        .isEqualTo(DEFAULT_ANSWER)
        );
    }

    @Test
    void 물은_요구사항이_있으면_LLM_답을_쓴다() {
        String llmAnswer = "하루에 새로 만들어지는 단축 URL은 10만 개 정도예요.";

        assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, List.of("DAILY_CREATION_VOLUME"),
                "  " + llmAnswer + "\n")).isEqualTo(llmAnswer);
    }

    @Test
    void LLM_답이_비었거나_300자를_넘으면_시드_답을_이어_쓴다() {
        // given: 시드 답은 문제 순서로 공백 하나를 두고 잇는다
        List<String> askedCodes = List.of("RETENTION_PERIOD", "DAILY_CREATION_VOLUME");
        String seedAnswers = DAILY_CREATION_VOLUME_ANSWER + " " + RETENTION_PERIOD_ANSWER;
        String longestAnswer = "가".repeat(300);

        // when & then
        assertAll(
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, askedCodes, null))
                        .isEqualTo(seedAnswers),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, askedCodes, ""))
                        .isEqualTo(seedAnswers),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, askedCodes, " \n\t "))
                        .isEqualTo(seedAnswers),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, askedCodes,
                        "가".repeat(301))).isEqualTo(seedAnswers),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, askedCodes,
                        longestAnswer)).isEqualTo(longestAnswer)
        );
    }

    // 판정은 YES인데 답이 정해진 문구면 답과 판정이 어긋난다. 판정을 따라 시드 답을 이어 써서 물은 것으로 기록한 요구사항을
    // 면접자가 실제로 듣게 한다.
    @Test
    void 물은_요구사항이_있는데_답이_정해진_문구면_시드_답을_이어_쓴다() {
        assertAll(
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS,
                        List.of("DAILY_CREATION_VOLUME"), DEFAULT_ANSWER)).isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS,
                        List.of("DAILY_CREATION_VOLUME"), "음, " + DEFAULT_ANSWER))
                        .isEqualTo(DAILY_CREATION_VOLUME_ANSWER)
        );
    }

    // 면접자가 직접 계산할 값(초당 요청 수)을 대신 계산했거나 정해 둔 사실에 없는 수치를 지어낸 답이다.
    @Test
    void LLM_답에_시드에_없는_숫자가_있으면_시드_답을_이어_쓴다() {
        assertAll(
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS,
                        List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개, 초당 116건 정도예요."))
                        .isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS,
                        List.of("DAILY_CREATION_VOLUME"), "하루 평균 10.5만 개 정도예요."))
                        .isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS,
                        List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."))
                        .isEqualTo("하루 평균 10만 개 정도예요.")
        );
    }

    @Test
    void 천_단위_쉼표가_있어도_같은_숫자로_본다() {
        List<Requirement> requirements = List.of(
                new Requirement("DAILY_ACTIVE_USERS", "하루 활성 사용자", "하루 활성 사용자는 1,000만 명이에요."),
                new Requirement("MONTHLY_ORDERS", "월 주문 수", "한 달 주문은 1000만 건이에요."));

        assertAll(
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(requirements,
                        List.of("DAILY_ACTIVE_USERS"), "하루에 1000만 명이 써요."))
                        .isEqualTo("하루에 1000만 명이 써요."),
                () -> assertThat(SystemDesignRequirementAnswerComposer.compose(requirements,
                        List.of("MONTHLY_ORDERS"), "한 달에 1,000만 건 정도예요."))
                        .isEqualTo("한 달에 1,000만 건 정도예요.")
        );
    }

    // 조회 비율(100배)만 물었는데 묻지 않은 하루 생성량(10만 개)을 흘린 답이다.
    @Test
    void 묻지_않은_요구사항의_숫자가_섞이면_시드_답을_이어_쓴다() {
        assertThat(SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, List.of("READ_WRITE_RATIO"),
                "조회는 생성의 100배쯤이고, 하루에 10만 개가 만들어져요."))
                .isEqualTo(READ_WRITE_RATIO_ANSWER);
    }

    @Test
    void 폴백하면_이유와_물은_요구사항을_WARN으로_남긴다() {
        // given
        Logger composerLogger = (Logger) LoggerFactory.getLogger(SystemDesignRequirementAnswerComposer.class);
        ListAppender<ILoggingEvent> composerLogs = new ListAppender<>();
        composerLogs.start();
        composerLogger.addAppender(composerLogs);

        // when
        try {
            SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, List.of("DAILY_CREATION_VOLUME"),
                    "하루 평균 10만 개, 초당 116건 정도예요.");
            SystemDesignRequirementAnswerComposer.compose(REQUIREMENTS, List.of("DAILY_CREATION_VOLUME"),
                    "하루 평균 10만 개 정도예요.");
        } finally {
            composerLogger.detachAppender(composerLogs);
        }

        // then: 폴백한 첫 호출만 남긴다
        assertThat(composerLogs.list).singleElement().satisfies(event -> assertAll(
                () -> assertThat(event.getLevel()).isEqualTo(Level.WARN),
                () -> assertThat(event.getFormattedMessage()).contains("시드에 없는 숫자 [116]", "DAILY_CREATION_VOLUME")
        ));
    }

    private ObjectNode verdicts(String dailyCreationVolume, String readWriteRatio, String retentionPeriod,
                                String shortKeyLength, String clickStatistics) {
        return objectMapper.createObjectNode()
                .put("reasoning", "판정 근거")
                .put("requirement_daily_creation_volume", dailyCreationVolume)
                .put("requirement_read_write_ratio", readWriteRatio)
                .put("requirement_retention_period", retentionPeriod)
                .put("requirement_short_key_length", shortKeyLength)
                .put("requirement_click_statistics", clickStatistics)
                .put("answer", "면접관의 답");
    }
}
