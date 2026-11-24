package com.samhap.kokomen.interview.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.global.fixture.bedrock.BedrockConversePropertiesFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerTurn;
import com.samhap.kokomen.interview.tool.SystemDesignEndCommand;
import com.samhap.kokomen.interview.tool.SystemDesignNextBaseQuestion;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementTurn;
import com.samhap.kokomen.interview.tool.SystemDesignSystemMessages;
import com.samhap.kokomen.interview.tool.SystemDesignUserMessages;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * 답변 평가(L2)·마지막 답변 평가(L3) 클라이언트의 요청 배선과 응답 검증을 Spring 없이 본다. BedrockConverseClient는 실물로 만들고
 * SDK 클라이언트만 목으로 둬서 캐시포인트 부착, tool_use 추출, 도구 입력 파싱이 실제 코드로 검증되게 한다.
 */
class SystemDesignAnswerBedrockClientTest {

    private static final String ANSWER_TOOL_NAME = "submit_system_design_answer";
    private static final String END_TOOL_NAME = "submit_system_design_end";
    private static final String ANSWER_CALL_FAILED_MESSAGE = "시스템 설계 답변 평가 호출에 실패했습니다.";
    private static final String END_CALL_FAILED_MESSAGE = "시스템 설계 마지막 답변 평가 호출에 실패했습니다.";
    private static final String RANK_INVALID_MESSAGE = "시스템 설계 답변 평가의 rank가 없거나 A~F가 아닙니다. rank=";
    private static final String FEEDBACK_EMPTY_MESSAGE = "시스템 설계 답변 평가의 feedback이 비었습니다. feedback=";
    private static final String TOTAL_FEEDBACK_EMPTY_MESSAGE = "시스템 설계 마지막 답변 평가의 종합 피드백이 비었습니다.";
    private static final long INTERVIEW_ID = 41L;
    private static final String FEEDBACK = "카운터를 Base62로 인코딩해 7자 안에서 충돌을 피한 근거가 분명했어요.";
    private static final String NEXT_QUESTION = "앞에서 두신 카운터를 서버 여러 대가 함께 쓰면 같은 키를 받지 않게 하려면 어떻게 하나요?";
    private static final List<String> POINT_FIELDS = List.of("point_read_path_cache", "point_key_generation_collision",
            "point_storage_estimation", "point_redirect_availability", "point_redirect_status_tradeoff");

    private BedrockRuntimeClient bedrockRuntimeClient;
    private SystemDesignAnswerBedrockClient client;
    private Logger callLogger;
    private ListAppender<ILoggingEvent> callLogs;
    private Logger clientLogger;
    private ListAppender<ILoggingEvent> clientLogs;

    @BeforeEach
    void setUp() {
        bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
        BedrockConverseClient converseClient = new BedrockConverseClient(bedrockRuntimeClient,
                BedrockConversePropertiesFixture.create(), objectMapper());
        client = new SystemDesignAnswerBedrockClient(converseClient, BedrockConversePropertiesFixture.create());
        callLogger = (Logger) LoggerFactory.getLogger(SystemDesignBedrockCallLogger.class);
        callLogs = new ListAppender<>();
        callLogs.start();
        callLogger.addAppender(callLogs);
        clientLogger = (Logger) LoggerFactory.getLogger(SystemDesignAnswerBedrockClient.class);
        clientLogs = new ListAppender<>();
        clientLogs.start();
        clientLogger.addAppender(clientLogs);
    }

    @AfterEach
    void tearDown() {
        callLogger.detachAppender(callLogs);
        clientLogger.detachAppender(clientLogs);
    }

    @Test
    void L2_요청은_maxTokens_2048을_쓴다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", FEEDBACK, NEXT_QUESTION)));
        SystemDesignAnswerCommand command = answerCommand();

        // when
        client.evaluateAnswer(command);

