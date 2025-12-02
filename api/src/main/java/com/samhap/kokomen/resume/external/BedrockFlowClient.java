package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowResponseStream;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

@Slf4j
@ExecutionTimer
@RequiredArgsConstructor
@Component
public class BedrockFlowClient {

    private static final long TIMEOUT_SECONDS = 60L;

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;

    public String invokeFlow(InvokeFlowRequest request) {
        AtomicReference<String> resultHolder = new AtomicReference<>();
        CompletableFuture<Void> future = new CompletableFuture<>();

        InvokeFlowResponseHandler responseHandler = InvokeFlowResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event -> extractBedrockResult(event, resultHolder)))
                .onComplete(() -> future.complete(null))
                .onError(future::completeExceptionally)
                .build();
        bedrockAgentRuntimeAsyncClient.invokeFlow(request, responseHandler);

        loadingBedrockResult(future);
        String result = resultHolder.get();
        if (result == null || result.isEmpty()) {
            log.error("No response from Bedrock flow");
            throw new ExternalApiException("Bedrock Flow로부터 응답이 없습니다.");
        }
        return result;
    }

    private static void extractBedrockResult(FlowResponseStream event, AtomicReference<String> resultHolder) {
        if (event instanceof FlowOutputEvent outputEvent) {
            String document = outputEvent.content()
                    .document()
                    .toString();
            setDocument(resultHolder, document);
        }
    }

    private static void setDocument(AtomicReference<String> resultHolder, String document) {
        if (document != null && !document.isEmpty()) {
            resultHolder.set(document);
        }
    }

    private static void loadingBedrockResult(CompletableFuture<Void> future) {
        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Bedrock Flow 호출 타임아웃", e);
            throw new ExternalApiException("Bedrock Flow 호출이 시간 초과되었습니다.", e);
        } catch (ExecutionException e) {
            log.error("Bedrock Flow 호출 중 오류 발생", e.getCause());
            throw new ExternalApiException("Bedrock Flow 호출 중 오류가 발생했습니다.", e.getCause());
        } catch (InterruptedException e) {
            log.error("Bedrock Flow 호출이 인터럽트됨", e);
            throw new ExternalApiException("Bedrock Flow 호출이 중단되었습니다.", e);
        }
    }
}
