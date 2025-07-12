package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class LlmApiException extends KokomenException {

    public LlmApiException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public LlmApiException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
