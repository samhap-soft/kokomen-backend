package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInputContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;

public class ResumeInvokeFlowRequestFactory {

    private static final String RESUME_EVALUATION_FLOW_ID = "7PFSJ37K39";
    private static final String RESUME_EVALUATION_FLOW_ALIAS_ID = "UHKJDB0N1Q";

    private ResumeInvokeFlowRequestFactory() {
    }

    public static InvokeFlowRequest createResumeEvaluationFlowRequest(ResumeEvaluationRequest request) {
        Document document = createResumeEvaluationDocument(request);
        FlowInputContent content = FlowInputContent.fromDocument(document);
        FlowInput flowInput = FlowInput.builder()
                .nodeName("FlowInputNode")
                .nodeOutputName("document")
                .content(content)
                .build();

        return InvokeFlowRequest.builder()
                .inputs(flowInput)
                .enableTrace(true)
                .flowIdentifier(RESUME_EVALUATION_FLOW_ID)
                .flowAliasIdentifier(RESUME_EVALUATION_FLOW_ALIAS_ID)
                .build();
    }

    private static Document createResumeEvaluationDocument(ResumeEvaluationRequest request) {
        Map<String, Document> documentMap = new LinkedHashMap<>();
        documentMap.put("resume_text", Document.fromString(request.resume()));
        documentMap.put("portfolio_text", Document.fromString(Objects.toString(request.portfolio(), "")));
        documentMap.put("job_position", Document.fromString(request.jobPosition()));
        documentMap.put("job_description", Document.fromString(Objects.toString(request.jobDescription(), "")));
        documentMap.put("job_career", Document.fromString(request.jobCareer()));
        return Document.fromMap(documentMap);
    }
}
