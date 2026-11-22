package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.util.Arrays;

public enum SystemDesignBlockKind {
    CLIENT,
    LOAD_BALANCER,
    API_SERVER,
    DB,
    CACHE,
    QUEUE,
    STORAGE,
    CDN,
    CUSTOM,
    ;

    /**
     * 대소문자를 구분해 읽는다. null이나 모르는 값은 400 {@code 블록 종류를 알 수 없어요.}다.
     */
    public static SystemDesignBlockKind from(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(SystemDesignErrorMessage.DIAGRAM_UNKNOWN_KIND.getMessage()));
    }
}
