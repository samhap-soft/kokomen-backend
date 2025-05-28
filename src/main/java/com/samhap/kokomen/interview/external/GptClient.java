package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GptClient {

    private static final String GPT_API_URL = "/v1/chat/completions";

    private final RestClient restClient;
    private final String gptApiKey;

    public GptClient(
            RestClient.Builder builder,
            @Value("${open-ai.api-key}") String gptApiKey
    ) {
        this.restClient = builder.baseUrl("https://api.openai.com")
                .build();
        this.gptApiKey = gptApiKey;
    }

    public GptResponse requestToGpt(GptRequest gptRequest) {
        return restClient.post()
                .uri(GPT_API_URL)
                .header("Authorization", "Bearer " + gptApiKey)
                .body(gptRequest)
                .retrieve()
                .body(GptResponse.class);
    }
}
