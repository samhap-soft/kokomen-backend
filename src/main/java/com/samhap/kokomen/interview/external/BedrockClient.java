package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.exception.LlmApiException;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClientResponseException;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

@Slf4j
@RequiredArgsConstructor
@Component
public class BedrockClient {

    private static final String INFERENCE_PROFILE_ID = "apac.anthropic.claude-sonnet-4-20250514-v1:0";

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public BedrockResponse requestToBedrock(QuestionAndAnswers questionAndAnswers) {
        ConverseRequest converseRequest = createConverseRequest(questionAndAnswers);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ConverseResponse converseResponse;
        try {
            converseResponse = bedrockRuntimeClient.converse(converseRequest);
        } catch (RestClientResponseException e) {
            throw new LlmApiException("Bedrock API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new LlmApiException("Bedrock API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }

        stopWatch.stop();
        log.info("Bedrock API 호출 완료 - {}ms", stopWatch.getTotalTimeMillis());

        String rawText = converseResponse.output().message().content().get(0).text();
        String cleanedContent = cleanJsonContent(rawText);

        return new BedrockResponse(cleanedContent);
    }

    private ConverseRequest createConverseRequest(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = InterviewMessagesFactory.createBedrockMessages(questionAndAnswers);

        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(INFERENCE_PROFILE_ID)
                .messages(messages);

        if (questionAndAnswers.isProceedRequest()) {
            Document document = Document.fromString("""
                    {
                        "type": "object",
                        "properties": {
                            "rank": {
                                "type": "string",
                                "description": "사용자 답변에 대한 평가 점수",
                                "enum": ["A", "B", "C", "D", "F"]
                            },
                            "feedback": {
                                "type": "string",
                                "description": "사용자 답변에 대한 피드백"
                            },
                            "next_question": {
                                "type": "string",
                                "description": "현재 문맥과 사용자 답변을 바탕으로 생성된 다음 꼬리 질문"
                            }
                        },
                        "required": ["rank", "feedback", "next_question"]
                    }
                    """);
            Tool tool = Tool.builder()
                    .toolSpec(ToolSpecification.builder()
                            .name("답변에 대한 평가 및 꼬리 질문 생성")
                            .description("답변에 대한 평가 및 꼬리 질문 생성")
                            .inputSchema(schemaBuilder -> schemaBuilder.json(document)).build())
                    .build();
            ToolConfiguration toolConfiguration = ToolConfiguration.builder()
                    .tools(List.of(tool))
                    .build();

            builder.toolConfig(toolConfiguration)
                    .system(InterviewMessagesFactory.createBedrockProceedSystemMessage());
            return builder.build();
        }

        Document document = Document.fromString("""
                {
                    "type": "object",
                    "properties": {
                        "rank": {
                            "type": "string",
                            "description": "사용자 답변에 대한 평가 점수",
                            "enum": ["A", "B", "C", "D", "F"]
                        },
                        "feedback": {
                            "type": "string",
                            "description": "사용자 답변에 대한 피드백"
                        },
                        "total_feedback": {
                            "type": "string",
                            "description": "전체 면접에 대한 피드백"
                        }
                    },
                    "required": ["rank", "feedback", "total_feedback"]
                }
                """);
        Tool tool = Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name("면접 마지막 질문에 대한 평가 및 전체 면접에 대한 피드백")
                        .description("면접 마지막 질문에 대한 평가 및 전체 면접에 대한 피드백")
                        .inputSchema(schemaBuilder -> schemaBuilder.json(document)).build())
                .build();
        ToolConfiguration toolConfiguration = ToolConfiguration.builder()
                .tools(List.of(tool))
                .build();

        builder.toolConfig(toolConfiguration)
                .system(InterviewMessagesFactory.createBedrockEndSystemMessage());
        return builder.build();
    }

    private String cleanJsonContent(String rawText) {
        return rawText
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .replaceAll("`", "")
                .trim();
    }
}
