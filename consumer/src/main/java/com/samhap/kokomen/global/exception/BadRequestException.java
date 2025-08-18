package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends KokomenException {

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
