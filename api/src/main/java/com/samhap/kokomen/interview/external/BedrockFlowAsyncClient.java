package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@ExecutionTimer
@RequiredArgsConstructor
@Component
public class BedrockFlowAsyncClient {

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;

    public void requestToBedrockFlow(InvokeFlowRequest invokeFlowRequest, InvokeFlowResponseHandler invokeFlowResponseHandler) {
        bedrockAgentRuntimeAsyncClient.invokeFlow(invokeFlowRequest, invokeFlowResponseHandler);
    }
}
