package com.samhap.kokomen.token.domain;

import java.util.List;
import lombok.Getter;

@Getter
public enum TokenPurchaseState {
    REFUNDABLE("환불 가능"),
    USABLE("사용 중"),
    EXHAUSTED("사용 완료"),
    REFUNDED("환불 완료");

    private final String displayMessage;

    TokenPurchaseState(String displayMessage) {
        this.displayMessage = displayMessage;
    }

    public static List<TokenPurchaseState> getUsableTokenPurchaseStates() {
        return List.of(USABLE, REFUNDABLE);
    }
}
