package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignText;
import com.samhap.kokomen.interview.external.dto.request.SystemDesignBedrockRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignEndCommand;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignToolSchemas;
import com.samhap.kokomen.interview.tool.SystemDesignUserMessages;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * 답변 평가(L2, 1~5번째 답변)와 마지막 답변 평가·리포트(L3, 6번째 답변). 비동기 워커에서 부른다.
 * {@code converse}부터 응답 검증까지를 감싸, SDK 예외(소켓 타임아웃, apiCall 타임아웃, 재시도를 다 쓴 스로틀링)와 검증 실패를 모두
 * {@link ExternalApiException}으로 올린다. 실패하면 워커가 아무것도 저장하지 않아 면접자가 다시 낼 수 있다.
 * 다음 질문만 실패가 아니라 폴백이다(null을 주고 서비스가 기준 질문을 쓴다).
 */
@Slf4j
@ExecutionTimer
@Component
public class SystemDesignAnswerBedrockClient {

    // interview.total_feedback 컬럼 한도
    public static final int MAX_TOTAL_FEEDBACK_LENGTH = 2_000;

    private static final String ANSWER_CALL_FAILED_MESSAGE = "시스템 설계 답변 평가 호출에 실패했습니다.";
    private static final String END_CALL_FAILED_MESSAGE = "시스템 설계 마지막 답변 평가 호출에 실패했습니다.";

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    /**
     * 워커 스레드에서 부르므로 요청 스레드를 잡지 않는다. 그래서 기존 V2 답변 흐름처럼 공용 converse 빈(@Primary)을 받고, 역질문 답
     * 클라이언트와 달리 @Qualifier를 붙이지 않는다(SystemDesignBedrockWiringTest가 고정한다).
     */
    public SystemDesignAnswerBedrockClient(BedrockConverseClient converseClient, BedrockConverseProperties properties) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    /**
     * @throws ExternalApiException 전송 실패, stopReason이 tool_use가 아님(maxTokens 잘림 포함), rank가 A~F가 아님, feedback이 빔
     */
    public SystemDesignAnswerEvaluation evaluateAnswer(SystemDesignAnswerCommand command) {
        return call(SystemDesignCallType.ANSWER, command.interviewId(), ANSWER_CALL_FAILED_MESSAGE,
                () -> requestAnswer(command));
    }

    private <T> T call(SystemDesignCallType callType, Long interviewId, String failureMessage, Supplier<T> request) {
        long startedAt = System.currentTimeMillis();
        try {
            return request.get();
        } catch (SdkException e) {
            ExternalApiException failure = new ExternalApiException(failureMessage, e);
            SystemDesignBedrockCallLogger.logFailure(callType, interviewId, System.currentTimeMillis() - startedAt,
                    failure);
            throw failure;
        } catch (RuntimeException e) {
            SystemDesignBedrockCallLogger.logFailure(callType, interviewId, System.currentTimeMillis() - startedAt, e);
            throw e;
        }
    }

    private SystemDesignAnswerEvaluation requestAnswer(SystemDesignAnswerCommand command) {
        int maxTokens = properties.systemDesignAnswerMaxTokens();
        ConverseResponse response = converseClient.converse(
                SystemDesignBedrockRequestFactory.createAnswerSystem(command.problem()),
                SystemDesignBedrockRequestFactory.createAnswerMessages(command),
                SystemDesignBedrockRequestFactory.createAnswerToolConfig(),
                maxTokens,
                properties.evaluationTemperature());
        JsonNode toolInput = readToolInput(SystemDesignCallType.ANSWER, command.interviewId(), maxTokens, response,
                SystemDesignToolSchemas.ANSWER_TOOL_NAME);
        return new SystemDesignAnswerEvaluation(readRank(toolInput), readFeedback(toolInput),
                readNextQuestion(command.interviewId(), toolInput));
    }

    // 사용량을 남긴 뒤 tool_use를 꺼낸다. maxTokens에서 잘렸으면 stopReason이 tool_use가 아니라 extractToolUse가 실패시킨다.
    private JsonNode readToolInput(SystemDesignCallType callType, Long interviewId, int maxTokens,
                                   ConverseResponse response, String toolName) {
        SystemDesignBedrockCallLogger.logUsage(callType, interviewId, response);
        if (response.stopReason() == StopReason.MAX_TOKENS) {
            log.error("시스템 설계 LLM 응답이 maxTokens에서 잘렸습니다. callType: {}, interviewId: {}, maxTokens: {}",
                    callType, interviewId, maxTokens);
        }
        ToolUseBlock toolUse = converseClient.extractToolUse(response, toolName);
        return converseClient.parseToolInput(toolUse, JsonNode.class);
    }

    // 등급은 A~F(AnswerRank 이름)를 대소문자까지 그대로 받는다. 도구 스키마의 enum은 모델에 대한 지시일 뿐이라 다시 본다.
    private static AnswerRank readRank(JsonNode toolInput) {
        String rank = readRawText(toolInput, SystemDesignToolSchemas.RANK_FIELD);
        return Arrays.stream(AnswerRank.values())
                .filter(value -> value.name().equals(rank))
                .findFirst()
                .orElseThrow(() -> new ExternalApiException("시스템 설계 답변 평가의 rank가 없거나 A~F가 아닙니다."));
    }

