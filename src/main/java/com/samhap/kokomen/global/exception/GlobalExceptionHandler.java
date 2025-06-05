package com.samhap.kokomen.global.exception;

import com.samhap.kokomen.global.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KokomenException.class)
    public ResponseEntity<ErrorResponse> handleKokomenException(KokomenException e) {
        log.warn("KokomenException :: status: {}, message: {}", e.getHttpStatusCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(GptApiException.class)
    public ResponseEntity<ErrorResponse> handleGptApiException(GptApiException e) {
        log.warn("GptApiException :: status: {}, message: {}, stackTrace: ", e.getHttpStatusCode(), e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Exception :: status: {}, message: {}, stackTrace: ", HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버에 문제가 발생하였습니다."));
    }
}
