package com.samhap.kokomen.global.exception;

import com.samhap.kokomen.global.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// TODO: HttpMessageNotReadableException 예외 처리 추가
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KokomenException.class)
    public ResponseEntity<ErrorResponse> handleKokomenException(KokomenException e) {
        log.warn("KokomenException :: status: {}, message: {}", e.getHttpStatusCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String defaultErrorMessageForUser = "잘못된 요청입니다.";
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(defaultErrorMessageForUser);

        if (message.equals(defaultErrorMessageForUser)) {
            log.warn("MethodArgumentNotValidException :: message: {}", e.getMessage());
        } else {
            log.warn("MethodArgumentNotValidException :: message: {}", message);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(ExternalApiException e) {
        log.warn("ExternalApiException :: status: {}, message: {}, stackTrace: ", e.getHttpStatusCode(), e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = "필수 요청 파라미터 '" + e.getParameterName() + "'가 누락되었습니다.";
        log.warn("MissingServletRequestParameterException :: message: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Exception :: status: {}, message: {}, stackTrace: ", HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버에 문제가 발생하였습니다."));
    }
}
