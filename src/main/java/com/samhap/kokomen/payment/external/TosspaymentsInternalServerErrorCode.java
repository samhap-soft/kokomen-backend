package com.samhap.kokomen.payment.external;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum TosspaymentsInternalServerErrorCode {

    INVALID_API_KEY,
    INVALID_AUTHORIZE_AUTH,
    UNAUTHORIZED_KEY,
    INCORRECT_BASIC_AUTH_FORMAT,
    ;

    private static final Set<String> CODES = Arrays.stream(values())
            .map(TosspaymentsInternalServerErrorCode::name)
            .collect(Collectors.toSet());

    public static boolean contains(String code) {
        return CODES.contains(code);
    }
}
