package com.samhap.kokomen.token.domain;

public enum TokenPurchaseState {
    REFUNDABLE, // 환불 가능
    USABLE,     // 사용 가능
    EXHAUSTED,  // 다 씀
    REFUNDED    // 환불 완료
}
