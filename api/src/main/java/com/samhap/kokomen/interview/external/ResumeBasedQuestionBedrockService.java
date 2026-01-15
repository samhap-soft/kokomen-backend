package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.external.dto.response.QuestionResponseWrapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInputContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowResponseStream;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@Slf4j
@Service
public class ResumeBasedQuestionBedrockService {

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final ResumeBasedQuestionGptClient gptClient;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;
    private final String flowId;
    private final String flowAliasId;

    public ResumeBasedQuestionBedrockService(
            BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient,
            ResumeBasedQuestionGptClient gptClient,
            ObjectMapper objectMapper,
            @Qualifier("gptCallbackExecutor")
            ThreadPoolTaskExecutor executor,
            @Value("${bedrock.flow.resume-based-question.flow-id:}")
            String flowId,
            @Value("${bedrock.flow.resume-based-question.flow-alias-id:}")
            String flowAliasId
    ) {
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
        this.gptClient = gptClient;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.flowId = flowId;
        this.flowAliasId = flowAliasId;
    }

    public List<GeneratedQuestionDto> generateQuestions(
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount
    ) {
        if (!isBedrockFlowConfigured()) {
            log.info("Bedrock Flow ID 미설정, GPT로 질문 생성");
            return generateQuestionsWithGpt(resumeText, portfolioText, jobCareer, questionCount);
        }

        try {
            return generateQuestionsWithBedrock(resumeText, portfolioText, jobCareer, questionCount);
        } catch (Exception e) {
            log.error("Bedrock 질문 생성 실패, GPT 폴백 시도", e);
            return generateQuestionsWithGpt(resumeText, portfolioText, jobCareer, questionCount);
        }
    }

    private boolean isBedrockFlowConfigured() {
        return flowId != null && !flowId.isBlank()
                && flowAliasId != null && !flowAliasId.isBlank();
    }

    private List<GeneratedQuestionDto> generateQuestionsWithBedrock(
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount
    ) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        CompletableFuture<List<GeneratedQuestionDto>> future = new CompletableFuture<>();
        InvokeFlowRequest flowRequest = createInvokeFlowRequest(resumeText, portfolioText, jobCareer, questionCount);

        bedrockAgentRuntimeAsyncClient.invokeFlow(
                flowRequest,
                createResponseHandler(future, resumeText, portfolioText, jobCareer, questionCount, mdcContext)
        );
        return future.join();
    }

    private InvokeFlowRequest createInvokeFlowRequest(
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount
    ) {
        Map<String, Document> documentMap = Map.of(
                "resume_text", Document.fromString(resumeText),
                "portfolio_text", Document.fromString(portfolioText != null ? portfolioText : ""),
                "job_career", Document.fromString(jobCareer),
                "question_count", Document.fromNumber(questionCount)
        );

        FlowInputContent content = FlowInputContent.fromDocument(Document.fromMap(documentMap));
        FlowInput flowInput = FlowInput.builder()
                .nodeName("FlowInputNode")
                .nodeOutputName("document")
                .content(content)
                .build();

        return InvokeFlowRequest.builder()
                .inputs(flowInput)
                .enableTrace(true)
                .flowIdentifier(flowId)
                .flowAliasIdentifier(flowAliasId)
                .build();
    }

    private InvokeFlowResponseHandler createResponseHandler(
            CompletableFuture<List<GeneratedQuestionDto>> future,
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount,
            Map<String, String> mdcContext
    ) {
        return InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event ->
                        executor.execute(() ->
                                handleBedrockResponse(event, future, mdcContext))))
                .onError(ex ->
                        executor.execute(() ->
                                handleBedrockError(ex, future, resumeText, portfolioText, jobCareer, questionCount,
                                        mdcContext)))
                .build();
    }

    private void handleBedrockResponse(
            FlowResponseStream event,
            CompletableFuture<List<GeneratedQuestionDto>> future,
            Map<String, String> mdcContext
    ) {
        try {
            setMdcContext(mdcContext);
            if (event instanceof FlowOutputEvent outputEvent) {
                String jsonPayload = outputEvent.content().document().toString();
                List<GeneratedQuestionDto> questions = parseQuestionResponse(jsonPayload);
                future.complete(questions);
            }
        } catch (Exception e) {
            log.error("Bedrock 응답 파싱 실패", e);
            future.completeExceptionally(e);
        } finally {
            MDC.clear();
        }
    }

    private void handleBedrockError(
            Throwable ex,
            CompletableFuture<List<GeneratedQuestionDto>> future,
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount,
            Map<String, String> mdcContext
    ) {
        try {
            setMdcContext(mdcContext);
            log.error("Bedrock 호출 실패, GPT 폴백 시도", ex);
            List<GeneratedQuestionDto> questions = generateQuestionsWithGpt(
                    resumeText, portfolioText, jobCareer, questionCount);
            future.complete(questions);
        } catch (Exception e) {
            log.error("GPT 폴백 실패", e);
            future.completeExceptionally(e);
        } finally {
            MDC.clear();
        }
    }

    private List<GeneratedQuestionDto> generateQuestionsWithGpt(
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount
    ) {
        String jsonResponse = gptClient.generateQuestions(resumeText, portfolioText, jobCareer, questionCount);
        return parseQuestionResponse(jsonResponse);
    }

    private List<GeneratedQuestionDto> parseQuestionResponse(String jsonResponse) {
        try {
            String cleanedJson = cleanJsonContent(jsonResponse);
            QuestionResponseWrapper wrapper = objectMapper.readValue(cleanedJson, QuestionResponseWrapper.class);
            return wrapper.questions();
        } catch (JsonProcessingException e) {
            log.error("질문 응답 파싱 실패: {}", jsonResponse, e);
            throw new ExternalApiException("질문 응답을 파싱하는데 실패했습니다.");
        }
    }

    private String cleanJsonContent(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return rawText;
        }
        String cleaned = rawText
                .replace("```json", "")
                .replace("```", "")
                .replace("`", "")
                .trim();

        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            String unwrapped = cleaned.substring(1, cleaned.length() - 1);
            return unwrapped.replace("\\\"", "\"").replace("\\n", "\n");
        }
        return cleaned;
    }

    private void setMdcContext(Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }
}
