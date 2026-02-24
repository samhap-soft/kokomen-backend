package com.samhap.kokomen.payment.domain;

public enum PaymentState {
    NEED_APPROVE, // 결제 승인 대기 상태가 오래 지속되는 경우 결제 취소 필요
    APPROVED, // 결제 완료 후 비즈니스 반영이 안된 상태
    NOT_NEED_CANCEL, // NEED_CANCEL 상태에서 환불처리 시도했으나 애초에 결제가 안 된 것으로 확인된 경우
    NEED_CANCEL, // 리드 타임 아웃 or 토스페이먼츠 5xx 응답인 경우 결제 취소 필요
    CONNECTION_TIMEOUT, // 연결 타임 아웃인 경우에는 환불 처리 불필요
    CANCELED, // 환불 처리 완료
    CLIENT_BAD_REQUEST, // 클라이언트 문제로 토스페이먼츠로부터 400을 받은 경우 사용자에게 메시지 노출 필요
    SERVER_BAD_REQUEST, // 서버 문제로 토스페이먼츠로부터 400을 받은 경우 사용자에게 메시지 노출 불필요
    COMPLETED, // 결제 완료 후 비즈니스 반영도 완료된 상태
    ;
}
