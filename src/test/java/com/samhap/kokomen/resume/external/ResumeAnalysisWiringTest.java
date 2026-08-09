package com.samhap.kokomen.resume.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisBedrockRequestFactory;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionResult;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * 신규 이력서 분석 LLM 콜의 배선(모델 파라미터·toolChoice·캐시포인트·system/user 메시지 구성)을 Spring 기동 없이 검증한다.
 * BedrockConverseClient는 실물로 생성하고 AWS SDK 레벨(BedrockRuntimeClient)만 목으로 잡는다.
 * ResumeAnalysisBedrockRequestFactory의 createEvaluationSystem/createQuestionGenerationSystem은
 * ResumeAnalysisSystemMessages를 그대로 감싸는 3줄짜리 래퍼라, 아래 두 "단일_소스" 테스트는 독립된
 * 두 렌더러를 비교하는 게 아니라 누군가 그 래퍼 안에 문자열을 인라인해 넣는 실수만 잡아낸다.
 */
class ResumeAnalysisWiringTest {

    private static final int EVALUATION_MAX_TOKENS = 16000;
    private static final int QUESTION_MAX_TOKENS = 2048;

    private BedrockRuntimeClient bedrockRuntimeClient;
    private ResumeAnalysisEvaluationBedrockClient evaluationBedrockClient;
    private ResumeAnalysisQuestionBedrockClient questionBedrockClient;

    @BeforeEach
    void setUp() {
        bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
        BedrockConverseProperties properties = new BedrockConverseProperties(
                "test-model-id", 2048, 4096, 1024, QUESTION_MAX_TOKENS, EVALUATION_MAX_TOKENS, 0.2f, 0.7f, 0.5f);
        BedrockConverseClient converseClient = new BedrockConverseClient(
                bedrockRuntimeClient, properties, objectMapper());
        evaluationBedrockClient = new ResumeAnalysisEvaluationBedrockClient(converseClient, properties);
        questionBedrockClient = new ResumeAnalysisQuestionBedrockClient(converseClient, properties);
    }

    @Test
    void 평가_콜은_temperature_0점2와_maxTokens_16000으로_호출된다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(evaluationResponse(true));

        ResumeAnalysisEvaluation evaluation = evaluationBedrockClient.evaluate(command(true));

