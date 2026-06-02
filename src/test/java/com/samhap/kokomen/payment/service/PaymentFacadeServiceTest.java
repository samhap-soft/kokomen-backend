package com.samhap.kokomen.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.global.exception.PaymentServiceErrorMessage;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResultFixtureBuilder;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.Failure;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import com.samhap.kokomen.payment.service.dto.CancelRequest;
import com.samhap.kokomen.payment.service.dto.ConfirmRequest;
import com.samhap.kokomen.payment.service.dto.PaymentResponse;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class PaymentFacadeServiceTest extends BaseTest {

    @Autowired
    private PaymentFacadeService paymentFacadeService;

    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

    @Autowired
    private TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;

    @Test
    void 결제_승인에_성공한다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any())).thenReturn(createSuccessResponse());

        PaymentResponse response = paymentFacadeService.confirmPayment(request);

        assertThat(response.paymentKey()).isEqualTo("payment_key");
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.APPROVED);
        assertThat(tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(payment.getId())).isPresent();
    }

    @Test
    void 서버_원인_400_에러가_발생하면_SERVER_BAD_REQUEST_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpClientErrorException clientError = mock(HttpClientErrorException.class);
        when(clientError.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientError.getResponseBodyAs(Failure.class))
                .thenReturn(new Failure("INVALID_API_KEY", "잘못된 API 키입니다."));
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(clientError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentServiceErrorMessage.CONFIRM_SERVER_ERROR.getMessage());

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.SERVER_BAD_REQUEST);
    }

    @Test
    void 클라이언트_원인_400_에러가_발생하면_CLIENT_BAD_REQUEST_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpClientErrorException clientError = mock(HttpClientErrorException.class);
        when(clientError.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientError.getResponseBodyAs(Failure.class))
                .thenReturn(new Failure("INVALID_CARD_NUMBER", "카드 번호가 유효하지 않습니다."));
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(clientError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(BadRequestException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.CLIENT_BAD_REQUEST);
    }

    @Test
    void 결제_승인_시_5xx_에러_응답_파싱에_성공하면_결과를_저장하고_NEED_CANCEL_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpServerErrorException serverError = mock(HttpServerErrorException.class);
        when(serverError.getResponseBodyAs(TosspaymentsPaymentResponse.class))
                .thenReturn(createSuccessResponse());
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(serverError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(HttpServerErrorException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.NEED_CANCEL);
        assertThat(tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(payment.getId())).isPresent();
    }

    @Test
    void 결제_승인_시_5xx_에러_응답_파싱에_실패하면_NEED_CANCEL_상태만_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpServerErrorException serverError = mock(HttpServerErrorException.class);
        when(serverError.getResponseBodyAs(TosspaymentsPaymentResponse.class))
                .thenThrow(new RuntimeException("파싱 실패"));
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(serverError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(HttpServerErrorException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.NEED_CANCEL);
        assertThat(tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(payment.getId())).isEmpty();
    }

    @Test
    void 결제_승인_시_연결_타임아웃이_발생하면_CONNECTION_TIMEOUT_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any()))
                .thenThrow(new ResourceAccessException("I/O error", new SocketTimeoutException("Connect timed out")));

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(ResourceAccessException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.CONNECTION_TIMEOUT);
    }

    @Test
    void 결제_승인_시_읽기_타임아웃이_발생하면_NEED_CANCEL_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any()))
                .thenThrow(new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(ResourceAccessException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.NEED_CANCEL);
    }

    @Test
    void 결제_승인_시_SocketTimeoutException_외_네트워크_오류가_발생하면_NEED_CANCEL_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any()))
                .thenThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")));

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(ResourceAccessException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.NEED_CANCEL);
    }

    @Test
    void 결제_승인_시_400_에러_응답_파싱에_실패하면_InternalServerErrorException을_던진다() {
        ConfirmRequest request = createConfirmRequest();
        HttpClientErrorException clientError = mock(HttpClientErrorException.class);
        when(clientError.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientError.getResponseBodyAs(Failure.class)).thenReturn(null);
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(clientError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentServiceErrorMessage.CONFIRM_SERVER_ERROR.getMessage());

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.SERVER_BAD_REQUEST);
    }

    @Test
    void 결제_승인_시_예상치_못한_예외가_발생하면_NEED_CANCEL_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(new RuntimeException("예상치 못한 오류"));

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(RuntimeException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.NEED_CANCEL);
    }

    @Test
    void 결제_승인_시_5xx_에러_후_재시도에_성공하면_APPROVED_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpServerErrorException serverError = mock(HttpServerErrorException.class);
        when(tosspaymentsClient.confirmPayment(any(), any()))
                .thenThrow(serverError)
                .thenReturn(createSuccessResponse());

        PaymentResponse response = paymentFacadeService.confirmPayment(request);

        assertThat(response.paymentKey()).isEqualTo("payment_key");
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.APPROVED);
        verify(tosspaymentsClient, times(2)).confirmPayment(any(), any());
    }

    @Test
    void 결제_승인_시_연결_타임아웃_후_재시도에_성공하면_APPROVED_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any()))
                .thenThrow(new ResourceAccessException("I/O error", new SocketTimeoutException("Connect timed out")))
                .thenReturn(createSuccessResponse());

        PaymentResponse response = paymentFacadeService.confirmPayment(request);

        assertThat(response.paymentKey()).isEqualTo("payment_key");
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.APPROVED);
        verify(tosspaymentsClient, times(2)).confirmPayment(any(), any());
    }

    @Test
    void 결제_승인_시_409_에러_후_재시도에_성공하면_APPROVED_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpClientErrorException conflictError = mock(HttpClientErrorException.class);
        when(conflictError.getStatusCode()).thenReturn(HttpStatus.CONFLICT);
        when(tosspaymentsClient.confirmPayment(any(), any()))
                .thenThrow(conflictError)
                .thenReturn(createSuccessResponse());

        PaymentResponse response = paymentFacadeService.confirmPayment(request);

        assertThat(response.paymentKey()).isEqualTo("payment_key");
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.APPROVED);
        verify(tosspaymentsClient, times(2)).confirmPayment(any(), any());
    }

    @Test
    void 결제_승인_시_클라이언트_400_에러는_재시도하지_않는다() {
        ConfirmRequest request = createConfirmRequest();
        HttpClientErrorException clientError = mock(HttpClientErrorException.class);
        when(clientError.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientError.getResponseBodyAs(Failure.class))
                .thenReturn(new Failure("INVALID_CARD_NUMBER", "카드 번호가 유효하지 않습니다."));
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(clientError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(BadRequestException.class);

        verify(tosspaymentsClient, times(1)).confirmPayment(any(), any());
    }

    @Test
    void 결제_승인_시_409_재시도_소진_후_NEED_CANCEL_상태로_변경한다() {
        ConfirmRequest request = createConfirmRequest();
        HttpClientErrorException conflictError = mock(HttpClientErrorException.class);
        when(conflictError.getStatusCode()).thenReturn(HttpStatus.CONFLICT);
        when(conflictError.getResponseBodyAs(Failure.class))
                .thenReturn(new Failure("IDEMPOTENT_REQUEST_PROCESSING", "이전 요청이 처리 중입니다."));
        when(tosspaymentsClient.confirmPayment(any(), any())).thenThrow(conflictError);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(InternalServerErrorException.class);

        TosspaymentsPayment payment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key").orElseThrow();
        assertThat(payment.getState()).isEqualTo(PaymentState.NEED_CANCEL);
        verify(tosspaymentsClient, times(3)).confirmPayment(any(), any());
    }

    @Test
    void 이미_APPROVED된_paymentKey로_재호출하면_외부_토스_호출_없이_멱등_응답을_반환한다() {
        saveApprovedPayment("payment_key");
        ConfirmRequest request = createConfirmRequest();

        PaymentResponse response = paymentFacadeService.confirmPayment(request);

        assertThat(response.paymentKey()).isEqualTo("payment_key");
        assertThat(response.method()).isEqualTo("카드");
        verify(tosspaymentsClient, times(0)).confirmPayment(any(), any());
    }

    @Test
    void 이미_COMPLETED된_paymentKey로_재호출하면_외부_토스_호출_없이_멱등_응답을_반환한다() {
        TosspaymentsPayment payment = saveApprovedPayment("payment_key");
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);
        ConfirmRequest request = createConfirmRequest();

        PaymentResponse response = paymentFacadeService.confirmPayment(request);

        assertThat(response.paymentKey()).isEqualTo("payment_key");
        verify(tosspaymentsClient, times(0)).confirmPayment(any(), any());
    }

    @Test
    void CLIENT_BAD_REQUEST_상태에서_재호출하면_저장된_failureMessage로_BadRequestException을_던진다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.CLIENT_BAD_REQUEST);
        tosspaymentsPaymentRepository.save(payment);
        TosspaymentsPaymentResult result = TosspaymentsPaymentResultFixtureBuilder.builder()
                .tosspaymentsPayment(payment)
                .failureCode("INVALID_CARD_NUMBER")
                .failureMessage("카드 번호가 유효하지 않습니다.")
                .build();
        tosspaymentsPaymentResultRepository.save(result);
        ConfirmRequest request = createConfirmRequest();

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("카드 번호가 유효하지 않습니다.");
        verify(tosspaymentsClient, times(0)).confirmPayment(any(), any());
    }

    @Test
    void CLIENT_BAD_REQUEST_상태에_failure_정보가_없으면_기본_메시지로_BadRequestException을_던진다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.CLIENT_BAD_REQUEST);
        tosspaymentsPaymentRepository.save(payment);
        ConfirmRequest request = createConfirmRequest();

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PaymentServiceErrorMessage.INVALID_REQUEST.getMessage());
    }

    @Test
    void NEED_CANCEL_상태에서_재호출하면_InternalServerErrorException을_던진다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        tosspaymentsPaymentRepository.save(payment);
        ConfirmRequest request = createConfirmRequest();

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(request))
                .isInstanceOf(InternalServerErrorException.class);
        verify(tosspaymentsClient, times(0)).confirmPayment(any(), any());
    }

    @Test
    void 같은_요청을_연속_두_번_보내면_외부_토스_API는_한_번만_호출된다() {
        ConfirmRequest request = createConfirmRequest();
        when(tosspaymentsClient.confirmPayment(any(), any())).thenReturn(createSuccessResponse());

        paymentFacadeService.confirmPayment(request);
        PaymentResponse second = paymentFacadeService.confirmPayment(request);

        assertThat(second.paymentKey()).isEqualTo("payment_key");
        verify(tosspaymentsClient, times(1)).confirmPayment(any(), any());
    }

    @Test
    void 결제_취소에_성공한다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPaymentResult paymentResult = TosspaymentsPaymentResultFixtureBuilder.builder()
                .tosspaymentsPayment(payment)
                .build();
        tosspaymentsPaymentResultRepository.save(paymentResult);

        LocalDateTime canceledAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        TosspaymentsCancel cancel = new TosspaymentsCancel(
                "cancel_tx_key", "단순 변심", 0L,
                canceledAt, 0L, null, 10000L, 0L, 10000L, "DONE", null
        );
        TosspaymentsPaymentResponse cancelResponse = new TosspaymentsPaymentResponse(
                "payment_key", PaymentType.NORMAL, "order_id", "주문명",
                "tvivarepublica", "KRW", "카드", 10000L, 10000L,
                TosspaymentsStatus.CANCELED, LocalDateTime.now(), LocalDateTime.now(),
                "cancel_tx_key", 9091L, 909L, 0L, 0L, true,
                "{}", null, null, null, "KR", null, List.of(cancel)
        );
        when(tosspaymentsClient.cancelPayment(any(), any(), any())).thenReturn(cancelResponse);

        paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심"));

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key")
                .orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.CANCELED);
        TosspaymentsPaymentResult updatedResult = tosspaymentsPaymentResultRepository
                .findByTosspaymentsPaymentId(updatedPayment.getId()).orElseThrow();
        assertThat(updatedResult.getCancelReason()).isEqualTo("단순 변심");
        assertThat(updatedResult.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(updatedResult.getCancelStatus()).isEqualTo("DONE");
    }

    @Test
    void 결제_취소_시_400_에러가_발생하면_BadRequestException을_던진다() {
        saveCancellablePayment("payment_key");
        HttpClientErrorException clientError = mock(HttpClientErrorException.class);
        when(clientError.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientError.getResponseBodyAs(Failure.class))
                .thenReturn(new Failure("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제입니다."));
        when(tosspaymentsClient.cancelPayment(any(), any(), any())).thenThrow(clientError);

        assertThatThrownBy(() -> paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 결제_취소_시_400_에러_응답_파싱에_실패하면_InternalServerErrorException을_던진다() {
        saveCancellablePayment("payment_key");
        HttpClientErrorException clientError = mock(HttpClientErrorException.class);
        when(clientError.getResponseBodyAs(Failure.class)).thenReturn(null);
        when(tosspaymentsClient.cancelPayment(any(), any(), any())).thenThrow(clientError);

        assertThatThrownBy(() -> paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심")))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentServiceErrorMessage.CANCEL_SERVER_ERROR.getMessage());
    }

    @Test
    void 결제_취소_시_5xx_에러가_발생하면_InternalServerErrorException을_던진다() {
        saveCancellablePayment("payment_key");
        when(tosspaymentsClient.cancelPayment(any(), any(), any()))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심")))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentServiceErrorMessage.CANCEL_SERVER_ERROR.getMessage());
    }

    @Test
    void 결제_취소_시_네트워크_에러가_발생하면_InternalServerErrorException을_던진다() {
        saveCancellablePayment("payment_key");
        when(tosspaymentsClient.cancelPayment(any(), any(), any()))
                .thenThrow(new ResourceAccessException("네트워크 오류"));

        assertThatThrownBy(() -> paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심")))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentServiceErrorMessage.CANCEL_NETWORK_ERROR.getMessage());
    }

    @Test
    void 결제_취소_시_예상치_못한_예외가_발생하면_InternalServerErrorException을_던진다() {
        saveCancellablePayment("payment_key");
        when(tosspaymentsClient.cancelPayment(any(), any(), any()))
                .thenThrow(new RuntimeException("예상치 못한 오류"));

        assertThatThrownBy(() -> paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심")))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentServiceErrorMessage.CANCEL_SERVER_ERROR.getMessage());
    }

    @Test
    void 취소_불가능한_상태에서_결제_취소를_시도하면_BadRequestException을_던진다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        tosspaymentsPaymentRepository.save(payment);

        assertThatThrownBy(() -> paymentFacadeService.cancelPayment(new CancelRequest("payment_key", "단순 변심")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("현재 결제 상태에서는 취소할 수 없습니다");
    }

    private void saveCancellablePayment(String paymentKey) {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .build();
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);
    }

    private TosspaymentsPayment saveApprovedPayment(String paymentKey) {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId("order_id")
                .totalAmount(10000L)
                .build();
        payment.updateState(PaymentState.APPROVED);
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPaymentResult result = TosspaymentsPaymentResultFixtureBuilder.builder()
                .tosspaymentsPayment(payment)
                .method("카드")
                .approvedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();
        tosspaymentsPaymentResultRepository.save(result);
        return payment;
    }

    private ConfirmRequest createConfirmRequest() {
        return new ConfirmRequest("payment_key", "order_id", 10000L, "주문명", 1L, "{}", ServiceType.INTERVIEW);
    }

    private TosspaymentsPaymentResponse createSuccessResponse() {
        return new TosspaymentsPaymentResponse(
                "payment_key", PaymentType.NORMAL, "order_id", "주문명",
                "tvivarepublica", "KRW", "카드", 10000L, 10000L,
                TosspaymentsStatus.DONE, LocalDateTime.now(), LocalDateTime.now(),
                "transaction_key", 9091L, 909L, 0L, 0L, true,
                "{}", null, null, null, "KR", null, null
        );
    }
}
