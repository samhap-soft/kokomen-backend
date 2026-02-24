package com.samhap.kokomen.global.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends KokomenException {

    public NotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
