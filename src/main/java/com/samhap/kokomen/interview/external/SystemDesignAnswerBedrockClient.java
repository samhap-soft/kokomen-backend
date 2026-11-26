package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignText;
import com.samhap.kokomen.interview.external.dto.request.SystemDesignBedrockRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignEndCommand;
import com.samhap.kokomen.interview.tool.SystemDesignEvaluationEvidenceVerifier;
import com.samhap.kokomen.interview.tool.SystemDesignEvaluationEvidenceVerifier.VerifiedVerdict;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignToolSchemas;
import com.samhap.kokomen.interview.tool.SystemDesignUserMessages;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * 답변 평가(L2, 1~5번째 답변)와 마지막 답변 평가·리포트(L3, 6번째 답변). 비동기 워커에서 부른다.
 * {@code converse}부터 응답 검증까지를 감싸, SDK 예외(소켓 타임아웃, 재시도를 다 쓴 스로틀링, Bedrock 서버 오류 등)와 검증 실패를 모두
 * {@link ExternalApiException}으로 올린다. 실패는 아무것도 저장하지 않고 면접자가 다시 내게 하려는 것이다.
 * 다음 질문만 실패가 아니라 폴백이다(null을 주고 서비스가 기준 질문을 쓴다). 평가 포인트의 MET 판정은 증거가 근거가 되지 않으면
 * 실패가 아니라 NOT_MET으로 내린다({@link SystemDesignEvaluationEvidenceVerifier}).
 */
@Slf4j
@ExecutionTimer
@Component
public class SystemDesignAnswerBedrockClient {