        ConverseRequest request = captureRequests(1).get(0);
        assertThat(request.modelId()).isEqualTo("test-model-id");
        assertThat(request.inferenceConfig().temperature()).isEqualTo(0.2f);
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(EVALUATION_MAX_TOKENS);
        assertThat(evaluation.totalScore()).isEqualTo(74);
    }

    @Test
    void 질문_콜은_temperature_0점7과_maxTokens_2048으로_호출된다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(questionsResponse());

        ResumeAnalysisQuestionResult result = questionBedrockClient.generateQuestions(questionCommand());

        ConverseRequest request = captureRequests(1).get(0);
        assertThat(request.inferenceConfig().temperature()).isEqualTo(0.7f);
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(QUESTION_MAX_TOKENS);
        assertThat(result.questions()).hasSize(5);
    }

    @Test
    void 평가_콜과_질문_콜이_이_순서로_정확히_한_번씩_호출된다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(evaluationResponse(true), questionsResponse());

        evaluationBedrockClient.evaluate(command(true));
        questionBedrockClient.generateQuestions(questionCommand());

        List<ConverseRequest> requests = captureRequests(2);
        assertThat(toolName(requests.get(0))).isEqualTo(ResumeAnalysisToolNames.EVALUATION);
        assertThat(toolName(requests.get(1))).isEqualTo(ResumeAnalysisToolNames.QUESTION_GENERATION);
    }

    @Test
    void 두_콜_모두_system_블록_마지막에_캐시포인트가_붙는다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(evaluationResponse(true), questionsResponse());

        evaluationBedrockClient.evaluate(command(true));
        questionBedrockClient.generateQuestions(questionCommand());

        for (ConverseRequest request : captureRequests(2)) {
            List<SystemContentBlock> system = request.system();
            assertThat(system.get(system.size() - 1).cachePoint()).isNotNull();
            assertThat(system.get(0).text()).isNotBlank();
        }
    }

    @Test
    void 평가_콜의_toolChoice는_평가_도구로_강제된다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(evaluationResponse(true));

        evaluationBedrockClient.evaluate(command(true));

        ConverseRequest request = captureRequests(1).get(0);
        assertThat(request.toolConfig().toolChoice().tool().name())
                .isEqualTo(ResumeAnalysisToolNames.EVALUATION);
    }

    @Test
    void 질문_콜의_toolChoice는_질문_도구로_강제된다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(questionsResponse());

        questionBedrockClient.generateQuestions(questionCommand());

        ConverseRequest request = captureRequests(1).get(0);
        assertThat(request.toolConfig().toolChoice().tool().name())
                .isEqualTo(ResumeAnalysisToolNames.QUESTION_GENERATION);
    }

    @Test
    void JD가_없으면_평가_콜의_toolConfig에_jd_fit_필드가_없다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(evaluationResponse(false));

        ResumeAnalysisEvaluation evaluation = evaluationBedrockClient.evaluate(command(false));

        ConverseRequest request = captureRequests(1).get(0);
        assertThat(schemaPropertyKeys(request)).noneMatch(key -> key.startsWith("jd_fit"));
        assertThat(evaluation.jdFit()).isNull();
        assertThat(evaluation.totalScore()).isEqualTo(78);
    }

    @Test
    void JD가_있으면_평가_콜의_toolConfig에_jd_fit_필드가_있다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(evaluationResponse(true));

        evaluationBedrockClient.evaluate(command(true));

        assertThat(schemaPropertyKeys(captureRequests(1).get(0))).contains("jd_fit_score");
    }

    @Test
    void 질문_콜의_user_메시지에만_evaluation_result가_들어간다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(evaluationResponse(true), questionsResponse());

        evaluationBedrockClient.evaluate(command(true));
        questionBedrockClient.generateQuestions(questionCommand());

        List<ConverseRequest> requests = captureRequests(2);
        assertThat(userText(requests.get(0))).doesNotContain("<evaluation_result>");
        assertThat(userText(requests.get(1)))
                .contains("<evaluation_result>")
                .contains("렌더된 평가 결과")
                .contains("<resume>")
                .contains("<target_position>")
                .contains("<job_career>");
    }

    @Test
    void JD가_없으면_평가_user_메시지에_채용공고_태그가_없다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(evaluationResponse(false));

        evaluationBedrockClient.evaluate(command(false));

        assertThat(userText(captureRequests(1).get(0))).doesNotContain("<job_requirements>");
    }

    @Test
    void JD가_있으면_평가_user_메시지에_채용공고가_들어간다() {
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class))).willReturn(evaluationResponse(true));

        evaluationBedrockClient.evaluate(command(true));

        assertThat(userText(captureRequests(1).get(0)))
                .contains("<job_requirements>")
                .contains("공고 본문");
    }

    @Test
    void 평가_시스템_메시지는_단일_소스에서_나온다() {
        for (boolean jdProvided : List.of(true, false)) {
            String bedrockSystem = ResumeAnalysisBedrockRequestFactory.createEvaluationSystem(jdProvided)
                    .get(0).text();

            assertThat(bedrockSystem).as("jdProvided=%s의 system 메시지", jdProvided)
                    .isEqualTo(ResumeAnalysisSystemMessages.evaluation(jdProvided));
        }
    }

    @Test
    void 질문_시스템_메시지는_단일_소스에서_나온다() {
        String bedrockSystem = ResumeAnalysisBedrockRequestFactory.createQuestionGenerationSystem()
                .get(0).text();

        assertThat(bedrockSystem).isEqualTo(ResumeAnalysisSystemMessages.questionGeneration());
    }

    private ResumeAnalysisCommand command(boolean jdProvided) {
        return new ResumeAnalysisCommand(1L, 2L, jdProvided, "이력서 본문", "포트폴리오 본문",
                "백엔드 개발자", jdProvided ? "공고 본문" : null, "3년차");
    }

    private ResumeAnalysisQuestionCallCommand questionCommand() {
        return ResumeAnalysisQuestionCallCommand.of(command(true), "렌더된 평가 결과");
    }

    // 기대 호출 횟수를 인자로 받는다. 실제 호출 수를 읽어 times()에 넣으면 verify가 절대 실패하지 않아 단정 가치가 0이 된다.
    private List<ConverseRequest> captureRequests(int expectedCallCount) {
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockRuntimeClient, times(expectedCallCount)).converse(captor.capture());
        return captor.getAllValues();
    }

    private String toolName(ConverseRequest request) {
        return request.toolConfig().tools().get(0).toolSpec().name();
    }

    private String userText(ConverseRequest request) {
        return request.messages().get(0).content().get(0).text();
    }

    @SuppressWarnings("unchecked")
    private List<String> schemaPropertyKeys(ConverseRequest request) {
        Map<String, Object> schema = (Map<String, Object>) DocumentJsonConverter.toJavaObject(
                request.toolConfig().tools().get(0).toolSpec().inputSchema().json());
        return List.copyOf(((Map<String, Object>) schema.get("properties")).keySet());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private ConverseResponse evaluationResponse(boolean jdProvided) {
        Map<String, Document> input = new LinkedHashMap<>();
        putDimension(input, "problem_solving", 90);
        putDimension(input, "project_experience", 80);
        putDimension(input, "technical_skills", 70);
        putDimension(input, "soft_skills", 60);
        if (jdProvided) {
            putDimension(input, "jd_fit", 50);
        }
        input.put("total_feedback", Document.fromString("종합 총평"));
        return toolUseResponse(ResumeAnalysisToolNames.EVALUATION, input);
    }

    private void putDimension(Map<String, Document> input, String key, int score) {
        input.put(key + "_reasoning", Document.fromString("사고 과정"));
        input.put(key + "_score", Document.fromNumber(score));
        input.put(key + "_reason", Document.fromList(List.of(
                Document.fromString("근거1"), Document.fromString("근거2"))));
        input.put(key + "_improvements", Document.fromList(List.of(
                Document.fromString("보완1"), Document.fromString("보완2"))));
    }

    private ConverseResponse questionsResponse() {
        List<Document> questions = List.of(
                questionDocument(1), questionDocument(2), questionDocument(3),
                questionDocument(4), questionDocument(5));
        return toolUseResponse(ResumeAnalysisToolNames.QUESTION_GENERATION,
                Map.of("questions", Document.fromList(questions)));
    }

    private Document questionDocument(int index) {
        return Document.fromMap(Map.of(
                "question", Document.fromString("질문 " + index),
                "reason", Document.fromString("이유 " + index)));
    }

    private ConverseResponse toolUseResponse(String toolName, Map<String, Document> input) {
        return ConverseResponse.builder()
                .stopReason(StopReason.TOOL_USE)
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
}
