package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInputContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest.Builder;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@ExecutionTimer
@RequiredArgsConstructor
@Component
public class BedrockFlowAsyncClient {

    private static final String IN_PROGRESS_INTERVIEW_FLOW_ID = "EFP2KPF1KA";
    private static final String IN_PORGRESS_INTERVIEW_FLOW_ALIAS_ID = "6OH93Y3MXT";
    private static final String FINISHED_INTERVIEW_FLOW_ID = "2Y5R698F4O";
    private static final String FINISHED_INTERVIEW_FLOW_ALIAS_ID = "5C5W3WFEHT";

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;

    public void requestToBedrock(QuestionAndAnswers questionAndAnswers, InvokeFlowResponseHandler invokeFlowResponseHandler) {
        InvokeFlowRequest invokeFlowRequest = createInvokeFlowRequest(questionAndAnswers);
        bedrockAgentRuntimeAsyncClient.invokeFlow(invokeFlowRequest, invokeFlowResponseHandler);
    }

    private InvokeFlowRequest createInvokeFlowRequest(QuestionAndAnswers questionAndAnswers) {
        FlowInputContent content = FlowInputContent.fromDocument(InterviewMessagesFactory.createBedrockFlowDocument(questionAndAnswers));
        FlowInput flowInput = FlowInput.builder()
                .nodeName("FlowInputNode")
                .nodeOutputName("document")
                .content(content)
                .build();
        InvokeFlowRequest.Builder builder = InvokeFlowRequest.builder()
                .inputs(flowInput)
                .enableTrace(true);

        return createInvokeFlowRequest(questionAndAnswers, builder);
    }

    private static InvokeFlowRequest createInvokeFlowRequest(QuestionAndAnswers questionAndAnswers, Builder builder) {
        if (questionAndAnswers.isProceedRequest()) {
            return builder.flowIdentifier(IN_PROGRESS_INTERVIEW_FLOW_ID)
                    .flowAliasIdentifier(IN_PORGRESS_INTERVIEW_FLOW_ALIAS_ID)
                    .build();
        }

        return builder.flowIdentifier(FINISHED_INTERVIEW_FLOW_ID)
                .flowAliasIdentifier(FINISHED_INTERVIEW_FLOW_ALIAS_ID)
                .build();
    }
}
