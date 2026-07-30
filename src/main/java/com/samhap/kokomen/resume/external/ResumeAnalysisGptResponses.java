package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponse;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponseMessage;

/**
 * 신규 이력서 분석 GPT 폴백 클라이언트 두 개가 공유하는 응답 처리. 평가 클라이언트와 질문 클라이언트는
 * 요청 형상과 파싱 대상만 다르고 응답 껍데기(choices -> message -> tool_calls)와 이중 인코딩 처리는 동일하다.
 * 두 클라이언트에 같은 블록을 복사해 두면 한쪽만 고쳐지는 표류가 생기므로 여기 한 곳에만 둔다.
 */
public final class ResumeAnalysisGptResponses {

    private ResumeAnalysisGptResponses() {
    }

    /**
     * GPT가 tool_calls.arguments를 이중 인코딩해 보내는 경우가 있어 한 겹 벗긴다.
     * 감싸여 있지 않으면 원본을 그대로 반환한다.
     */
    public static String unwrapJsonString(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        return json;
    }

    /**
     * tool_calls 첫 항목까지 도달할 수 있는지 단계별로 검증한다. 각 단계에서 실패 지점을 특정해
     * 예외 메시지에 남기므로, 뒤에서 인덱스 접근이 NPE나 IndexOutOfBounds로 터지지 않는다.
     */
    public static void validate(Object response) {
        if (response == null) {
            throw new ExternalApiException("GPT API로부터 유효한 응답을 받지 못했습니다.");
        }
        if (!(response instanceof ResumeGptResponse gptResponse)) {
            throw new ExternalApiException(
                    "GPT API로부터 예기치 않은 타입의 응답을 받았습니다: " + response.getClass().getName());
        }
        if (gptResponse.choices() == null || gptResponse.choices().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 choices가 없습니다.");
        }
        ResumeGptResponseMessage message = gptResponse.choices().get(0).message();
        if (message == null) {
            throw new ExternalApiException("GPT API 응답에 message가 없습니다.");
        }
        if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
            throw new ExternalApiException("GPT API 응답에 tool_calls가 없습니다.");
        }
    }
}
