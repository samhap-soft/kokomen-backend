package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 지금 상태에서는 할 수 없는 요청(409). 공용 KokomenException 핸들러가 그대로 409로 내보낸다.
 */
public class ConflictException extends KokomenException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause, HttpStatus.CONFLICT);
    }
}