        // then
        ConverseRequest request = captureRequest();
        List<SystemContentBlock> system = request.system();
        assertAll(
                () -> assertThat(request.modelId()).isEqualTo("us.anthropic.claude-sonnet-4-6"),
                () -> assertThat(request.inferenceConfig().maxTokens()).isEqualTo(2048),
                () -> assertThat(request.inferenceConfig().temperature()).isEqualTo(0.2f),
                () -> assertThat(request.toolConfig().tools()).hasSize(1),
                () -> assertThat(request.toolConfig().tools().get(0).toolSpec().name()).isEqualTo(ANSWER_TOOL_NAME),
                () -> assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo(ANSWER_TOOL_NAME),
                () -> assertThat(inputSchemaPropertyNames(request))
                        .containsExactly("reasoning", "rank", "feedback", "next_question"),
                () -> assertThat(system).hasSize(2),
                () -> assertThat(system.get(0).text()).isEqualTo(SystemDesignSystemMessages.answer(command.problem())),
                () -> assertThat(system.get(1).cachePoint()).isNotNull(),
                () -> assertThat(request.messages()).hasSize(1),
                () -> assertThat(request.messages().get(0).role()).isEqualTo(ConversationRole.USER),
                () -> assertThat(request.messages().get(0).content().get(0).text())
                        .isEqualTo(SystemDesignUserMessages.answer(command))
        );
    }

    @Test
    void L3_요청은_maxTokens_3200을_쓴다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(END_TOOL_NAME, endInput("MET", "MET", "MET", "MET", "MET")));
        SystemDesignEndCommand command = endCommand();

        // when
        client.evaluateEnd(command);

        // then
        ConverseRequest request = captureRequest();
        List<SystemContentBlock> system = request.system();
        assertAll(
                () -> assertThat(request.modelId()).isEqualTo("us.anthropic.claude-sonnet-4-6"),
                () -> assertThat(request.inferenceConfig().maxTokens()).isEqualTo(3200),
                () -> assertThat(request.inferenceConfig().temperature()).isEqualTo(0.2f),
                () -> assertThat(request.toolConfig().tools()).hasSize(1),
                () -> assertThat(request.toolConfig().tools().get(0).toolSpec().name()).isEqualTo(END_TOOL_NAME),
                () -> assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo(END_TOOL_NAME),
                () -> assertThat(inputSchemaPropertyNames(request)).containsExactly("reasoning", "rank", "feedback",
                        "strengths", "improvements", "learning_direction",
                        "point_read_path_cache_evidence", "point_read_path_cache",
                        "point_key_generation_collision_evidence", "point_key_generation_collision",
                        "point_storage_estimation_evidence", "point_storage_estimation",
                        "point_redirect_availability_evidence", "point_redirect_availability",
                        "point_redirect_status_tradeoff_evidence", "point_redirect_status_tradeoff"),
                () -> assertThat(system).hasSize(2),
                () -> assertThat(system.get(0).text()).isEqualTo(SystemDesignSystemMessages.end(command.problem())),
                () -> assertThat(system.get(1).cachePoint()).isNotNull(),
                () -> assertThat(request.messages()).hasSize(1),
                () -> assertThat(request.messages().get(0).role()).isEqualTo(ConversationRole.USER),
                () -> assertThat(request.messages().get(0).content().get(0).text())
                        .isEqualTo(SystemDesignUserMessages.end(command))
        );
    }

    // 운영 값에서는 proceed-max-tokens도 2048이라 위 테스트만으로는 어느 키를 읽는지 가려지지 않는다.
    // 다른 키와 겹치지 않는 값으로 답변 평가·마지막 답변 평가 전용 maxTokens와 평가 temperature를 읽는지 본다.
    @Test
    void 요청은_답변_평가_전용_maxTokens와_평가_temperature를_읽는다() {
        // given
        BedrockConverseProperties properties = BedrockConversePropertiesFixture.builder()
                .modelId("system-design-test-model")
                .systemDesignAnswerMaxTokens(1111)
                .systemDesignEndMaxTokens(2222)
                .evaluationTemperature(0.3f)
                .build();
        SystemDesignAnswerBedrockClient distinctClient = new SystemDesignAnswerBedrockClient(
                new BedrockConverseClient(bedrockRuntimeClient, properties, objectMapper()), properties);
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", FEEDBACK, NEXT_QUESTION)),
                toolUseResponse(END_TOOL_NAME, endInput("MET", "MET", "MET", "MET", "MET")));

        // when
        distinctClient.evaluateAnswer(answerCommand());
        distinctClient.evaluateEnd(endCommand());

        // then
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockRuntimeClient, times(2)).converse(captor.capture());
        ConverseRequest answerRequest = captor.getAllValues().get(0);
        ConverseRequest endRequest = captor.getAllValues().get(1);
        assertAll(
                () -> assertThat(answerRequest.modelId()).isEqualTo("system-design-test-model"),
                () -> assertThat(answerRequest.inferenceConfig().maxTokens()).isEqualTo(1111),
                () -> assertThat(answerRequest.inferenceConfig().temperature()).isEqualTo(0.3f),
                () -> assertThat(endRequest.modelId()).isEqualTo("system-design-test-model"),
                () -> assertThat(endRequest.inferenceConfig().maxTokens()).isEqualTo(2222),
                () -> assertThat(endRequest.inferenceConfig().temperature()).isEqualTo(0.3f)
        );
    }

    // 같은 문제면 면접·답변·다이어그램이 달라도 도구와 system 블록(캐시포인트까지)이 글자 그대로 같아 캐시 프리픽스를 함께 쓴다.
    @Test
    void 같은_문제의_다른_답변은_같은_도구와_system_블록으로_요청한다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", FEEDBACK, NEXT_QUESTION)));
        SystemDesignAnswerCommand first = answerCommand();
        SystemDesignAnswerCommand second = new SystemDesignAnswerCommand(42L,
                SystemDesignProblemContextFixture.urlShortener(), List.of(), List.of(),
                new SystemDesignAnswerTurn(1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요.", "API 서버 하나로 시작합니다."),
                SystemDesignDiagramFixture.withCache(),
                new SystemDesignNextBaseQuestion(2, SystemDesignStage.HIGH_LEVEL, "단축 키는 어떻게 만드나요?"));

        // when
        client.evaluateAnswer(first);
        client.evaluateAnswer(second);

        // then
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockRuntimeClient, times(2)).converse(captor.capture());
        ConverseRequest firstRequest = captor.getAllValues().get(0);
        ConverseRequest secondRequest = captor.getAllValues().get(1);
        assertAll(
                () -> assertThat(secondRequest.toolConfig()).isEqualTo(firstRequest.toolConfig()),
                () -> assertThat(secondRequest.system()).isEqualTo(firstRequest.system()),
                () -> assertThat(secondRequest.messages()).isNotEqualTo(firstRequest.messages())
        );
    }

    @Test
    void 같은_문제의_다른_마지막_답변은_같은_도구와_system_블록으로_요청한다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(END_TOOL_NAME, endInput("MET", "MET", "MET", "MET", "MET")));
        SystemDesignEndCommand first = endCommand();
        SystemDesignEndCommand second = new SystemDesignEndCommand(43L,
                SystemDesignProblemContextFixture.urlShortener(), List.of(),
                List.of(new SystemDesignAnswerTurn(5, SystemDesignStage.DEEP_DIVE, "캐시가 내려가면 어떻게 되나요?",
                        "DB로 바로 읽습니다.")),
                new SystemDesignAnswerTurn(6, SystemDesignStage.DEEP_DIVE, "301과 302 중 무엇을 쓰나요?", "301을 씁니다."),
                SystemDesignDiagramFixture.urlShortenerStarter(), List.of("READ_WRITE_RATIO", "CLICK_STATISTICS"));

        // when
        client.evaluateEnd(first);
        client.evaluateEnd(second);

        // then
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockRuntimeClient, times(2)).converse(captor.capture());
        ConverseRequest firstRequest = captor.getAllValues().get(0);
        ConverseRequest secondRequest = captor.getAllValues().get(1);
        assertAll(
                () -> assertThat(secondRequest.toolConfig()).isEqualTo(firstRequest.toolConfig()),
                () -> assertThat(secondRequest.system()).isEqualTo(firstRequest.system()),
                () -> assertThat(secondRequest.messages()).isNotEqualTo(firstRequest.messages())
        );
    }

    // 저장할 값이라 앞뒤 공백은 지운다.
    @Test
    void L2는_rank와_feedback과_다음_질문을_돌려준다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(ANSWER_TOOL_NAME,
                answerInput("B", "  " + FEEDBACK + "\n", "\n" + NEXT_QUESTION + "  ")));

        // when
        SystemDesignAnswerEvaluation evaluation = client.evaluateAnswer(answerCommand());

        // then
        assertAll(
                () -> assertThat(evaluation.rank()).isEqualTo(AnswerRank.B),
                () -> assertThat(evaluation.feedback()).isEqualTo(FEEDBACK),
                () -> assertThat(evaluation.nextQuestion()).isEqualTo(NEXT_QUESTION)
        );
    }

    @Test
    void L3는_평가_포인트_판정을_문제_순서의_code로_돌려준다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(END_TOOL_NAME,
                endInput("MET", "NOT_MET", "MET", "NOT_MET", "NOT_MET")));

        // when
        SystemDesignEndEvaluation evaluation = client.evaluateEnd(endCommand());

        // then
        assertAll(
                () -> assertThat(evaluation.rank()).isEqualTo(AnswerRank.A),
                () -> assertThat(evaluation.feedback()).isEqualTo(FEEDBACK),
                () -> assertThat(evaluation.metByPointCode()).containsExactly(
                        entry("READ_PATH_CACHE", true),
                        entry("KEY_GENERATION_COLLISION", false),
                        entry("STORAGE_ESTIMATION", true),
                        entry("REDIRECT_AVAILABILITY", false),
                        entry("REDIRECT_STATUS_TRADEOFF", false))
        );
    }

    // 기존 면접의 종합 피드백과 같은 규칙이다. 빈 필드는 건너뛰고, 공백은 하나로 줄여 공백 하나로 잇는다.
    @Test
    void 종합_피드백은_세_필드를_공백_하나로_이어_쓰고_빈_필드는_건너뛴다() {
        // given
        Map<String, Document> input = endInput("MET", "MET", "MET", "MET", "MET");
        input.put("strengths", Document.fromString("읽기 경로에 캐시를 둔 근거가 분명했어요."));
        input.put("improvements", Document.fromString("   "));
        input.put("learning_direction", Document.fromString("301과 302의\n차이를  비교해 보세요."));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(END_TOOL_NAME, input));

        // when
        SystemDesignEndEvaluation evaluation = client.evaluateEnd(endCommand());

        // then
        assertThat(evaluation.totalFeedback()).isEqualTo("읽기 경로에 캐시를 둔 근거가 분명했어요. 301과 302의 차이를 비교해 보세요.");
    }

    // 도구 스키마의 enum은 모델에 대한 지시일 뿐이라 서버가 다시 본다. 등급은 대소문자를 구분하고, E는 등급에 없다.
    // 실패 원인(없음, 다른 타입, 다른 값)을 가릴 수 있게 받은 값을 JSON 표현으로 메시지에 남긴다.
    @Test
    void rank가_A부터_F_밖이면_ExternalApiException() {
        // given
        Map<String, Document> withoutRank = answerInput("A", FEEDBACK, NEXT_QUESTION);
        withoutRank.remove("rank");
        Map<String, Document> numberRank = answerInput("A", FEEDBACK, NEXT_QUESTION);
        numberRank.put("rank", Document.fromNumber(1));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("S", FEEDBACK, NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("E", FEEDBACK, NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("a", FEEDBACK, NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput(" A", FEEDBACK, NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("", FEEDBACK, NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, withoutRank),
                toolUseResponse(ANSWER_TOOL_NAME, numberRank),
                toolUseResponse(END_TOOL_NAME, endInputWithRank("S")));

        // when & then
        assertAll(
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "\"S\""),
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "\"E\""),
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "\"a\""),
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "\" A\""),
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "\"\""),
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "없음"),
                () -> assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "1"),
                () -> assertRankInvalid(() -> client.evaluateEnd(endCommand()), "\"S\"")
        );
    }

    // 받은 값이 길어도 LLM 출력을 길게 남기지 않는다. JSON 표현의 앞 20자(여는 따옴표 포함)까지만 쓴다.
    @Test
    void 검증_실패_메시지에는_받은_값을_20자까지만_남긴다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("가".repeat(30), FEEDBACK, NEXT_QUESTION)));

        // when & then
        assertRankInvalid(() -> client.evaluateAnswer(answerCommand()), "\"" + "가".repeat(19));
    }

    @Test
    void feedback이_비면_ExternalApiException() {
        // given
        Map<String, Document> withoutFeedback = answerInput("B", FEEDBACK, NEXT_QUESTION);
        withoutFeedback.remove("feedback");
        Map<String, Document> numberFeedback = answerInput("B", FEEDBACK, NEXT_QUESTION);
        numberFeedback.put("feedback", Document.fromNumber(3));
        Map<String, Document> emptyEndFeedback = endInput("MET", "MET", "MET", "MET", "MET");
        emptyEndFeedback.put("feedback", Document.fromString(" \n "));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", "", NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", "  \t\n", NEXT_QUESTION)),
                toolUseResponse(ANSWER_TOOL_NAME, withoutFeedback),
                toolUseResponse(ANSWER_TOOL_NAME, numberFeedback),
                toolUseResponse(END_TOOL_NAME, emptyEndFeedback));

        // when & then
        assertAll(
                () -> assertFeedbackEmpty(() -> client.evaluateAnswer(answerCommand()), "\"\""),
                () -> assertFeedbackEmpty(() -> client.evaluateAnswer(answerCommand()), "\"  \\t\\n\""),
                () -> assertFeedbackEmpty(() -> client.evaluateAnswer(answerCommand()), "없음"),
                () -> assertFeedbackEmpty(() -> client.evaluateAnswer(answerCommand()), "3"),
                () -> assertFeedbackEmpty(() -> client.evaluateEnd(endCommand()), "\" \\n \"")
        );
    }

    // 다음 질문만 실패가 아니라 폴백이다. 비었거나 질문 컬럼 한도(500자)를 넘으면 null을 주고 서비스가 기준 질문을 그대로 쓴다.
    @Test
    void next_question이_비거나_500자를_넘으면_null() {
        // given
        Map<String, Document> withoutNextQuestion = answerInput("C", FEEDBACK, NEXT_QUESTION);
        withoutNextQuestion.remove("next_question");
        Map<String, Document> numberNextQuestion = answerInput("C", FEEDBACK, NEXT_QUESTION);
        numberNextQuestion.put("next_question", Document.fromNumber(500));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("C", FEEDBACK, "")),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("C", FEEDBACK, " \n ")),
                toolUseResponse(ANSWER_TOOL_NAME, withoutNextQuestion),
                toolUseResponse(ANSWER_TOOL_NAME, numberNextQuestion),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("C", FEEDBACK, "가".repeat(501))),
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("C", FEEDBACK, "가".repeat(500))));

        // when
        List<SystemDesignAnswerEvaluation> evaluations = List.of(client.evaluateAnswer(answerCommand()),
                client.evaluateAnswer(answerCommand()), client.evaluateAnswer(answerCommand()),
                client.evaluateAnswer(answerCommand()), client.evaluateAnswer(answerCommand()),
                client.evaluateAnswer(answerCommand()));

        // then
        assertAll(
                () -> assertThat(evaluations.subList(0, 5)).allSatisfy(evaluation -> assertAll(
                        () -> assertThat(evaluation.nextQuestion()).isNull(),
                        () -> assertThat(evaluation.rank()).isEqualTo(AnswerRank.C),
                        () -> assertThat(evaluation.feedback()).isEqualTo(FEEDBACK)
                )),
                () -> assertThat(evaluations.get(5).nextQuestion()).isEqualTo("가".repeat(500)),
                () -> assertThat(clientLogs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                        .hasSize(5)
                        .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("interviewId: 41"))
        );
    }

    // 500자 한도는 저장할 값(엔티티를 되돌린 값)의 길이로 본다.
    @Test
    void next_question_길이는_엔티티를_되돌린_뒤에_센다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("C", FEEDBACK, "&lt;".repeat(150))));

        // when
        SystemDesignAnswerEvaluation evaluation = client.evaluateAnswer(answerCommand());

        // then
        assertThat(evaluation.nextQuestion()).isEqualTo("<".repeat(150));
    }

    // 빠진 판정을 NOT_MET으로 조용히 받으면 리포트가 영구히 낮게 굳는다. 실패로 두면 아무것도 저장되지 않아 다시 낼 수 있다.
    @Test
    void point_필드가_하나라도_없거나_이상하면_ExternalApiException() {
        // given
        Map<String, Document> withoutLastPoint = endInput("MET", "MET", "MET", "MET", "MET");
        withoutLastPoint.remove("point_redirect_status_tradeoff");
        Map<String, Document> numberPoint = endInput("MET", "MET", "MET", "MET", "MET");
        numberPoint.put("point_storage_estimation", Document.fromNumber(1));
        Map<String, Document> nullPoint = endInput("MET", "MET", "MET", "MET", "MET");
        nullPoint.put("point_redirect_availability", Document.fromNull());
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(END_TOOL_NAME, withoutLastPoint),
                toolUseResponse(END_TOOL_NAME, endInput("met", "MET", "MET", "MET", "MET")),
                toolUseResponse(END_TOOL_NAME, endInput("MET", "YES", "MET", "MET", "MET")),
                toolUseResponse(END_TOOL_NAME, endInput("MET", "MET", "", "MET", "MET")),
                toolUseResponse(END_TOOL_NAME, numberPoint),
                toolUseResponse(END_TOOL_NAME, nullPoint));

        // when & then
        assertAll(
                () -> assertPointInvalid("point_redirect_status_tradeoff", "없음"),
                () -> assertPointInvalid("point_read_path_cache", "\"met\""),
                () -> assertPointInvalid("point_key_generation_collision", "\"YES\""),
                () -> assertPointInvalid("point_storage_estimation", "\"\""),
                () -> assertPointInvalid("point_storage_estimation", "1"),
                () -> assertPointInvalid("point_redirect_availability", "null")
        );
    }

    @Test
    void 모르는_필드는_무시한다() {
        // given
        Map<String, Document> answerInput = answerInput("D", FEEDBACK, NEXT_QUESTION);
        answerInput.put("next_question_draft", Document.fromString("다른 질문"));
        Map<String, Document> endInput = endInput("NOT_MET", "NOT_MET", "NOT_MET", "NOT_MET", "NOT_MET");
        endInput.put("point_unknown", Document.fromString("MAYBE"));
        endInput.put("requirement_daily_creation_volume", Document.fromString("YES"));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput), toolUseResponse(END_TOOL_NAME, endInput));

        // when
        SystemDesignAnswerEvaluation answerEvaluation = client.evaluateAnswer(answerCommand());
        SystemDesignEndEvaluation endEvaluation = client.evaluateEnd(endCommand());

        // then
        assertAll(
                () -> assertThat(answerEvaluation.nextQuestion()).isEqualTo(NEXT_QUESTION),
                () -> assertThat(endEvaluation.metByPointCode()).hasSize(5).doesNotContainKey("UNKNOWN")
                        .containsValues(false).doesNotContainValue(true)
        );
    }

    @Test
    void 종합_피드백_세_필드가_모두_비면_ExternalApiException() {
        // given
        Map<String, Document> missingAll = endInput("MET", "MET", "MET", "MET", "MET");
        missingAll.remove("strengths");
        missingAll.remove("improvements");
        missingAll.remove("learning_direction");
        Map<String, Document> blankAll = endInput("MET", "MET", "MET", "MET", "MET");
        blankAll.put("strengths", Document.fromString(""));
        blankAll.put("improvements", Document.fromString("  "));
        blankAll.put("learning_direction", Document.fromString("\n\t"));
        Map<String, Document> numberAll = endInput("MET", "MET", "MET", "MET", "MET");
        numberAll.put("strengths", Document.fromNumber(1));
        numberAll.put("improvements", Document.fromNumber(2));
        numberAll.put("learning_direction", Document.fromNumber(3));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(END_TOOL_NAME, missingAll), toolUseResponse(END_TOOL_NAME, blankAll),
                toolUseResponse(END_TOOL_NAME, numberAll));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(TOTAL_FEEDBACK_EMPTY_MESSAGE),
                () -> assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(TOTAL_FEEDBACK_EMPTY_MESSAGE),
                () -> assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(TOTAL_FEEDBACK_EMPTY_MESSAGE)
        );
    }

    // interview.total_feedback 컬럼이 2000자라 잇고 난 뒤 자른다.
    @Test
    void 종합_피드백이_2000자를_넘으면_자른다() {
        // given
        Map<String, Document> input = endInput("MET", "MET", "MET", "MET", "MET");
        input.put("strengths", Document.fromString("가".repeat(1_200)));
        input.put("improvements", Document.fromString("나".repeat(900)));
        input.put("learning_direction", Document.fromString("다".repeat(10)));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(END_TOOL_NAME, input));

        // when
        SystemDesignEndEvaluation evaluation = client.evaluateEnd(endCommand());

        // then
        assertThat(evaluation.totalFeedback())
                .hasSize(2_000)
                .isEqualTo("가".repeat(1_200) + " " + "나".repeat(799));
    }

    // 모델이 user 메시지의 엔티티를 출력에 그대로 옮겨 쓸 수 있다. 저장 전에 원래 문자로 되돌리고, &amp;lt;는 &lt;까지만 되돌린다.
    @Test
    void 출력의_엔티티를_원래_문자로_되돌린다() {
        // given
        Map<String, Document> endInput = endInput("MET", "MET", "MET", "MET", "MET");
        endInput.put("feedback", Document.fromString("&lt;캐시&gt; &amp; DB를 나눈 점이 좋아요."));
        endInput.put("strengths", Document.fromString("&lt;api&gt; → &lt;cache&gt; 흐름이 분명했어요."));
        endInput.put("improvements", Document.fromString("장애 &amp; 복구를 짚어 보세요."));
        endInput.put("learning_direction", Document.fromString("&amp;lt;는 그대로예요."));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", "&lt;Redis&gt; &amp; DB를 나눴어요. &amp;lt;는 그대로예요.",
                        "앞에서 두신 &lt;Kafka&gt; &amp; 워커가 멈추면 어떻게 되나요?")),
                toolUseResponse(END_TOOL_NAME, endInput));

        // when
        SystemDesignAnswerEvaluation answerEvaluation = client.evaluateAnswer(answerCommand());
        SystemDesignEndEvaluation endEvaluation = client.evaluateEnd(endCommand());

        // then
        assertAll(
                () -> assertThat(answerEvaluation.feedback()).isEqualTo("<Redis> & DB를 나눴어요. &lt;는 그대로예요."),
                () -> assertThat(answerEvaluation.nextQuestion()).isEqualTo("앞에서 두신 <Kafka> & 워커가 멈추면 어떻게 되나요?"),
                () -> assertThat(endEvaluation.feedback()).isEqualTo("<캐시> & DB를 나눈 점이 좋아요."),
                () -> assertThat(endEvaluation.totalFeedback())
                        .isEqualTo("<api> → <cache> 흐름이 분명했어요. 장애 & 복구를 짚어 보세요. &lt;는 그대로예요.")
        );
    }

    @Test
    void MAX_TOKENS_잘림은_ExternalApiException() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(maxTokensResponse(2_048), maxTokensResponse(3_200));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> client.evaluateAnswer(answerCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessageContaining("stopReason=max_tokens"),
                () -> assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessageContaining("stopReason=max_tokens"),
                () -> assertThat(clientLogs.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .satisfiesExactly(
                                message -> assertThat(message).contains("ANSWER", "interviewId: 41", "maxTokens: 2048"),
                                message -> assertThat(message).contains("END", "interviewId: 41", "maxTokens: 3200")),
                () -> assertFailureLogged("ANSWER", "END")
        );
    }

    @Test
    void 소켓_타임아웃_SdkClientException은_ExternalApiException으로_바꾼다() {
        // given
        SdkClientException socketTimeout = SdkClientException.builder()
                .message("Unable to execute HTTP request: Read timed out")
                .cause(new SocketTimeoutException("Read timed out"))
                .build();
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willThrow(socketTimeout);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> client.evaluateAnswer(answerCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(ANSWER_CALL_FAILED_MESSAGE)
                        .hasCause(socketTimeout),
                () -> assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(END_CALL_FAILED_MESSAGE)
                        .hasCause(socketTimeout),
                () -> assertFailureLogged("ANSWER", "END")
        );
    }

    // 공용 converse 빈은 apiCall 타임아웃이 없고 SDK 기본 재시도를 쓴다. 그래도 다 쓰고 나온 SDK 예외는 같은 실패로 바꾼다.
    @Test
    void ApiCallTimeoutException과_재시도를_다_쓴_스로틀링도_ExternalApiException으로_바꾼다() {
        // given
        ApiCallTimeoutException apiCallTimeout = ApiCallTimeoutException.create(60_000);
        ThrottlingException throttling = ThrottlingException.builder().message("Too many requests").build();
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willThrow(apiCallTimeout, throttling);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> client.evaluateAnswer(answerCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(ANSWER_CALL_FAILED_MESSAGE)
                        .hasCause(apiCallTimeout),
                () -> assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(END_CALL_FAILED_MESSAGE)
                        .hasCause(throttling)
        );
    }

    @Test
    void 사용량을_INFO_한_줄로_남긴다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", FEEDBACK, NEXT_QUESTION),
                        usage(4_200, 650, 3_900, 0)),
                toolUseResponse(END_TOOL_NAME, endInput("MET", "MET", "MET", "MET", "MET"),
                        usage(5_100, 980, 0, 4_300)));

        // when
        client.evaluateAnswer(answerCommand());
        client.evaluateEnd(endCommand());

        // then
        assertThat(callLogs.list).satisfiesExactly(
                event -> assertAll(
                        () -> assertThat(event.getLevel()).isEqualTo(Level.INFO),
                        () -> assertThat(event.getFormattedMessage()).contains("interviewId: 41", "callType: ANSWER",
                                "inputTokens: 4200", "outputTokens: 650", "cacheReadInputTokens: 3900",
                                "cacheWriteInputTokens: 0", "stopReason: tool_use")),
                event -> assertAll(
                        () -> assertThat(event.getLevel()).isEqualTo(Level.INFO),
                        () -> assertThat(event.getFormattedMessage()).contains("interviewId: 41", "callType: END",
                                "inputTokens: 5100", "outputTokens: 980", "cacheReadInputTokens: 0",
                                "cacheWriteInputTokens: 4300", "stopReason: tool_use")));
    }

    @Test
    void usage가_없어도_실패하지_않는다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(
                toolUseResponse(ANSWER_TOOL_NAME, answerInput("B", FEEDBACK, NEXT_QUESTION), null),
                toolUseResponse(END_TOOL_NAME, endInput("MET", "MET", "MET", "MET", "MET"), null));

        // when
        SystemDesignAnswerEvaluation answerEvaluation = client.evaluateAnswer(answerCommand());
        SystemDesignEndEvaluation endEvaluation = client.evaluateEnd(endCommand());

        // then
        assertAll(
                () -> assertThat(answerEvaluation.rank()).isEqualTo(AnswerRank.B),
                () -> assertThat(endEvaluation.rank()).isEqualTo(AnswerRank.A),
                () -> assertThat(callLogs.list).hasSize(2).allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("inputTokens: null", "outputTokens: null"))
        );
    }

    private void assertRankInvalid(Runnable call, String received) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ExternalApiException.class)
                .hasMessage(RANK_INVALID_MESSAGE + received);
    }

    private void assertFeedbackEmpty(Runnable call, String received) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ExternalApiException.class)
                .hasMessage(FEEDBACK_EMPTY_MESSAGE + received);
    }

    private void assertPointInvalid(String fieldName, String received) {
        assertThatThrownBy(() -> client.evaluateEnd(endCommand()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 평가 포인트 판정 필드가 없거나 값이 잘못됐습니다. field=" + fieldName
                        + ", value=" + received);
    }

    private void assertFailureLogged(String... callTypes) {
        assertThat(callLogs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .hasSize(callTypes.length)
                .allSatisfy(message -> assertThat(message).contains("interviewId: 41"))
                .anySatisfy(message -> assertThat(message).contains("callType: " + callTypes[0]))
                .anySatisfy(message -> assertThat(message).contains("callType: " + callTypes[callTypes.length - 1]));
    }

    @SuppressWarnings("unchecked")
    private static List<String> inputSchemaPropertyNames(ConverseRequest request) {
        Map<String, Object> schema = (Map<String, Object>) DocumentJsonConverter.toJavaObject(
                request.toolConfig().tools().get(0).toolSpec().inputSchema().json());
        return List.copyOf(((Map<String, Object>) schema.get("properties")).keySet());
    }

    private ConverseRequest captureRequest() {
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockRuntimeClient, times(1)).converse(captor.capture());
        return captor.getValue();
    }

    private static SystemDesignAnswerCommand answerCommand() {
        return new SystemDesignAnswerCommand(INTERVIEW_ID, SystemDesignProblemContextFixture.urlShortener(),
                List.of(new SystemDesignRequirementTurn("하루에 단축 URL이 몇 개나 만들어지나요?",
                        "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.")),
                List.of(new SystemDesignAnswerTurn(1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요.",
                        "클라이언트 요청은 API 서버로 갑니다.")),
                new SystemDesignAnswerTurn(2, SystemDesignStage.HIGH_LEVEL, "단축 키는 어떻게 만드나요?",
                        "DB 자동 증가 id를 Base62로 인코딩합니다."),
                SystemDesignDiagramFixture.urlShortenerStarter(),
                new SystemDesignNextBaseQuestion(3, SystemDesignStage.HIGH_LEVEL, "데이터가 얼마나 쌓일지 추정해 보세요."));
    }

    private static SystemDesignEndCommand endCommand() {
        return new SystemDesignEndCommand(INTERVIEW_ID, SystemDesignProblemContextFixture.urlShortener(),
                List.of(new SystemDesignRequirementTurn("하루에 단축 URL이 몇 개나 만들어지나요?",
                        "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.")),
                List.of(new SystemDesignAnswerTurn(5, SystemDesignStage.DEEP_DIVE, "DB 서버가 내려가면 어떻게 되나요?",
                        "복제본을 승격합니다.")),
                new SystemDesignAnswerTurn(6, SystemDesignStage.DEEP_DIVE, "301과 302 중 무엇을 쓰나요?",
                        "만료와 차단을 되돌릴 수 있게 302를 씁니다."),
                SystemDesignDiagramFixture.withCache(), List.of("DAILY_CREATION_VOLUME"));
    }

    private static Map<String, Document> answerInput(String rank, String feedback, String nextQuestion) {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("reasoning", Document.fromString("채점 근거와 질문 설계"));
        input.put("rank", Document.fromString(rank));
        input.put("feedback", Document.fromString(feedback));
        input.put("next_question", Document.fromString(nextQuestion));
        return input;
    }

    private static Map<String, Document> endInputWithRank(String rank) {
        Map<String, Document> input = endInput("MET", "MET", "MET", "MET", "MET");
        input.put("rank", Document.fromString(rank));
        return input;
    }

    // 평가 포인트 판정은 문제 순서(POINT_FIELDS)다. 나머지 필드는 성공하는 값으로 채운다.
    private static Map<String, Document> endInput(String... pointVerdicts) {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("reasoning", Document.fromString("마지막 답변 채점 근거와 평가 포인트 판정 근거"));
        input.put("rank", Document.fromString("A"));
        input.put("feedback", Document.fromString(FEEDBACK));
        input.put("strengths", Document.fromString("읽기 경로에 캐시를 둔 근거가 분명했어요."));
        input.put("improvements", Document.fromString("저장 용량을 수치로 추정해 보세요."));
        input.put("learning_direction", Document.fromString("301과 302의 차이를 비교해 보세요."));
        for (int index = 0; index < POINT_FIELDS.size(); index++) {
            input.put(POINT_FIELDS.get(index), Document.fromString(pointVerdicts[index]));
        }
        return input;
    }

    private static ConverseResponse toolUseResponse(String toolName, Map<String, Document> input) {
        return toolUseResponse(toolName, input, usage(4_200, 600, 0, 3_900));
    }

    private static ConverseResponse toolUseResponse(String toolName, Map<String, Document> input, TokenUsage usage) {
        return ConverseResponse.builder()
                .stopReason(StopReason.TOOL_USE)
                .usage(usage)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                        .toolUseId("tool-use-1")
                                        .name(toolName)
                                        .input(Document.fromMap(input))
                                        .build()))
                                .build())
                        .build())
                .build();
    }

    private static ConverseResponse maxTokensResponse(int outputTokens) {
        return ConverseResponse.builder()
                .stopReason(StopReason.MAX_TOKENS)
                .usage(usage(5_000, outputTokens, 0, 4_300))
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("{\"reasoning\": \"가장 최근 답변은"))
                                .build())
                        .build())
                .build();
    }

    private static TokenUsage usage(int inputTokens, int outputTokens, int cacheReadInputTokens,
                                    int cacheWriteInputTokens) {
        return TokenUsage.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .cacheReadInputTokens(cacheReadInputTokens)
                .cacheWriteInputTokens(cacheWriteInputTokens)
                .build();
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
