package com.samhap.kokomen.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ServiceUnavailableExceptionTest {

    private static final String MESSAGE = "이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.";

    @Test
    void 서비스_불가_예외는_503_상태코드를_가진다() {
        ServiceUnavailableException exception = new ServiceUnavailableException(MESSAGE);

        assertThat(exception).isInstanceOf(KokomenException.class);
        assertThat(exception.getHttpStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(MESSAGE);
    }

    @Test
    void 전용_핸들러는_503과_예외_메시지를_그대로_응답한다() {
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleServiceUnavailableException(new ServiceUnavailableException(MESSAGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(MESSAGE);
    }

    @Test
    void 기존_KokomenException_핸들러의_응답은_바뀌지_않는다() {
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleKokomenException(new BadRequestException("잘못된 요청입니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("잘못된 요청입니다.");
    }
}
