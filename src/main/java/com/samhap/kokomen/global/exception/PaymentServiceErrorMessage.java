package com.samhap.kokomen.global.exception;

import lombok.Getter;

@Getter
public enum PaymentServiceErrorMessage {

    MISSING_REQUEST_PARAMETER("필수 요청 파라미터가 누락되었습니다."),
    JSON_PARSE_ERROR("JSON 파싱 오류: 유효하지 않은 값이 전달되었습니다."),
    PAYMENT_NOT_FOUND_BY_ID("해당 id의 결제 정보가 존재하지 않습니다."),
    PAYMENT_NOT_FOUND_BY_PAYMENT_KEY("해당 paymentKey의 결제 정보가 존재하지 않습니다."),
    PAYMENT_RESULT_NOT_FOUND("해당 결제의 결과 정보가 존재하지 않습니다."),
    CONFIRM_SERVER_ERROR("결제 처리 중 서버 오류가 발생했습니다."),
    CANCEL_SERVER_ERROR("결제 취소 처리 중 서버 오류가 발생했습니다."),
    CANCEL_NETWORK_ERROR("결제 취소 처리 중 네트워크 오류가 발생했습니다."),
    INVALID_REQUEST("잘못된 요청입니다."),
    INVALID_REQUEST_FORMAT("잘못된 요청 형식입니다."),
    INTERNAL_SERVER_ERROR("서버에 문제가 발생하였습니다.");

    private final String message;

    PaymentServiceErrorMessage(String message) {
        this.message = message;
    }
}
