package com.samhap.kokomen.interview.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.global.fixture.bedrock.BedrockConversePropertiesFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
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
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * 역질문 답 클라이언트의 요청 배선과 응답 검증을 Spring 없이 본다. BedrockConverseClient는 실물로 만들고 SDK 클라이언트만 목으로 둬서
 * 캐시포인트 부착, tool_use 추출, 도구 입력 파싱이 실제 코드로 검증되게 한다.
 */
class SystemDesignRequirementBedrockClientTest {

    private static final String TOOL_NAME = "submit_requirement_answer";
    private static final String CALL_FAILED_MESSAGE = "시스템 설계 역질문 답변 호출에 실패했습니다.";
    private static final long INTERVIEW_ID = 31L;

    private BedrockRuntimeClient bedrockRuntimeClient;
    private SystemDesignRequirementBedrockClient client;
    private Logger callLogger;
    private ListAppender<ILoggingEvent> callLogs;
    private Logger clientLogger;
    private ListAppender<ILoggingEvent> clientLogs;

    @BeforeEach
    void setUp() {
        bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
        BedrockConverseClient converseClient = new BedrockConverseClient(bedrockRuntimeClient,
                BedrockConversePropertiesFixture.create(), objectMapper());
        client = new SystemDesignRequirementBedrockClient(converseClient, BedrockConversePropertiesFixture.create());
        callLogger = (Logger) LoggerFactory.getLogger(SystemDesignBedrockCallLogger.class);
        callLogs = new ListAppender<>();
        callLogs.start();
        callLogger.addAppender(callLogs);
        clientLogger = (Logger) LoggerFactory.getLogger(SystemDesignRequirementBedrockClient.class);
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
    void 요청은_maxTokens_1024_temperature_0_2와_도구_강제와_캐시포인트를_쓴다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(verdicts("YES", "NO", "NO", "NO", "NO", "하루 평균 10만 개 정도예요.")));
        SystemDesignRequirementAnswerCommand command = command();

        // when
        client.answerRequirement(command);

