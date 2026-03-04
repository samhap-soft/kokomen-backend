package com.samhap.kokomen.payment.tool;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentKeyMasker {

    public static String mask(String key) {
        if (key == null || key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 8) + "***";
    }
}
