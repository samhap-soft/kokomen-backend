package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends KokomenException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
