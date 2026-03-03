package com.samhap.kokomen.payment.domain;

public enum TosspaymentsStatus {
    READY, // 결제 준비
    IN_PROGRESS, // 결제 진행 중
    DONE, // 승인 성공
    CANCELED, // 결제 취소
    PARTIAL_CANCELED, // 결제 부분 취소
    ABORTED, // 승인 실패
    EXPIRED // 유효 시간 만료
}
