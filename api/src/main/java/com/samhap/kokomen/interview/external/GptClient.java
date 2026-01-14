package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.BaseGptClient;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.request.GptMessage;
import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.Message;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@ExecutionTimer
@Component
public class GptClient extends BaseGptClient {

    public GptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${open-ai.api-key}") String gptApiKey
    ) {
        super(builder, objectMapper, gptApiKey);
    }

    public GptResponse requestToGpt(QuestionAndAnswers questionAndAnswers) {
        GptRequest gptRequest = createGptRequest(questionAndAnswers);
        return executeRequest(gptRequest, GptResponse.class);
    }

    private GptRequest createGptRequest(QuestionAndAnswers questionAndAnswers) {
        if (questionAndAnswers.isProceedRequest()) {
            List<GptMessage> gptMessages = InterviewMessagesFactory.createGptProceedMessages(questionAndAnswers);
            return GptRequest.createProceedGptRequest(gptMessages);
        }
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptEndMessages(questionAndAnswers);
        return GptRequest.createEndGptRequest(gptMessages);
    }

    @Override
    protected void validateResponse(Object response) {
        if (response == null) {
            throw new ExternalApiException("GPT API로부터 유효한 응답을 받지 못했습니다.");
        }
        if (!(response instanceof GptResponse gptResponse)) {
            throw new ExternalApiException("GPT API로부터 예기치 않은 타입의 응답을 받았습니다: " + response.getClass().getName());
        }

        if (gptResponse.choices() == null || gptResponse.choices().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 choices가 없습니다.");
        }
        Message message = gptResponse.choices().get(0).message();
        if (message == null) {
            throw new ExternalApiException("GPT API 응답에 message가 없습니다.");
        }
        if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 tool_calls가 없습니다.");
        }
    }
}
