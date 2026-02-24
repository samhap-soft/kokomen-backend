package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class InternalServerErrorException extends KokomenException {

    public InternalServerErrorException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