        // then
        ConverseRequest request = captureRequest();
        List<SystemContentBlock> system = request.system();
        assertAll(
                () -> assertThat(request.modelId()).isEqualTo("us.anthropic.claude-sonnet-4-6"),
                () -> assertThat(request.inferenceConfig().maxTokens()).isEqualTo(1024),
                () -> assertThat(request.inferenceConfig().temperature()).isEqualTo(0.2f),
                () -> assertThat(request.toolConfig().tools()).hasSize(1),
                () -> assertThat(request.toolConfig().tools().get(0).toolSpec().name()).isEqualTo(TOOL_NAME),
                () -> assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo(TOOL_NAME),
                () -> assertThat(system).hasSize(2),
                () -> assertThat(system.get(0).text())
                        .isEqualTo(SystemDesignSystemMessages.requirementAnswer(command.problem())),
                () -> assertThat(system.get(1).cachePoint()).isNotNull(),
                () -> assertThat(request.messages()).hasSize(1),
                () -> assertThat(request.messages().get(0).role()).isEqualTo(ConversationRole.USER),
                () -> assertThat(request.messages().get(0).content().get(0).text())
                        .isEqualTo(SystemDesignUserMessages.requirementAnswer(command))
        );
    }

    // 운영 값에서는 answer-feedback-max-tokens도 1024라 위 테스트만으로는 어느 키를 읽는지 가려지지 않는다.
    // 다른 키와 겹치지 않는 값으로 역질문 전용 maxTokens와 평가 temperature를 읽는지, 이 문제의 판정 필드를 싣는지 본다.
    @Test
    void 요청은_역질문_전용_maxTokens와_평가_temperature를_읽고_문제의_판정_필드를_싣는다() {
        // given
        BedrockConverseProperties properties = BedrockConversePropertiesFixture.builder()
                .modelId("system-design-test-model")
                .systemDesignRequirementMaxTokens(777)
                .evaluationTemperature(0.3f)
                .build();
        SystemDesignRequirementBedrockClient distinctClient = new SystemDesignRequirementBedrockClient(
                new BedrockConverseClient(bedrockRuntimeClient, properties, objectMapper()), properties);
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(verdicts("YES", "NO", "NO", "NO", "NO", "하루 평균 10만 개 정도예요.")));

        // when
        distinctClient.answerRequirement(command());

        // then
        ConverseRequest request = captureRequest();
        assertAll(
                () -> assertThat(request.modelId()).isEqualTo("system-design-test-model"),
                () -> assertThat(request.inferenceConfig().maxTokens()).isEqualTo(777),
                () -> assertThat(request.inferenceConfig().temperature()).isEqualTo(0.3f),
                () -> assertThat(inputSchemaPropertyNames(request)).containsExactly("reasoning",
                        "requirement_daily_creation_volume", "requirement_read_write_ratio",
                        "requirement_retention_period", "requirement_short_key_length",
                        "requirement_click_statistics", "answer")
        );
    }

    // 같은 문제면 면접·이전 역질문·이번 역질문이 달라도 도구와 system 블록(캐시포인트까지)이 글자 그대로 같아 캐시 프리픽스를 함께 쓴다.
    @Test
    void 같은_문제의_다른_역질문은_같은_도구와_system_블록으로_요청한다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(verdicts("YES", "NO", "NO", "NO", "NO", "하루 평균 10만 개 정도예요.")));
        SystemDesignRequirementAnswerCommand first = new SystemDesignRequirementAnswerCommand(41L,
                SystemDesignProblemContextFixture.urlShortener(), List.of(), "하루에 몇 개나 만들어지나요?");
        SystemDesignRequirementAnswerCommand second = new SystemDesignRequirementAnswerCommand(42L,
                SystemDesignProblemContextFixture.urlShortener(),
                List.of(new SystemDesignRequirementTurn("하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.")),
                "그럼 조회는요?");

        // when
        client.answerRequirement(first);
        client.answerRequirement(second);

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
    void YES로_판정한_요구사항_code를_문제_순서로_돌려준다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(
                verdicts("NO", "YES", "NO", "NO", "YES", "조회는 생성의 100배쯤이고, 클릭 통계는 범위에서 빼도 돼요.")));

        // when
        SystemDesignRequirementAnswerResult result = client.answerRequirement(command());

        // then
        assertAll(
                () -> assertThat(result.askedRequirementCodes())
                        .containsExactly("READ_WRITE_RATIO", "CLICK_STATISTICS"),
                () -> assertThat(result.answer()).isEqualTo("조회는 생성의 100배쯤이고, 클릭 통계는 범위에서 빼도 돼요.")
        );
    }

    @Test
    void 모두_NO면_빈_목록이다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(
                verdicts("NO", "NO", "NO", "NO", "NO", "등록되지 않은 내용이에요. 가정하고 진행하세요.")));

        // when
        SystemDesignRequirementAnswerResult result = client.answerRequirement(command());

        // then
        assertThat(result.askedRequirementCodes()).isEmpty();
    }

    @Test
    void 출력의_엔티티를_원래_문자로_되돌린다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(
                verdicts("NO", "NO", "NO", "YES", "NO", "단축 키는 7자 이하 &lt;영문&gt; &amp; 숫자예요. &amp;lt;는 그대로예요.")));

        // when
        SystemDesignRequirementAnswerResult result = client.answerRequirement(command());

        // then
        assertThat(result.answer()).isEqualTo("단축 키는 7자 이하 <영문> & 숫자예요. &lt;는 그대로예요.");
    }

    // answer가 없거나 문자열이 아니면 빈 답으로 받고, 최종 답은 서비스가 시드 답으로 정한다.
    @Test
    void answer가_없거나_문자열이_아니면_빈_답이다() {
        // given
        Map<String, Document> withoutAnswer = verdicts("YES", "NO", "NO", "NO", "NO", "");
        withoutAnswer.remove("answer");
        Map<String, Document> numberAnswer = verdicts("YES", "NO", "NO", "NO", "NO", "");
        numberAnswer.put("answer", Document.fromNumber(100_000));
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(toolUseResponse(withoutAnswer), toolUseResponse(numberAnswer));

        // when
        SystemDesignRequirementAnswerResult first = client.answerRequirement(command());
        SystemDesignRequirementAnswerResult second = client.answerRequirement(command());

        // then
        assertAll(
                () -> assertThat(first.askedRequirementCodes()).containsExactly("DAILY_CREATION_VOLUME"),
                () -> assertThat(first.answer()).isEmpty(),
                () -> assertThat(second.answer()).isEmpty()
        );
    }

    @Test
    void 요구사항_필드가_빠지면_ExternalApiException() {
        // given
        Map<String, Document> input = verdicts("YES", "NO", "NO", "NO", "NO", "하루 평균 10만 개 정도예요.");
        input.remove("requirement_click_statistics");
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(input));

        // when & then
        assertThatThrownBy(() -> client.answerRequirement(command()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_click_statistics");
        assertFailureLogged();
    }

    @Test
    void 판정_값이_YES_NO가_아니면_ExternalApiException() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(
                verdicts("YES", "yes", "NO", "NO", "NO", "하루 평균 10만 개 정도예요.")));

        // when & then
        assertThatThrownBy(() -> client.answerRequirement(command()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_read_write_ratio");
    }

    @Test
    void stopReason이_MAX_TOKENS면_ExternalApiException() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(ConverseResponse.builder()
                .stopReason(StopReason.MAX_TOKENS)
                .usage(usage(3_000, 1_024, 0, 2_500))
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("{\"reasoning\": \"역질문이 묻는 것은"))
                                .build())
                        .build())
                .build());

        // when & then
        assertThatThrownBy(() -> client.answerRequirement(command()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("stopReason=max_tokens");
        assertFailureLogged();
        assertThat(clientLogs.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .contains("interviewId: 31", "maxTokens: 1024");
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
        assertThatThrownBy(() -> client.answerRequirement(command()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage(CALL_FAILED_MESSAGE)
                .hasCause(socketTimeout);
        assertFailureLogged();
    }

    @Test
    void ApiCallTimeoutException은_ExternalApiException으로_바꾼다() {
        // given
        ApiCallTimeoutException apiCallTimeout = ApiCallTimeoutException.create(25_000);
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willThrow(apiCallTimeout);

        // when & then
        assertThatThrownBy(() -> client.answerRequirement(command()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage(CALL_FAILED_MESSAGE)
                .hasCause(apiCallTimeout);
    }

    // 재시도를 다 쓴 스로틀링과 Bedrock의 503도 SDK 예외다. 앱의 503(동시 실행 상한)과 달리 LLM 호출 실패(500)로 바꾼다.
    @Test
    void 재시도를_다_쓴_스로틀링과_Bedrock_503도_ExternalApiException으로_바꾼다() {
        // given
        ThrottlingException throttling = ThrottlingException.builder().message("Too many requests").build();
        ServiceUnavailableException bedrockUnavailable = ServiceUnavailableException.builder()
                .message("Bedrock is unavailable")
                .build();
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willThrow(throttling, bedrockUnavailable);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> client.answerRequirement(command()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(CALL_FAILED_MESSAGE)
                        .hasCause(throttling),
                () -> assertThatThrownBy(() -> client.answerRequirement(command()))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage(CALL_FAILED_MESSAGE)
                        .hasCause(bedrockUnavailable)
        );
    }

    @Test
    void 사용량을_INFO_한_줄로_남긴다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(
                verdicts("YES", "NO", "NO", "NO", "NO", "하루 평균 10만 개 정도예요."), usage(1_650, 210, 1_400, 0)));

        // when
        client.answerRequirement(command());

        // then
        assertThat(callLogs.list).singleElement().satisfies(event -> assertAll(
                () -> assertThat(event.getLevel()).isEqualTo(Level.INFO),
                () -> assertThat(event.getFormattedMessage()).contains("interviewId: 31",
                        "callType: REQUIREMENT_ANSWER", "inputTokens: 1650", "outputTokens: 210",
                        "cacheReadInputTokens: 1400", "cacheWriteInputTokens: 0", "stopReason: tool_use")
        ));
    }

    @Test
    void usage가_없어도_실패하지_않는다() {
        // given
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(toolUseResponse(
                verdicts("YES", "NO", "NO", "NO", "NO", "하루 평균 10만 개 정도예요."), null));

        // when
        SystemDesignRequirementAnswerResult result = client.answerRequirement(command());

        // then
        assertAll(
                () -> assertThat(result.askedRequirementCodes()).containsExactly("DAILY_CREATION_VOLUME"),
                () -> assertThat(callLogs.list).singleElement()
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .asString()
                        .contains("inputTokens: null", "outputTokens: null")
        );
    }

    private void assertFailureLogged() {
        assertThat(callLogs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .contains("interviewId: 31", "callType: REQUIREMENT_ANSWER");
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

    private static SystemDesignRequirementAnswerCommand command() {
        return new SystemDesignRequirementAnswerCommand(INTERVIEW_ID, SystemDesignProblemContextFixture.urlShortener(),
                List.of(new SystemDesignRequirementTurn("단축 키는 몇 자까지 되나요?",
                        "단축 키는 영문 대소문자와 숫자로 7자 이하여야 해요.")),
                "하루에 단축 URL이 몇 개나 만들어지나요?");
    }

    private static Map<String, Document> verdicts(String dailyCreationVolume, String readWriteRatio,
                                                  String retentionPeriod, String shortKeyLength,
                                                  String clickStatistics, String answer) {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("reasoning", Document.fromString("판정 근거"));
        input.put("requirement_daily_creation_volume", Document.fromString(dailyCreationVolume));
        input.put("requirement_read_write_ratio", Document.fromString(readWriteRatio));
        input.put("requirement_retention_period", Document.fromString(retentionPeriod));
        input.put("requirement_short_key_length", Document.fromString(shortKeyLength));
        input.put("requirement_click_statistics", Document.fromString(clickStatistics));
        input.put("answer", Document.fromString(answer));
        return input;
    }

    private static ConverseResponse toolUseResponse(Map<String, Document> input) {
        return toolUseResponse(input, usage(1_650, 180, 0, 1_400));
    }

    private static ConverseResponse toolUseResponse(Map<String, Document> input, TokenUsage usage) {
        return ConverseResponse.builder()
                .stopReason(StopReason.TOOL_USE)
                .usage(usage)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                        .toolUseId("tool-use-1")
                                        .name(TOOL_NAME)
                                        .input(Document.fromMap(input))
                                        .build()))
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
