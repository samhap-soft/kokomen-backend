package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.external.dto.request.SystemDesignBedrockRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerComposer;
import com.samhap.kokomen.interview.tool.SystemDesignToolSchemas;
import com.samhap.kokomen.interview.tool.SystemDesignUserMessages;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * 요구사항 역질문 답(L1). 요청 스레드에서 동기로 부르므로 짧은 타임아웃의 전용 converse 빈을 쓴다.
 * {@code converse}부터 응답 검증까지를 감싸, SDK 예외(소켓 타임아웃, apiCall 타임아웃, 재시도를 다 쓴 스로틀링)와 검증 실패를 모두
 * {@link ExternalApiException}으로 올린다. 최종 답(정해진 문구·폴백)은 서비스가 정한다.
 */
@Slf4j
@ExecutionTimer
@Component
public class SystemDesignRequirementBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    /**
     * 레포에 lombok.config가 없어 필드의 @Qualifier가 @RequiredArgsConstructor 생성자로 옮겨지지 않는다. 그래서 명시적 생성자의
     * 파라미터에 붙인다. 빠지면 공용 converse 빈(@Primary)이 조용히 주입된다(SystemDesignBedrockWiringTest가 고정한다).
     */
    public SystemDesignRequirementBedrockClient(
            @Qualifier("systemDesignBedrockConverseClient") BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    /**
     * @throws ExternalApiException 전송 실패, stopReason이 tool_use가 아님(maxTokens 잘림 포함), 판정 필드 누락·잘못된 값
     */
    public SystemDesignRequirementAnswerResult answerRequirement(SystemDesignRequirementAnswerCommand command) {
        long startedAt = System.currentTimeMillis();
        try {
            return requestAnswer(command);
        } catch (SdkException e) {
            ExternalApiException failure = new ExternalApiException("시스템 설계 역질문 답변 호출에 실패했습니다.", e);
            logFailure(command, startedAt, failure);
            throw failure;
        } catch (RuntimeException e) {
            logFailure(command, startedAt, e);
            throw e;
        }
    }

    private SystemDesignRequirementAnswerResult requestAnswer(SystemDesignRequirementAnswerCommand command) {
        List<Requirement> requirements = command.problem().requirements();
        ConverseResponse response = converseClient.converse(
                SystemDesignBedrockRequestFactory.createRequirementAnswerSystem(command.problem()),
                SystemDesignBedrockRequestFactory.createRequirementAnswerMessages(command),
                SystemDesignBedrockRequestFactory.createRequirementAnswerToolConfig(requirements),
                properties.systemDesignRequirementMaxTokens(),
                properties.evaluationTemperature());
        SystemDesignBedrockCallLogger.logUsage(SystemDesignCallType.REQUIREMENT_ANSWER, command.interviewId(),
                response);
        if (response.stopReason() == StopReason.MAX_TOKENS) {
            log.error("시스템 설계 역질문 답이 maxTokens에서 잘렸습니다. interviewId: {}, maxTokens: {}",
                    command.interviewId(), properties.systemDesignRequirementMaxTokens());
        }
        ToolUseBlock toolUse = converseClient.extractToolUse(response,
                SystemDesignToolSchemas.REQUIREMENT_ANSWER_TOOL_NAME);
        JsonNode toolInput = converseClient.parseToolInput(toolUse, JsonNode.class);
        List<String> askedCodes = SystemDesignRequirementAnswerComposer.resolveAskedCodes(requirements, toolInput);
        return new SystemDesignRequirementAnswerResult(askedCodes,
                SystemDesignUserMessages.unescape(answerOrEmpty(toolInput)));
    }

    // 답이 없거나 문자열이 아니면 빈 답으로 본다. 빈 답은 서비스가 시드 답으로 바꾼다.
    private static String answerOrEmpty(JsonNode toolInput) {
        JsonNode answer = toolInput == null ? null : toolInput.get(SystemDesignToolSchemas.ANSWER_FIELD);
        if (answer == null || !answer.isTextual()) {
            return "";
        }
        return answer.textValue();
    }

    private static void logFailure(SystemDesignRequirementAnswerCommand command, long startedAt, Exception failure) {
        SystemDesignBedrockCallLogger.logFailure(SystemDesignCallType.REQUIREMENT_ANSWER, command.interviewId(),
                System.currentTimeMillis() - startedAt, failure);
    }
}
