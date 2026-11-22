package com.samhap.kokomen.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ConflictExceptionTest {

    private static final String MESSAGE = "이미 끝난 면접이에요.";

    @Test
    void 상태_코드는_409다() {
        IllegalStateException cause = new IllegalStateException("원인");

        ConflictException exception = new ConflictException(MESSAGE);
        ConflictException exceptionWithCause = new ConflictException(MESSAGE, cause);

        assertAll(
                () -> assertThat(exception).isInstanceOf(KokomenException.class),
                () -> assertThat(exception.getHttpStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(exception.getHttpStatusCode().value()).isEqualTo(409),
                () -> assertThat(exception.getMessage()).isEqualTo(MESSAGE),
                () -> assertThat(exceptionWithCause.getHttpStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(exceptionWithCause.getCause()).isSameAs(cause)
        );
    }

    @Test
    void 공용_핸들러가_409와_예외_메시지를_그대로_응답한다() {
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleKokomenException(new ConflictException(MESSAGE));

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().message()).isEqualTo(MESSAGE)
        );
    }
}
