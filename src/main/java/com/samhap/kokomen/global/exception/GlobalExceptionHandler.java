package com.samhap.kokomen.global.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.samhap.kokomen.global.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InternalApiException.class)
    public ResponseEntity<ErrorResponse> handleInternalApiException(InternalApiException e) {
        log.error("Exception :: status: {}, message: {}, stackTrace: ", HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(KokomenException.class)
    public ResponseEntity<ErrorResponse> handleKokomenException(KokomenException e) {
        log.warn("KokomenException :: status: {}, message: {}", e.getHttpStatusCode(), e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 용량 포화(503)는 즉시 알람 대상이라 handleKokomenException의 log.warn에 묻히지 않게 전용 핸들러로 분리한다.
     * ServiceUnavailableException은 KokomenException의 하위 타입이므로 Spring이 더 구체적인 이 핸들러를 선택하고,
     * 기존 예외들의 처리 경로는 그대로 유지된다.
     * 카운터는 actuator가 이미 내보내는 http_server_requests_seconds_count{status="503"}로 관측한다
     * (MeterRegistry를 주입해 이 클래스에 의존성을 추가하지 않는다).
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailableException(ServiceUnavailableException e) {
        log.error("ServiceUnavailableException :: status: {}, message: {}, stackTrace: ", e.getHttpStatusCode(),
                e.getMessage(), e);
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
        log.warn("ExternalApiException :: status: {}, message: {}, stackTrace: ", e.getHttpStatusCode(), e.getMessage(),
                e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        String message = "필수 요청 파라미터 '" + e.getParameterName() + "'가 누락되었습니다.";
        log.warn("MissingServletRequestParameterException :: message: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        String message = "잘못된 요청 형식입니다. JSON 형식을 확인해주세요.";
        if (e.getCause() instanceof InvalidFormatException invalidFormatException) {
            String fieldName = invalidFormatException.getPath().get(0).getFieldName();
            String invalidValue = String.valueOf(invalidFormatException.getValue());
            message = String.format(
                    "JSON 파싱 오류: '%s' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: '%s')",
                    fieldName,
                    invalidValue
            );
        }

        log.warn("HttpMessageNotReadableException :: message: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("NoResourceFoundException :: message: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException :: message: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("지원하지 않는 Content-Type입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Exception :: status: {}, message: {}, stackTrace: ", HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버에 문제가 발생하였습니다."));
    }
}
