package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class ExternalApiException extends KokomenException {

    public ExternalApiException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
