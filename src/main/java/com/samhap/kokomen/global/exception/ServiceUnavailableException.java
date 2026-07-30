package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends KokomenException {

    public ServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
