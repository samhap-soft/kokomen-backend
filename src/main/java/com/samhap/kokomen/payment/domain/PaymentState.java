package com.samhap.kokomen.payment.domain;

public enum PaymentState {
    NEED_APPROVE, // 결제 승인 대기 상태가 오래 지속되는 경우 결제 취소 필요
    APPROVED, // 토스 결제 승인 완료, 비즈니스 반영(토큰 지급) 대기 상태
    NOT_NEED_CANCEL, // 결제가 승인되지 않은 것으로 확인된 경우 (EXPIRED, ABORTED 등)
    NEED_CANCEL, // 리드 타임 아웃 or 토스페이먼츠 5xx 응답인 경우 결제 취소 필요
    CONNECTION_TIMEOUT, // 연결 타임 아웃인 경우에는 환불 처리 불필요
    CANCELED, // 환불 처리 완료
    CLIENT_BAD_REQUEST, // 클라이언트 문제로 토스페이먼츠로부터 400을 받은 경우 사용자에게 메시지 노출 필요
    SERVER_BAD_REQUEST, // 서버 문제로 토스페이먼츠로부터 400을 받은 경우 사용자에게 메시지 노출 불필요
    COMPLETED, // 결제 완료 후 비즈니스 반영도 완료된 상태
    ;

    public boolean canCompleteByWebhook() {
        return this == NEED_APPROVE || this == APPROVED || this == NEED_CANCEL || this == CONNECTION_TIMEOUT;
    }

    public boolean canCancelByWebhook() {
        return this == NEED_APPROVE || this == APPROVED || this == NEED_CANCEL || this == CONNECTION_TIMEOUT;
    }

    public boolean canResolveAsNotNeeded() {
        return this == NEED_APPROVE || this == NEED_CANCEL || this == CONNECTION_TIMEOUT;
    }

    public boolean canCancelByApi() {
        return this == COMPLETED || this == APPROVED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED
                || this == CLIENT_BAD_REQUEST || this == SERVER_BAD_REQUEST
                || this == NOT_NEED_CANCEL;
    }
}
