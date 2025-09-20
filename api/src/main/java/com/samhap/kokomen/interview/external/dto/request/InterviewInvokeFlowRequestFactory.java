package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInputContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest.Builder;

public class InterviewInvokeFlowRequestFactory {

    private static final String IN_PROGRESS_INTERVIEW_PROCEED_FLOW_ID = "EFP2KPF1KA";
    private static final String IN_PORGRESS_INTERVIEW_PROCEED_FLOW_ALIAS_ID = "5BDDAF8XFW";
    private static final String FINISHED_INTERVIEW_PROCEED_FLOW_ID = "2Y5R698F4O";
    private static final String FINISHED_INTERVIEW_PROCEED_FLOW_ALIAS_ID = "U68M2IXVYC";
    private static final String ANSWER_FEEDBACK_FLOW_ID = "ZM3ILHZCC3";
    private static final String ANSWER_FEEDBACK_FLOW_ALIAS_ID = "H8WX8W1J7W";

    private InterviewInvokeFlowRequestFactory() {
    }

    public static InvokeFlowRequest createInterviewProceedInvokeFlowRequest(QuestionAndAnswers questionAndAnswers) {
        FlowInputContent content = FlowInputContent.fromDocument(InterviewMessagesFactory.createInterviewProceedBedrockFlowDocument(questionAndAnswers));
        FlowInput flowInput = FlowInput.builder()
                .nodeName("FlowInputNode")
                .nodeOutputName("document")
                .content(content)
                .build();
        InvokeFlowRequest.Builder builder = InvokeFlowRequest.builder()
                .inputs(flowInput)
                .enableTrace(true);

        return createInterviewProceedInvokeFlowRequest(questionAndAnswers, builder);
    }

    private static InvokeFlowRequest createInterviewProceedInvokeFlowRequest(QuestionAndAnswers questionAndAnswers, Builder builder) {
        if (questionAndAnswers.isProceedRequest()) {
            return builder.flowIdentifier(IN_PROGRESS_INTERVIEW_PROCEED_FLOW_ID)
                    .flowAliasIdentifier(IN_PORGRESS_INTERVIEW_PROCEED_FLOW_ALIAS_ID)
                    .build();
        }

        return builder.flowIdentifier(FINISHED_INTERVIEW_PROCEED_FLOW_ID)
                .flowAliasIdentifier(FINISHED_INTERVIEW_PROCEED_FLOW_ALIAS_ID)
                .build();
    }

    public static InvokeFlowRequest createAnswerFeedbackInvokeFlowRequest(QuestionAndAnswers questionAndAnswers, AnswerRank curAnswerRank) {
        Document document = InterviewMessagesFactory.createAnswerFeedbackBedrockFlowDocument(questionAndAnswers, curAnswerRank);
        FlowInputContent content = FlowInputContent.fromDocument(document);
        FlowInput flowInput = FlowInput.builder()
                .nodeName("FlowInputNode")
                .nodeOutputName("document")
                .content(content)
                .build();

        return InvokeFlowRequest.builder()
                .inputs(flowInput)
                .enableTrace(true)
                .flowIdentifier(ANSWER_FEEDBACK_FLOW_ID)
                .flowAliasIdentifier(ANSWER_FEEDBACK_FLOW_ALIAS_ID)
                .build();
    }
}
