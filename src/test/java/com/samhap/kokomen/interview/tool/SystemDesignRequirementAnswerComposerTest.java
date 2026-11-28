package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

class SystemDesignRequirementAnswerComposerTest {

    private static final List<Requirement> REQUIREMENTS = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS;
    private static final String DECLINED_ANSWER = "요구사항을 묻는 질문에만 답할 수 있어요. 설계 방법은 직접 정해 주세요.";
    private static final String DAILY_CREATION_VOLUME_ANSWER = "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.";
    private static final String READ_WRITE_RATIO_ANSWER = "단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요.";
    private static final String RETENTION_PERIOD_ANSWER = "단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요.";
    private static final String CUSTOM_ALIAS_ANSWER = "커스텀 별칭은 이번 범위에서 빼도 돼요.";
    private static final String[] ALL_NO = {"NO", "NO", "NO", "NO", "NO"};
    private static final String[] DAILY_CREATION_VOLUME_ONLY = {"YES", "NO", "NO", "NO", "NO"};
    private static final String[] READ_WRITE_RATIO_ONLY = {"NO", "YES", "NO", "NO", "NO"};

    private final ObjectMapper objectMapper = Jackson2CompatibleJsonMapper.builder().build();
    private Logger composerLogger;
    private ListAppender<ILoggingEvent> composerLogs;

    @BeforeEach
    void setUp() {
        composerLogger = (Logger) LoggerFactory.getLogger(SystemDesignRequirementAnswerComposer.class);
        composerLogs = new ListAppender<>();
        composerLogs.start();
        composerLogger.addAppender(composerLogs);
    }

    @AfterEach
    void tearDown() {
        composerLogger.detachAppender(composerLogs);
    }

