package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class InternalApiException extends KokomenException {

    public InternalApiException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public InternalApiException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
