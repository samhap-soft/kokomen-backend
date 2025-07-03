package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class GptApiException extends KokomenException {

    public GptApiException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public GptApiException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