    private static String readFeedback(JsonNode toolInput) {
        String feedback = readText(toolInput, SystemDesignToolSchemas.FEEDBACK_FIELD);
        if (feedback.isEmpty()) {
            throw new ExternalApiException("시스템 설계 답변 평가의 feedback이 비었습니다.");
        }
        return feedback;
    }

    // 다음 질문만 폴백이 있다. 비었거나 질문 컬럼 한도를 넘으면 null을 주고, 서비스가 기준 질문을 그대로 쓴다.
    private static String readNextQuestion(Long interviewId, JsonNode toolInput) {
        String nextQuestion = readText(toolInput, SystemDesignToolSchemas.NEXT_QUESTION_FIELD);
        if (nextQuestion.isEmpty() || nextQuestion.length() > SystemDesignQuestion.MAX_CONTENT_LENGTH) {
            log.warn("시스템 설계 다음 질문을 기준 질문으로 바꿉니다. interviewId: {}, length: {}", interviewId,
                    nextQuestion.length());
            return null;
        }
        return nextQuestion;
    }

    // LLM 텍스트 출력. 엔티티를 원래 문자로 되돌리고 앞뒤 공백을 지운다. 없거나 문자열이 아니면 빈 문자열이다.
    private static String readText(JsonNode toolInput, String fieldName) {
        return SystemDesignText.normalize(SystemDesignUserMessages.unescape(readRawText(toolInput, fieldName)));
    }

    private static String readRawText(JsonNode toolInput, String fieldName) {
        JsonNode value = toolInput == null ? null : toolInput.get(fieldName);
        if (value == null || !value.isTextual()) {
            return null;
        }
        return value.textValue();
    }

    /**
     * @throws ExternalApiException 전송 실패, stopReason이 tool_use가 아님(maxTokens 잘림 포함), rank가 A~F가 아님, feedback이 빔,
     *                              종합 피드백 세 필드가 모두 빔, 평가 포인트 판정 필드 누락·잘못된 값
     */
    public SystemDesignEndEvaluation evaluateEnd(SystemDesignEndCommand command) {
        return call(SystemDesignCallType.END, command.interviewId(), END_CALL_FAILED_MESSAGE,
                () -> requestEnd(command));
    }

    private SystemDesignEndEvaluation requestEnd(SystemDesignEndCommand command) {
        int maxTokens = properties.systemDesignEndMaxTokens();
        List<EvaluationPoint> points = command.problem().evaluationPoints();
        ConverseResponse response = converseClient.converse(
                SystemDesignBedrockRequestFactory.createEndSystem(command.problem()),
                SystemDesignBedrockRequestFactory.createEndMessages(command),
                SystemDesignBedrockRequestFactory.createEndToolConfig(points),
                maxTokens,
                properties.evaluationTemperature());
        JsonNode toolInput = readToolInput(SystemDesignCallType.END, command.interviewId(), maxTokens, response,
                SystemDesignToolSchemas.END_TOOL_NAME);
        return new SystemDesignEndEvaluation(readRank(toolInput), readFeedback(toolInput), readTotalFeedback(toolInput),
                readPointVerdicts(points, toolInput));
    }

    // 기존 면접의 종합 피드백과 같은 규칙으로 세 필드를 잇는다. 비면 실패이고, interview.total_feedback 한도를 넘으면 자른다.
    private static String readTotalFeedback(JsonNode toolInput) {
        String totalFeedback = new TotalFeedbackResponse(
                readText(toolInput, SystemDesignToolSchemas.STRENGTHS_FIELD),
                readText(toolInput, SystemDesignToolSchemas.IMPROVEMENTS_FIELD),
                readText(toolInput, SystemDesignToolSchemas.LEARNING_DIRECTION_FIELD)).composeTotalFeedback();
        if (SystemDesignText.normalize(totalFeedback).isEmpty()) {
            throw new ExternalApiException("시스템 설계 마지막 답변 평가의 종합 피드백이 비었습니다.");
        }
        return SystemDesignText.truncate(totalFeedback, MAX_TOTAL_FEEDBACK_LENGTH);
    }

    /**
     * 평가 포인트마다 판정 필드가 문자열 MET/NOT_MET(대소문자 구분)인지 본다. 하나라도 없거나 다른 값이면 응답 전체를 실패로 본다.
     * 빠진 판정을 NOT_MET으로 조용히 받으면 리포트가 영구히 낮게 굳기 때문이다. 모르는 필드는 무시한다.
     */
    private static Map<String, Boolean> readPointVerdicts(List<EvaluationPoint> points, JsonNode toolInput) {
        Map<String, Boolean> metByPointCode = new LinkedHashMap<>();
        for (EvaluationPoint point : points) {
            String fieldName = SystemDesignToolSchemas.pointFieldName(point.code());
            String verdict = readRawText(toolInput, fieldName);
            if (!SystemDesignToolSchemas.POINT_MET.equals(verdict)
                    && !SystemDesignToolSchemas.POINT_NOT_MET.equals(verdict)) {
                throw new ExternalApiException("시스템 설계 평가 포인트 판정 필드가 없거나 값이 잘못됐습니다. field=" + fieldName);
            }
            metByPointCode.put(point.code(), SystemDesignToolSchemas.POINT_MET.equals(verdict));
        }
        return metByPointCode;
    }
}
