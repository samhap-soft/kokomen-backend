package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.LlmApiException;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.request.InterviewHistory;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInputContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@ExecutionTimer
@RequiredArgsConstructor
@Component
public class BedrockFlowAsyncClient {

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final ObjectMapper objectMapper;

    public void requestToBedrock(QuestionAndAnswers questionAndAnswers, InvokeFlowResponseHandler invokeFlowResponseHandler) throws LlmApiException {
        InvokeFlowRequest invokeFlowRequest = createInvokeFlowRequest(questionAndAnswers);
        bedrockAgentRuntimeAsyncClient.invokeFlow(invokeFlowRequest, invokeFlowResponseHandler);
    }

    private InvokeFlowRequest createInvokeFlowRequest(QuestionAndAnswers questionAndAnswers) {
        InterviewHistory interviewHistory = InterviewHistory.from(questionAndAnswers);

        List<Document> documents = interviewHistory.interviewHistory().stream()
                .map(qna -> Document.fromMap(Map.of(
                        "question", Document.fromString(qna.question()),
                        "answer", Document.fromString(qna.answer())
                )))
                .toList();

        Document document = Document.fromMap(Map.of(
                "interview_history", Document.fromList(documents)
        ));

        FlowInputContent content = FlowInputContent.builder()
                .document(document)
                .build();

        FlowInput flowInput = FlowInput.builder()
                .nodeName("FlowInputNode")
                .nodeOutputName("document")
                .content(content)
                .build();

        InvokeFlowRequest.Builder builder = InvokeFlowRequest.builder()
                .inputs(flowInput)
                .enableTrace(true);

        if (questionAndAnswers.isProceedRequest()) {
            return builder.flowIdentifier("EFP2KPF1KA")
                    .flowAliasIdentifier("6OH93Y3MXT")
                    .build();
        }

        return builder.flowIdentifier("2Y5R698F4O")
                .flowAliasIdentifier("5C5W3WFEHT")
                .build();
    }
}
