package com.samhap.kokomen.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class KokomenException extends RuntimeException {

    private final HttpStatusCode httpStatusCode;

    public KokomenException(String message, HttpStatusCode httpStatusCode) {
        super(message);
        this.httpStatusCode = httpStatusCode;
    }

    public KokomenException(String message, Throwable cause, HttpStatusCode httpStatusCode) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
    }
}