    // ---------------------------------------------------------------- 판정

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
        assertThat(SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS,
                toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO))).isEmpty();
    }

    @Test
    void 요구사항_필드가_하나라도_없으면_실패다() {
        // given
        ObjectNode toolInput = toolInput("SEED", DAILY_CREATION_VOLUME_ANSWER, DAILY_CREATION_VOLUME_ONLY);
        toolInput.remove("requirement_short_key_length");

        // when & then
        assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.compose(command(), toolInput))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_short_key_length");
    }

    @ParameterizedTest
    @MethodSource("invalidStringValues")
    void 판정_값이_YES나_NO가_아니면_실패다(JsonNode invalidVerdict) {
        // given
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.set("requirement_read_write_ratio", invalidVerdict);

        // when & then
        assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.resolveAskedCodes(REQUIREMENTS, toolInput))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_read_write_ratio");
    }

    static Stream<JsonNode> invalidStringValues() {
        return Stream.of(StringNode.valueOf("yes"), StringNode.valueOf("MAYBE"), StringNode.valueOf(""),
                StringNode.valueOf(" YES"), IntNode.valueOf(1), BooleanNode.TRUE, NullNode.getInstance());
    }

    @Test
    void 도구_입력이_객체가_아니면_실패다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.compose(command(), null))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessageContaining("field=requirement_daily_creation_volume"),
                () -> assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.compose(command(),
                        StringNode.valueOf("YES")))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessageContaining("field=requirement_daily_creation_volume")
        );
    }

    @Test
    void 모르는_필드는_무시한다() {
        // given
        ObjectNode toolInput = toolInput("SEED", READ_WRITE_RATIO_ANSWER, READ_WRITE_RATIO_ONLY);
        toolInput.put("requirement_unknown", "YES");
        toolInput.put("confidence", 0.9);

        // when
        SystemDesignRequirementAnswerResult result = SystemDesignRequirementAnswerComposer.compose(command(),
                toolInput);

        // then
        assertThat(result.askedRequirementCodes()).containsExactly("READ_WRITE_RATIO");
    }

    // ---------------------------------------------------------------- 답의 종류

    @ParameterizedTest
    @MethodSource("invalidStringValues")
    void 답의_종류가_enum_밖이면_실패다(JsonNode invalidAnswerType) {
        // given
        ObjectNode toolInput = toolInput("SEED", DAILY_CREATION_VOLUME_ANSWER, DAILY_CREATION_VOLUME_ONLY);
        toolInput.set("answer_type", invalidAnswerType);

        // when & then
        assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.compose(command(), toolInput))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 답의 종류가 없거나 값이 잘못됐습니다. field=answer_type");
    }

    @Test
    void 답의_종류가_없으면_실패다() {
        // given
        ObjectNode toolInput = toolInput("SEED", DAILY_CREATION_VOLUME_ANSWER, DAILY_CREATION_VOLUME_ONLY);
        toolInput.remove("answer_type");

        // when & then
        assertThatThrownBy(() -> SystemDesignRequirementAnswerComposer.compose(command(), toolInput))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 답의 종류가 없거나 값이 잘못됐습니다. field=answer_type");
    }

    // 답의 종류는 판정을 기준으로 바로잡는다. YES가 있으면 시드 사실이 답에 들어가고, YES가 없으면 시드 사실이 들어가지 않는다.
    @Test
    void 답의_종류는_판정에_맞춰_바로잡는다() {
        String mixedAnswer = "하루 평균 10만 개 정도 만들어지고, 커스텀 별칭은 이번 범위에서 빼도 돼요.";

        assertAll(
                () -> assertThat(compose(toolInput("ASSUMED", mixedAnswer, DAILY_CREATION_VOLUME_ONLY)).answerType())
                        .isEqualTo(SystemDesignRequirementAnswerType.MIXED),
                () -> assertThat(compose(toolInput("SEED", CUSTOM_ALIAS_ANSWER, ALL_NO)).answerType())
                        .isEqualTo(SystemDesignRequirementAnswerType.ASSUMED),
                () -> assertThat(compose(toolInput("MIXED", CUSTOM_ALIAS_ANSWER, ALL_NO)).answerType())
                        .isEqualTo(SystemDesignRequirementAnswerType.ASSUMED),
                () -> assertThat(compose(toolInput("SEED", DAILY_CREATION_VOLUME_ANSWER, DAILY_CREATION_VOLUME_ONLY))
                        .answerType()).isEqualTo(SystemDesignRequirementAnswerType.SEED),
                () -> assertThat(compose(toolInput("MIXED", mixedAnswer, DAILY_CREATION_VOLUME_ONLY)).answerType())
                        .isEqualTo(SystemDesignRequirementAnswerType.MIXED),
                () -> assertThat(compose(toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO)).answerType())
                        .isEqualTo(SystemDesignRequirementAnswerType.ASSUMED)
        );
    }

    // YES를 믿고 SEED로 본다. 모델이 정해 둔 사실로 실제로 답했으면 그 답을 쓴다.
    @Test
    void DECLINED인데_YES가_있고_쓸_수_있는_답이면_SEED로_본다() {
        assertThat(compose(toolInput("DECLINED", DAILY_CREATION_VOLUME_ANSWER, DAILY_CREATION_VOLUME_ONLY)))
                .extracting(SystemDesignRequirementAnswerResult::askedRequirementCodes,
                        SystemDesignRequirementAnswerResult::answerType, SystemDesignRequirementAnswerResult::answer)
                .containsExactly(List.of("DAILY_CREATION_VOLUME"), SystemDesignRequirementAnswerType.SEED,
                        DAILY_CREATION_VOLUME_ANSWER);
    }

    // "요구사항을 전부 알려 주세요"를 DECLINED로 보면서 YES를 여럿 표시하고 답을 비운 경우다. 시드 답으로 바꾸면 거절하려던 질문에
    // 정해 둔 사실을 한꺼번에 알려 주게 된다. 모델이 실제로 알려 준 것이 없으니 물은 요구사항으로 남기지도 않는다.
    @Test
    void DECLINED인데_YES가_있고_쓸_답이_없으면_시드를_알리지_않고_정해진_문구로_답한다() {
        // given
        String[] allYes = {"YES", "YES", "YES", "YES", "YES"};
        ObjectNode emptyAnswer = toolInput("DECLINED", "", allYes);
        emptyAnswer.set("requirement_items", items(item("하루 생성량", "하루 평균 10만 개가 만들어진다.", "SEED")));

        // when
        SystemDesignRequirementAnswerResult empty = compose(emptyAnswer);
        SystemDesignRequirementAnswerResult declined = compose(toolInput("DECLINED", DECLINED_ANSWER, allYes));
        SystemDesignRequirementAnswerResult leaked = compose(toolInput("DECLINED", "하루 10만 개이고 5년 보관해요.",
                DAILY_CREATION_VOLUME_ONLY));

        // then
        assertAll(
                () -> assertThat(List.of(empty, declined, leaked)).allSatisfy(result -> assertThat(result)
                        .extracting(SystemDesignRequirementAnswerResult::askedRequirementCodes,
                                SystemDesignRequirementAnswerResult::answerType,
                                SystemDesignRequirementAnswerResult::answer,
                                SystemDesignRequirementAnswerResult::requirementItems)
                        .containsExactly(List.of(), SystemDesignRequirementAnswerType.DECLINED, DECLINED_ANSWER,
                                List.of())),
                () -> assertThat(warnings()).hasSize(3)
                        .allMatch(message -> message.contains("정해진 문구로 답합니다"))
        );
    }

    @Test
    void DECLINED면_정해진_문구로_답하고_확정_항목을_비운다() {
        // given: 모델이 답과 항목을 적었어도 쓰지 않는다
        ObjectNode toolInput = toolInput("DECLINED", "MySQL을 쓰면 돼요.", ALL_NO);
        toolInput.set("requirement_items", items(item("저장소", "MySQL을 써요.", "ASSUMED")));

        // when
        SystemDesignRequirementAnswerResult result = compose(toolInput);

        // then
        assertThat(result)
                .extracting(SystemDesignRequirementAnswerResult::askedRequirementCodes,
                        SystemDesignRequirementAnswerResult::answerType, SystemDesignRequirementAnswerResult::answer,
                        SystemDesignRequirementAnswerResult::requirementItems)
                .containsExactly(List.of(), SystemDesignRequirementAnswerType.DECLINED, DECLINED_ANSWER, List.of());
    }

    // ---------------------------------------------------------------- 답

    @Test
    void 시드에_없는_요구사항은_LLM이_정한_답과_확정_항목을_쓴다() {
        // given
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.set("requirement_items", items(item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", "ASSUMED")));

        // when
        SystemDesignRequirementAnswerResult result = compose(toolInput);

        // then
        assertAll(
                () -> assertThat(result.askedRequirementCodes()).isEmpty(),
                () -> assertThat(result.answerType()).isEqualTo(SystemDesignRequirementAnswerType.ASSUMED),
                () -> assertThat(result.answer()).isEqualTo(CUSTOM_ALIAS_ANSWER),
                () -> assertThat(result.requirementItems()).containsExactly(new SystemDesignRequirementItem(
                        "커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", SystemDesignRequirementSource.ASSUMED))
        );
    }

    @Test
    void 물은_요구사항이_있으면_LLM_답을_다듬어_쓴다() {
        String llmAnswer = "하루에 새로 만들어지는 단축 URL은 10만 개 정도예요.";

        assertThat(compose(toolInput("SEED", "  " + llmAnswer + "\n", DAILY_CREATION_VOLUME_ONLY)).answer())
                .isEqualTo(llmAnswer);
    }

    @Test
    void 답의_엔티티를_원래_문자로_되돌린다() {
        assertThat(compose(toolInput("ASSUMED", "별칭에는 &lt;영문&gt; &amp; 숫자만 써요. &amp;lt;는 그대로예요.", ALL_NO))
                .answer()).isEqualTo("별칭에는 <영문> & 숫자만 써요. &lt;는 그대로예요.");
    }

    @Test
    void 답이_비었거나_300자를_넘으면_물은_시드_답을_문제_순서로_이어_쓴다() {
        // given
        String[] dailyAndRetention = {"YES", "NO", "YES", "NO", "NO"};
        String seedAnswers = DAILY_CREATION_VOLUME_ANSWER + " " + RETENTION_PERIOD_ANSWER;
        String longestAnswer = "가".repeat(300);
        ObjectNode withoutAnswer = toolInput("SEED", "", dailyAndRetention);
        withoutAnswer.remove("answer");
        ObjectNode numberAnswer = toolInput("SEED", "", dailyAndRetention);
        numberAnswer.put("answer", 100_000);

        // when & then
        assertAll(
                () -> assertThat(compose(withoutAnswer).answer()).isEqualTo(seedAnswers),
                () -> assertThat(compose(numberAnswer).answer()).isEqualTo(seedAnswers),
                () -> assertThat(compose(toolInput("SEED", "", dailyAndRetention)).answer()).isEqualTo(seedAnswers),
                () -> assertThat(compose(toolInput("SEED", " \n\t ", dailyAndRetention)).answer())
                        .isEqualTo(seedAnswers),
                () -> assertThat(compose(toolInput("SEED", "가".repeat(301), dailyAndRetention)).answer())
                        .isEqualTo(seedAnswers),
                () -> assertThat(compose(toolInput("SEED", longestAnswer, dailyAndRetention)).answer())
                        .isEqualTo(longestAnswer)
        );
    }

    @Test
    void 물은_시드가_없는데_답이_비었거나_300자를_넘으면_실패다() {
        assertAll(
                () -> assertThatThrownBy(() -> compose(toolInput("ASSUMED", " ", ALL_NO)))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("시스템 설계 역질문 답을 쓸 수 없습니다. reason=빈 답"),
                () -> assertThatThrownBy(() -> compose(toolInput("ASSUMED", "가".repeat(301), ALL_NO)))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("시스템 설계 역질문 답을 쓸 수 없습니다. reason=답 길이 301자")
        );
    }

    // "가정하고 진행하세요"처럼 결정을 면접자에게 넘긴 답, 요구사항 질문이 아닐 때의 문구를 쓴 답은 쓰지 않는다.
    @ParameterizedTest
    @ValueSource(strings = {
            "등록되지 않은 내용이에요. 가정하고 진행하세요.",
            "음, 등록되지 않은 내용이에요. 가정하고 진행하세요.",
            "그 부분은 정해 두지 않았어요. 자유롭게 가정해서 진행해 주세요.",
            "정해지지 않은 내용이라 직접 가정해 주세요.",
            "적절히 가정하셔도 돼요.",
            "요구사항을 묻는 질문에만 답할 수 있어요."
    })
    void 회피_문구가_있으면_물은_시드_답으로_바꾸고_물은_시드가_없으면_실패다(String evasiveAnswer) {
        assertAll(
                () -> assertThat(compose(toolInput("SEED", evasiveAnswer, DAILY_CREATION_VOLUME_ONLY)).answer())
                        .isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThatThrownBy(() -> compose(toolInput("ASSUMED", evasiveAnswer, ALL_NO)))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("시스템 설계 역질문 답을 쓸 수 없습니다. reason=회피 문구")
        );
    }

    // 정해진 안내 문구의 일부("등록되지 않은")가 들어간 정상 문장까지 막으면, 바꿀 시드 답이 없을 때 같은 질문이 계속 500이 된다.
    @ParameterizedTest
    @ValueSource(strings = {
            "등록되지 않은 사용자도 단축 URL을 만들 수 있어요.",
            "만료일이 정해지지 않은 링크는 만들 수 없어요.",
            "로그인은 필요 없고, 따로 정해 두지 않은 별칭은 받지 않아요."
    })
    void 안내_문구와_겹치는_말이_있어도_정상_문장은_회피_문구가_아니다(String answer) {
        assertThat(compose(toolInput("ASSUMED", answer, ALL_NO)).answer()).isEqualTo(answer);
    }

    // 조회 비율(100배)만 물었는데 묻지 않은 하루 생성량(10만)을 흘린 답이다.
    @Test
    void 묻지_않은_시드의_숫자와_단위가_있으면_물은_시드_답으로_바꾼다() {
        assertThat(compose(toolInput("SEED", "조회는 생성의 100배쯤이고, 하루에 10만 개가 만들어져요.", READ_WRITE_RATIO_ONLY)))
                .extracting(SystemDesignRequirementAnswerResult::answerType,
                        SystemDesignRequirementAnswerResult::answer)
                .containsExactly(SystemDesignRequirementAnswerType.SEED, READ_WRITE_RATIO_ANSWER);
    }

    // AI가 정한 값이 묻지 않은 시드와 같은 수치면 나중에 그 시드를 물었을 때 정해 둔 사실이 새어 나간 셈이다. 바꿀 시드 답이 없으니
    // 실패로 보고, 사용자는 답 생성 실패(500)를 받는다.
    @Test
    void 묻지_않은_시드의_숫자와_단위가_있는데_물은_시드가_없으면_실패다() {
        assertThatThrownBy(() -> compose(toolInput("ASSUMED", "단축 URL은 만든 날부터 5년 동안 보관해요.", ALL_NO)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 답을 쓸 수 없습니다. reason=묻지 않은 요구사항의 수치 [5년]");
    }

    @Test
    void 숫자만_같고_단위가_다르면_유출이_아니다() {
        String answer = "한 회원은 커스텀 별칭을 5개까지 만들 수 있어요.";

        assertThat(compose(toolInput("ASSUMED", answer, ALL_NO)).answer()).isEqualTo(answer);
    }

    // 천 단위 쉼표를 지우고, 숫자 바로 뒤의 한글은 첫 글자(조사가 붙어도 같은 단위), 영문은 이어진 글자 전체를 대소문자 없이 본다.
    @Test
    void 숫자와_단위는_쉼표와_뒤에_붙은_조사를_빼고_비교한다() {
        // given
        List<Requirement> requirements = List.of(
                new Requirement("DAILY_ACTIVE_USERS", "하루 활성 사용자", "하루 활성 사용자는 1,000만 명이에요."),
                new Requirement("READ_RATIO", "조회 비율", "조회는 쓰기의 100배쯤이에요."),
                new Requirement("MESSAGE_SIZE", "메시지 크기", "메시지는 평균 50바이트이고 사진은 20MB까지예요."),
                new Requirement("PRICING", "요금", "요금은 이번 범위에서 빼도 돼요."));
        SystemDesignRequirementAnswerCommand command = new SystemDesignRequirementAnswerCommand(31L,
                new SystemDesignProblemContext(7L, "문제", "설명", "기준", requirements, List.of()), List.of(),
                SystemDesignConfirmedRequirements.empty(), "질문");
        String[] pricingOnly = {"NO", "NO", "NO", "YES"};

        // when & then
        assertAll(
                () -> assertThat(composeWith(command, "요금은 빼고 하루 1000만명이 써요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 조회가 100배예요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 메시지는 50바이트 정도예요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 사진은 20mb까지예요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 하루 1000번 정도 써요.", pricingOnly))
                        .isEqualTo("요금은 빼고 하루 1000번 정도 써요.")
        );
    }

    // 전각 숫자는 같은 숫자로 읽고(NFKC), 영문 단위와 %는 숫자 사이에 공백이 하나 있어도 같은 단위로 본다. 한글 단위는 붙여 쓴 것만 본다.
    @Test
    void 전각_숫자와_공백을_둔_영문_단위도_같은_수치로_본다() {
        // given
        List<Requirement> requirements = List.of(
                new Requirement("RESPONSE_LATENCY", "응답 지연 목표", "API는 100ms 안에 응답해야 해요."),
                new Requirement("PHOTO_SIZE", "사진 용량", "원본은 장당 최대 20MB까지 받아요."),
                new Requirement("DAILY_VOLUME", "하루 생성량", "하루 평균 10만 개가 만들어져요."),
                new Requirement("PRICING", "요금", "요금은 이번 범위에서 빼도 돼요."));
        SystemDesignRequirementAnswerCommand command = new SystemDesignRequirementAnswerCommand(31L,
                new SystemDesignProblemContext(7L, "문제", "설명", "기준", requirements, List.of()), List.of(),
                SystemDesignConfirmedRequirements.empty(), "질문");
        String[] pricingOnly = {"NO", "NO", "NO", "YES"};

        // when & then
        assertAll(
                () -> assertThat(composeWith(command, "요금은 빼고 응답은 100 ms 안이에요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 사진은 20 MB까지예요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 하루 １０만 개예요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요."),
                () -> assertThat(composeWith(command, "요금은 빼고 사진은 ２０ＭＢ까지예요.", pricingOnly))
                        .isEqualTo("요금은 이번 범위에서 빼도 돼요.")
        );
    }

    // 계산을 대신하지 않는 규칙은 프롬프트가 막는다. LLM이 정한 내용이 들어간 답(ASSUMED·MIXED)에서 서버는 묻지 않은 시드의 수치만 본다.
    @Test
    void LLM이_정한_내용이_들어간_답은_묻지_않은_시드의_수치가_아니면_숫자를_막지_않는다() {
        assertAll(
                () -> assertThat(compose(toolInput("ASSUMED", "가용성은 99.9%를 목표로 해요.", ALL_NO)).answer())
                        .isEqualTo("가용성은 99.9%를 목표로 해요."),
                () -> assertThat(compose(toolInput("MIXED", "하루 평균 10만 개이고, 가용성은 99.9%예요.",
                        DAILY_CREATION_VOLUME_ONLY)).answer()).isEqualTo("하루 평균 10만 개이고, 가용성은 99.9%예요.")
        );
    }

    // 정해 둔 사실만으로 답하는 SEED 답에는 정해 둔 사실(지금까지 물은 시드)과 앞에서 확정한 항목에 없는 수치가 들어갈 까닭이 없다.
    // 시드 값을 바꾼 답(20만)과 면접자가 할 계산을 대신한 답(초당 116건)은 물은 시드 답으로 바꾼다.
    @Test
    void SEED_답에_정해_둔_사실에_없는_수치가_있으면_물은_시드_답으로_바꾼다() {
        assertAll(
                () -> assertThat(compose(toolInput("SEED", "하루 평균 20만 개 정도예요.", DAILY_CREATION_VOLUME_ONLY))
                        .answer()).isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(compose(toolInput("SEED", "하루 평균 10만 개, 초당 116건 정도예요.",
                        DAILY_CREATION_VOLUME_ONLY)).answer()).isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(warnings()).hasSize(2)
                        .allMatch(message -> message.contains("정해 둔 사실에 없는 수치"))
        );
    }

    @Test
    void SEED_답은_앞에서_물은_시드와_확정_항목의_수치를_다시_말할_수_있다() {
        // given
        SystemDesignRequirementAnswerCommand command = command(new SystemDesignConfirmedRequirements(
                List.of("DAILY_CREATION_VOLUME"), List.of(new SystemDesignRequirementItem("서비스 지역",
                "국내 사용자 3천만 명을 대상으로 한다.", SystemDesignRequirementSource.ASSUMED))));
        String answer = "하루 10만 개가 만들어지는 3천만 명 서비스라, 조회는 생성의 100배쯤이에요.";

        // when & then
        assertThat(SystemDesignRequirementAnswerComposer.compose(command,
                toolInput("SEED", answer, READ_WRITE_RATIO_ONLY)).answer()).isEqualTo(answer);
    }

    // 앞 역질문에서 이미 들은 시드의 수치는 새어 나간 것이 아니다. 같은 값을 다시 말해야 모순이 생기지 않는다.
    @Test
    void 앞에서_물은_시드의_수치는_유출이_아니다() {
        // given
        SystemDesignRequirementAnswerCommand command = command(new SystemDesignConfirmedRequirements(
                List.of("DAILY_CREATION_VOLUME"), List.of()));
        String answer = "하루 10만 개 기준으로 커스텀 별칭은 이번 범위에서 빼도 돼요.";

        // when & then
        assertThat(SystemDesignRequirementAnswerComposer.compose(command, toolInput("ASSUMED", answer, ALL_NO))
                .answer()).isEqualTo(answer);
    }

    @Test
    void 앞에서_확정한_항목의_수치는_유출이_아니다() {
        // given
        SystemDesignRequirementAnswerCommand command = command(new SystemDesignConfirmedRequirements(List.of(),
                List.of(new SystemDesignRequirementItem("접속 로그 보관", "접속 로그는 5년 보관한다.",
                        SystemDesignRequirementSource.ASSUMED))));
        String answer = "앞에서 말한 대로 접속 로그는 5년 보관해요.";

        // when & then
        assertThat(SystemDesignRequirementAnswerComposer.compose(command, toolInput("ASSUMED", answer, ALL_NO))
                .answer()).isEqualTo(answer);
    }

    // ---------------------------------------------------------------- 확정 항목

    @Test
    void 확정_항목은_이름과_내용을_다듬고_엔티티를_되돌린다() {
        // given
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.set("requirement_items", items(item("  커스텀 별칭\n", " 별칭에는 &lt;영문&gt; &amp; 숫자만 쓴다. ", "ASSUMED")));

        // when & then
        assertThat(compose(toolInput).requirementItems()).containsExactly(new SystemDesignRequirementItem(
                "커스텀 별칭", "별칭에는 <영문> & 숫자만 쓴다.", SystemDesignRequirementSource.ASSUMED));
    }

    // 길이를 넘은 항목을 자르면 뜻이 바뀔 수 있어 통째로 버린다. 항목은 다음 역질문의 문맥과 리포트용이라 버려도 답은 나간다.
    @Test
    void 규칙을_어긴_확정_항목은_잘라내지_않고_버린다() {
        // given
        ObjectNode missingDetail = item("인증", "로그인이 필요하다.", "ASSUMED");
        missingDetail.remove("detail");
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.set("requirement_items", items(
                item("가".repeat(31), "이름이 31자다.", "ASSUMED"),
                item("상세", "가".repeat(151), "ASSUMED"),
                item(" ", "이름이 비었다.", "ASSUMED"),
                missingDetail,
                item("출처", "출처가 소문자다.", "assumed"),
                item("출처", "출처가 enum 밖이다.", "OTHER"),
                objectMapper.createObjectNode().put("name", "출처").put("detail", "출처가 숫자다.").put("source", 1),
                StringNode.valueOf("커스텀 별칭"),
                item("가".repeat(30), "가".repeat(150), "ASSUMED")));

        // when
        SystemDesignRequirementAnswerResult result = compose(toolInput);

        // then
        assertAll(
                () -> assertThat(result.requirementItems()).containsExactly(new SystemDesignRequirementItem(
                        "가".repeat(30), "가".repeat(150), SystemDesignRequirementSource.ASSUMED)),
                () -> assertThat(result.answer()).isEqualTo(CUSTOM_ALIAS_ANSWER),
                () -> assertThat(warnings()).hasSize(8)
                        .allMatch(message -> message.startsWith("시스템 설계 역질문 확정 항목을 버립니다."))
        );
    }

    @Test
    void 확정_항목은_앞에서부터_3개까지만_남긴다() {
        // given
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.set("requirement_items", items(
                item("첫째", "첫째 항목이다.", "ASSUMED"),
                item("둘째", "둘째 항목이다.", "ASSUMED"),
                item("셋째", "셋째 항목이다.", "ASSUMED"),
                item("넷째", "넷째 항목이다.", "ASSUMED")));

        // when & then
        assertAll(
                () -> assertThat(compose(toolInput).requirementItems()).extracting(SystemDesignRequirementItem::name)
                        .containsExactly("첫째", "둘째", "셋째"),
                () -> assertThat(warnings()).singleElement().asString().contains("3개를 넘는 확정 항목", "4")
        );
    }

    @Test
    void 물은_시드가_없으면_SEED_항목을_버린다() {
        // given
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.set("requirement_items", items(
                item("하루 생성량", "하루 평균 10만 개가 만들어진다.", "SEED"),
                item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", "ASSUMED")));

        // when & then
        assertThat(compose(toolInput).requirementItems()).extracting(SystemDesignRequirementItem::name)
                .containsExactly("커스텀 별칭");
    }

    @Test
    void 묻지_않은_시드의_수치가_있는_확정_항목은_버린다() {
        // given
        ObjectNode toolInput = toolInput("SEED", READ_WRITE_RATIO_ANSWER, READ_WRITE_RATIO_ONLY);
        toolInput.set("requirement_items", items(
                item("조회와 생성 비율", "조회는 생성의 100배쯤이다.", "SEED"),
                item("하루 조회량", "하루 10만 개를 기준으로 조회가 100배다.", "SEED")));

        // when
        SystemDesignRequirementAnswerResult result = compose(toolInput);

        // then
        assertAll(
                () -> assertThat(result.requirementItems()).extracting(SystemDesignRequirementItem::name)
                        .containsExactly("조회와 생성 비율"),
                () -> assertThat(warnings()).singleElement().asString().contains("묻지 않은 요구사항의 수치 [10만]")
        );
    }

    // 답을 시드 답으로 바꾸면 LLM이 정한 내용은 면접자에게 나가지 않는다. 답과 항목이 어긋나지 않게 LLM이 정한 항목도 버린다.
    @Test
    void 답을_시드_답으로_바꾸면_ASSUMED_항목을_버리고_SEED로_본다() {
        // given
        ObjectNode toolInput = toolInput("MIXED", "", DAILY_CREATION_VOLUME_ONLY);
        toolInput.set("requirement_items", items(
                item("하루 생성량", "하루 평균 10만 개가 만들어진다.", "SEED"),
                item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", "ASSUMED")));

        // when
        SystemDesignRequirementAnswerResult result = compose(toolInput);

        // then
        assertAll(
                () -> assertThat(result.answer()).isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(result.answerType()).isEqualTo(SystemDesignRequirementAnswerType.SEED),
                () -> assertThat(result.requirementItems())
                        .extracting(SystemDesignRequirementItem::name, SystemDesignRequirementItem::source)
                        .containsExactly(tuple("하루 생성량", SystemDesignRequirementSource.SEED))
        );
    }

    // 답에 LLM이 정한 내용이 들어갔다는 뜻이라 MIXED가 맞다.
    @Test
    void ASSUMED_항목이_남은_SEED_답은_MIXED로_본다() {
        // given
        ObjectNode toolInput = toolInput("SEED", "하루 평균 10만 개 정도이고, 이벤트성 대량 생성은 없어요.",
                DAILY_CREATION_VOLUME_ONLY);
        toolInput.set("requirement_items", items(item("대량 생성", "이벤트성 대량 생성은 없다.", "ASSUMED")));

        // when & then
        assertThat(compose(toolInput).answerType()).isEqualTo(SystemDesignRequirementAnswerType.MIXED);
    }

    @Test
    void 확정_항목_필드가_없거나_배열이_아니면_빈_목록이다() {
        // given
        ObjectNode missing = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        missing.remove("requirement_items");
        ObjectNode object = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        object.set("requirement_items", item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", "ASSUMED"));
        ObjectNode brokenJson = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        brokenJson.put("requirement_items", "[{\"name\": \"커스텀 별칭\"");

        // when & then
        assertAll(
                () -> assertThat(compose(missing).requirementItems()).isEmpty(),
                () -> assertThat(compose(object).requirementItems()).isEmpty(),
                () -> assertThat(compose(brokenJson).requirementItems()).isEmpty(),
                () -> assertThat(compose(missing).answer()).isEqualTo(CUSTOM_ALIAS_ANSWER)
        );
    }

    // 모델이 배열을 JSON 문자열로 감싸 보내는 경우가 있다. 읽을 수 있으면 같은 규칙으로 검사해 쓴다.
    @Test
    void 문자열로_온_확정_항목_배열은_읽어서_검사한다() throws Exception {
        // given
        ObjectNode toolInput = toolInput("ASSUMED", CUSTOM_ALIAS_ANSWER, ALL_NO);
        toolInput.put("requirement_items", objectMapper.writeValueAsString(items(
                item("커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", "ASSUMED"),
                item("하루 생성량", "하루 평균 10만 개가 만들어진다.", "SEED"))));

        // when & then
        assertThat(compose(toolInput).requirementItems()).containsExactly(new SystemDesignRequirementItem(
                "커스텀 별칭", "커스텀 별칭은 이번 범위에서 뺀다.", SystemDesignRequirementSource.ASSUMED));
    }

    // ---------------------------------------------------------------- 로그

    @Test
    void 시드_답으로_바꾸면_이유와_물은_요구사항을_WARN으로_남긴다() {
        // when
        compose(toolInput("SEED", "조회는 생성의 100배쯤이고, 하루에 10만 개가 만들어져요.", READ_WRITE_RATIO_ONLY));
        compose(toolInput("SEED", READ_WRITE_RATIO_ANSWER, READ_WRITE_RATIO_ONLY));

        // then: 바꾼 첫 호출만 남긴다
        assertThat(composerLogs.list).singleElement().satisfies(event -> assertAll(
                () -> assertThat(event.getLevel()).isEqualTo(Level.WARN),
                () -> assertThat(event.getFormattedMessage()).contains("시드 답으로 바꿉니다",
                        "묻지 않은 요구사항의 수치 [10만]", "READ_WRITE_RATIO", "interviewId: 31")
        ));
    }

    @Test
    void 쓸_답이_없어_실패하면_이유를_WARN으로_남긴다() {
        // when
        assertThatThrownBy(() -> compose(toolInput("ASSUMED", "단축 URL은 만든 날부터 5년 동안 보관해요.", ALL_NO)))
                .isInstanceOf(ExternalApiException.class);

        // then
        assertThat(composerLogs.list).singleElement().satisfies(event -> assertAll(
                () -> assertThat(event.getLevel()).isEqualTo(Level.WARN),
                () -> assertThat(event.getFormattedMessage()).contains("실패로 봅니다", "묻지 않은 요구사항의 수치 [5년]",
                        "ASSUMED", "interviewId: 31")
        ));
    }

    private SystemDesignRequirementAnswerResult compose(JsonNode toolInput) {
        return SystemDesignRequirementAnswerComposer.compose(command(), toolInput);
    }

    private String composeWith(SystemDesignRequirementAnswerCommand command, String answer, String... verdicts) {
        ObjectNode toolInput = objectMapper.createObjectNode().put("reasoning", "판정 근거");
        List<Requirement> requirements = command.problem().requirements();
        for (int index = 0; index < requirements.size(); index++) {
            toolInput.put(SystemDesignToolSchemas.requirementFieldName(requirements.get(index).code()),
                    verdicts[index]);
        }
        toolInput.put("answer_type", "MIXED");
        toolInput.set("requirement_items", objectMapper.createArrayNode());
        toolInput.put("answer", answer);
        return SystemDesignRequirementAnswerComposer.compose(command, toolInput).answer();
    }

    private static SystemDesignRequirementAnswerCommand command() {
        return command(SystemDesignConfirmedRequirements.empty());
    }

    private static SystemDesignRequirementAnswerCommand command(SystemDesignConfirmedRequirements confirmed) {
        return new SystemDesignRequirementAnswerCommand(31L, SystemDesignProblemContextFixture.urlShortener(),
                List.of(), confirmed, "역질문");
    }

    // URL 단축기의 요구사항 5개에 문제 순서로 판정을 넣는다. 확정 항목은 빈 배열이다.
    private ObjectNode toolInput(String answerType, String answer, String... verdicts) {
        ObjectNode toolInput = objectMapper.createObjectNode().put("reasoning", "판정 근거");
        for (int index = 0; index < REQUIREMENTS.size(); index++) {
            toolInput.put(SystemDesignToolSchemas.requirementFieldName(REQUIREMENTS.get(index).code()),
                    verdicts[index]);
        }
        toolInput.put("answer_type", answerType);
        toolInput.set("requirement_items", objectMapper.createArrayNode());
        toolInput.put("answer", answer);
        return toolInput;
    }

    private ObjectNode item(String name, String detail, String source) {
        return objectMapper.createObjectNode()
                .put("name", name)
                .put("detail", detail)
                .put("source", source);
    }

    private ArrayNode items(JsonNode... items) {
        ArrayNode array = objectMapper.createArrayNode();
        for (JsonNode item : items) {
            array.add(item);
        }
        return array;
    }

    private List<String> warnings() {
        return composerLogs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
