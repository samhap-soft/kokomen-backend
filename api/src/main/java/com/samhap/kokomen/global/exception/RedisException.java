package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class RedisException extends KokomenException {

    public RedisException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public RedisException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
