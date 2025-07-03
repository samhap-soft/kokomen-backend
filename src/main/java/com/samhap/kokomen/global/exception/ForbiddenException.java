package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends KokomenException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