    private static final String ANSWER_CALL_FAILED_MESSAGE = "시스템 설계 답변 평가 호출에 실패했습니다.";
    private static final String END_CALL_FAILED_MESSAGE = "시스템 설계 마지막 답변 평가 호출에 실패했습니다.";
    private static final String MISSING_VALUE = "없음";
    private static final int MAX_RECEIVED_VALUE_LENGTH = 20;

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    /**
     * 채점 전용 converse 빈을 받는다. 공용 converse 빈(@Primary)은 소켓 60초에 SDK 기본 재시도(최대 4회)라 생성이 60초를 넘으면 같은
     * 채점을 네 번 과금한다. 레포에 lombok.config가 없어 명시적 생성자의 파라미터에 @Qualifier를 붙인다. 빠지면 공용 빈이 조용히 주입된다
     * (SystemDesignBedrockWiringTest가 고정한다).
     */
    public SystemDesignAnswerBedrockClient(
            @Qualifier("systemDesignAnswerBedrockConverseClient") BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
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
                nextQuestionOrNull(command.interviewId(), toolInput));
    }

    // 다음 질문만 폴백이 있다. 비었거나 질문 컬럼 한도를 넘으면 null을 주고, 서비스가 기준 질문을 그대로 쓴다.
    private static String nextQuestionOrNull(Long interviewId, JsonNode toolInput) {
        String nextQuestion = textOrEmpty(toolInput, SystemDesignToolSchemas.NEXT_QUESTION_FIELD);
        if (nextQuestion.isEmpty() || nextQuestion.length() > SystemDesignQuestion.MAX_CONTENT_LENGTH) {
            log.warn("시스템 설계 다음 질문을 기준 질문으로 바꿉니다. interviewId: {}, length: {}", interviewId,
                    nextQuestion.length());
            return null;
        }
        return nextQuestion;
    }

    /**
     * @throws ExternalApiException 전송 실패, stopReason이 tool_use가 아님(maxTokens 잘림 포함), rank가 A~F가 아님, feedback이 빔,
     *                              종합 피드백 세 필드가 모두 빔, 평가 포인트 증거 필드 누락·문자열 아님, 판정 필드 누락·잘못된 값
     */
    public SystemDesignEndEvaluation evaluateEnd(SystemDesignEndCommand command) {
        return call(SystemDesignCallType.END, command.interviewId(), END_CALL_FAILED_MESSAGE,
                () -> requestEnd(command));
    }

    private SystemDesignEndEvaluation requestEnd(SystemDesignEndCommand command) {
        int maxTokens = properties.systemDesignEndMaxTokens();
        ConverseResponse response = converseClient.converse(
                SystemDesignBedrockRequestFactory.createEndSystem(command.problem()),
                SystemDesignBedrockRequestFactory.createEndMessages(command),
                SystemDesignBedrockRequestFactory.createEndToolConfig(command.problem().evaluationPoints()),
                maxTokens,
                properties.evaluationTemperature());
        JsonNode toolInput = readToolInput(SystemDesignCallType.END, command.interviewId(), maxTokens, response,
                SystemDesignToolSchemas.END_TOOL_NAME);
        return new SystemDesignEndEvaluation(readRank(toolInput), readFeedback(toolInput), readTotalFeedback(toolInput),
                readPointVerdicts(command, toolInput));
    }

    // 기존 면접의 종합 피드백과 같은 규칙으로 세 필드를 잇는다. 비면 실패이고, interview.total_feedback 한도를 넘으면 자른다.
    private static String readTotalFeedback(JsonNode toolInput) {
        String totalFeedback = new TotalFeedbackResponse(
                textOrEmpty(toolInput, SystemDesignToolSchemas.STRENGTHS_FIELD),
                textOrEmpty(toolInput, SystemDesignToolSchemas.IMPROVEMENTS_FIELD),
                textOrEmpty(toolInput, SystemDesignToolSchemas.LEARNING_DIRECTION_FIELD)).composeTotalFeedback();
        if (SystemDesignText.normalize(totalFeedback).isEmpty()) {
            throw new ExternalApiException("시스템 설계 마지막 답변 평가의 종합 피드백이 비었습니다.");
        }
        return SystemDesignText.truncate(totalFeedback, Interview.MAX_TOTAL_FEEDBACK_LENGTH);
    }

    /**
     * 평가 포인트마다 증거 필드가 문자열이고 판정 필드가 문자열 MET/NOT_MET(대소문자 구분)인지 본다. 하나라도 없거나 다르면 응답 전체를
     * 실패로 본다. 빠진 판정을 NOT_MET으로 조용히 받으면 리포트가 영구히 낮게 굳기 때문이다. 모르는 필드는 무시한다.
     * 모든 포인트를 읽은 뒤 MET 판정을 증거로 검증한다. 증거가 근거가 되지 않으면 실패가 아니라 NOT_MET으로 내리고, 내린 포인트마다
     * WARN 한 줄을 남긴다. 실패한 응답에는 그 WARN을 남기지 않고, 로그에는 증거 원문 대신 내린 이유만 쓴다.
     */
    private static Map<String, Boolean> readPointVerdicts(SystemDesignEndCommand command, JsonNode toolInput) {
        List<PointOutput> outputs = command.problem().evaluationPoints().stream()
                .map(point -> readPointOutput(point, toolInput))
                .toList();
        SystemDesignEvaluationEvidenceVerifier verifier = SystemDesignEvaluationEvidenceVerifier.of(command);
        Map<String, Boolean> metByPointCode = new LinkedHashMap<>();
        for (PointOutput output : outputs) {
            VerifiedVerdict verdict = verifier.verify(output.met(), output.evidence());
            if (verdict.isDowngraded()) {
                log.warn("시스템 설계 평가 포인트 판정을 NOT_MET으로 내립니다. interviewId: {}, code: {}, reason: {}",
                        command.interviewId(), output.code(), verdict.downgradeReason());
            }
            metByPointCode.put(output.code(), verdict.met());
        }
        return metByPointCode;
    }

    private static PointOutput readPointOutput(EvaluationPoint point, JsonNode toolInput) {
        String evidenceFieldName = SystemDesignToolSchemas.pointEvidenceFieldName(point.code());
        String evidence = rawTextOrNull(toolInput, evidenceFieldName);
        if (evidence == null) {
            throw new ExternalApiException("시스템 설계 평가 포인트 증거 필드가 없거나 문자열이 아닙니다. field="
                    + evidenceFieldName + ", value=" + received(toolInput, evidenceFieldName));
        }
        String fieldName = SystemDesignToolSchemas.pointFieldName(point.code());
        String verdict = rawTextOrNull(toolInput, fieldName);
        if (!SystemDesignToolSchemas.POINT_MET.equals(verdict)
                && !SystemDesignToolSchemas.POINT_NOT_MET.equals(verdict)) {
            throw new ExternalApiException("시스템 설계 평가 포인트 판정 필드가 없거나 값이 잘못됐습니다. field=" + fieldName
                    + ", value=" + received(toolInput, fieldName));
        }
        return new PointOutput(point.code(), SystemDesignToolSchemas.POINT_MET.equals(verdict), evidence);
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
        String rank = rawTextOrNull(toolInput, SystemDesignToolSchemas.RANK_FIELD);
        return Arrays.stream(AnswerRank.values())
                .filter(value -> value.name().equals(rank))
                .findFirst()
                .orElseThrow(() -> new ExternalApiException("시스템 설계 답변 평가의 rank가 없거나 A~F가 아닙니다. rank="
                        + received(toolInput, SystemDesignToolSchemas.RANK_FIELD)));
    }

    private static String readFeedback(JsonNode toolInput) {
        String feedback = textOrEmpty(toolInput, SystemDesignToolSchemas.FEEDBACK_FIELD);
        if (feedback.isEmpty()) {
            throw new ExternalApiException("시스템 설계 답변 평가의 feedback이 비었습니다. feedback="
                    + received(toolInput, SystemDesignToolSchemas.FEEDBACK_FIELD));
        }
        return feedback;
    }

    // 실패 원인(없음, 다른 타입, 다른 값)을 가릴 수 있게 받은 값을 JSON 표현으로 짧게 남긴다. LLM 출력을 길게 남기지 않는다.
    private static String received(JsonNode toolInput, String fieldName) {
        JsonNode value = toolInput == null ? null : toolInput.get(fieldName);
        if (value == null) {
            return MISSING_VALUE;
        }
        return SystemDesignText.truncate(value.toString(), MAX_RECEIVED_VALUE_LENGTH);
    }

    // LLM 텍스트 출력. 엔티티를 원래 문자로 되돌리고 앞뒤 공백을 지운다. 없거나 문자열이 아니면 빈 문자열이다.
    private static String textOrEmpty(JsonNode toolInput, String fieldName) {
        return SystemDesignText.normalize(SystemDesignUserMessages.unescape(rawTextOrNull(toolInput, fieldName)));
    }

    private static String rawTextOrNull(JsonNode toolInput, String fieldName) {
        JsonNode value = toolInput == null ? null : toolInput.get(fieldName);
        if (value == null || !value.isTextual()) {
            return null;
        }
        return value.textValue();
    }

    /**
     * 검증하기 전의 평가 포인트 출력. met은 LLM 판정이고, evidence는 엔티티를 되돌리기 전의 증거 필드 값이다.
     */
    private record PointOutput(
            String code,
            boolean met,
            String evidence
    ) {
    }
}
