package com.samhap.kokomen.payment.service;

import com.samhap.kokomen.global.annotation.DistributedLock;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.global.exception.KokomenException;
import com.samhap.kokomen.global.exception.PaymentServiceErrorMessage;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.external.TosspaymentsClient;
import com.samhap.kokomen.payment.external.TosspaymentsInternalServerErrorCode;
import com.samhap.kokomen.payment.external.dto.Failure;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentCancelRequest;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.service.dto.CancelRequest;
import com.samhap.kokomen.payment.service.dto.ConfirmRequest;
import com.samhap.kokomen.payment.service.dto.PaymentResponse;
import java.net.SocketTimeoutException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentFacadeService {

    private final TosspaymentsTransactionService tosspaymentsTransactionService;
    private final TosspaymentsPaymentService tosspaymentsPaymentService;
    private final TosspaymentsClient tosspaymentsClient;
    private final RetryTemplate tosspaymentsConfirmRetryTemplate;

    @DistributedLock(prefix = "payment", key = "#request.paymentKey()")
    public PaymentResponse confirmPayment(ConfirmRequest request) {
        TosspaymentsPayment tosspaymentsPayment = tosspaymentsPaymentService.saveTosspaymentsPayment(request);
        try {
            TosspaymentsPaymentResponse tosspaymentsPaymentResponse = confirmPayment(request, tosspaymentsPayment);
            return PaymentResponse.from(tosspaymentsPaymentResponse);
        } catch (KokomenException | HttpServerErrorException | ResourceAccessException e) {
            // inner에서 상태 처리 완료
            throw e;
        } catch (Exception e) {
            // 예상치 못한 예외만 NEED_CANCEL 설정
            tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.NEED_CANCEL);
            throw e;
        }
    }

    private TosspaymentsPaymentResponse confirmPayment(ConfirmRequest request,
                                                       TosspaymentsPayment tosspaymentsPayment) {
        String idempotencyKey = UUID.randomUUID().toString();
        try {
            TosspaymentsPaymentResponse tosspaymentsConfirmResponse = tosspaymentsConfirmRetryTemplate.execute(
                    context -> {
                        if (context.getRetryCount() > 0) {
                            log.warn("토스페이먼츠 결제 승인 재시도 {}회차, paymentKey = {}",
                                    context.getRetryCount(), request.paymentKey());
                        }
                        return tosspaymentsClient.confirmPayment(request.toTosspaymentsConfirmRequest(),
                                idempotencyKey);
                    });
            tosspaymentsPayment.validateTosspaymentsResult(tosspaymentsConfirmResponse.paymentKey(),
                    tosspaymentsConfirmResponse.orderId(),
                    tosspaymentsConfirmResponse.totalAmount());
            TosspaymentsPaymentResult tosspaymentsPaymentResult = tosspaymentsConfirmResponse.toTosspaymentsPaymentResult(
                    tosspaymentsPayment);
            tosspaymentsTransactionService.applyTosspaymentsPaymentResult(tosspaymentsPaymentResult,
                    PaymentState.COMPLETED);
            return tosspaymentsConfirmResponse;
        } catch (HttpClientErrorException e) {
            throw handleConfirmClientError(e, tosspaymentsPayment);
        } catch (HttpServerErrorException e) {
            handleConfirmServerError(e, tosspaymentsPayment);
            throw e;
        } catch (ResourceAccessException e) {
            handleConfirmNetworkError(e, tosspaymentsPayment);
            throw e;
        }
    }

    private RuntimeException handleConfirmClientError(HttpClientErrorException e,
                                                      TosspaymentsPayment tosspaymentsPayment) {
        Failure failure = e.getResponseBodyAs(Failure.class);
        if (failure == null) {
            log.error("토스 결제 실패(400) - 응답 파싱 실패", e);
            tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.SERVER_BAD_REQUEST);
            return new InternalServerErrorException(PaymentServiceErrorMessage.CONFIRM_SERVER_ERROR.getMessage(), e);
        }
        String code = failure.code();

        if ("IDEMPOTENT_REQUEST_PROCESSING".equals(code)) {
            log.error("토스 결제 처리 중 상태 지속 (409), paymentKey = {}", tosspaymentsPayment.getPaymentKey());
            tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.NEED_CANCEL);
            return new InternalServerErrorException(PaymentServiceErrorMessage.CONFIRM_SERVER_ERROR.getMessage(), e);
        }

        if (TosspaymentsInternalServerErrorCode.contains(code)) {
            log.error("토스 결제 실패(서버 원인 400), code = {}, message = {}", code, failure.message());
            tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.SERVER_BAD_REQUEST);
            return new InternalServerErrorException(PaymentServiceErrorMessage.CONFIRM_SERVER_ERROR.getMessage(), e);
        }

        log.info("토스 결제 실패(클라이언트 원인 400), code = {}, message = {}", code, failure.message());
        tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.CLIENT_BAD_REQUEST);
        return new BadRequestException(failure.message(), e);
    }

    private void handleConfirmServerError(HttpServerErrorException e, TosspaymentsPayment tosspaymentsPayment) {
        try {
            TosspaymentsPaymentResponse tosspaymentsConfirmResponse = e.getResponseBodyAs(
                    TosspaymentsPaymentResponse.class);
            TosspaymentsPaymentResult tosspaymentsPaymentResult = tosspaymentsConfirmResponse.toTosspaymentsPaymentResult(
                    tosspaymentsPayment);
            tosspaymentsTransactionService.applyTosspaymentsPaymentResult(tosspaymentsPaymentResult,
                    PaymentState.NEED_CANCEL);
        } catch (Exception parseException) {
            log.warn("토스 5xx 응답 파싱 실패, 상태만 업데이트합니다. paymentId = {}", tosspaymentsPayment.getId(), parseException);
            tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.NEED_CANCEL);
        }
    }

    private void handleConfirmNetworkError(ResourceAccessException e, TosspaymentsPayment tosspaymentsPayment) {
        if (e.getRootCause() instanceof SocketTimeoutException socketTimeoutException) {
            if (socketTimeoutException.getMessage().contains("Connect timed out")) {
                tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.CONNECTION_TIMEOUT);
                return;
            }
            if (socketTimeoutException.getMessage().contains("Read timed out")) {
                tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.NEED_CANCEL);
                return;
            }
        }
        tosspaymentsPaymentService.updateState(tosspaymentsPayment.getId(), PaymentState.NEED_CANCEL);
    }

    public void cancelPayment(CancelRequest request) {
        TosspaymentsPaymentCancelRequest tosspaymentsPaymentCancelRequest = new TosspaymentsPaymentCancelRequest(
                request.cancelReason());
        String idempotencyKey = UUID.randomUUID().toString();
        try {
            TosspaymentsPaymentResponse response = tosspaymentsClient.cancelPayment(request.paymentKey(),
                    tosspaymentsPaymentCancelRequest, idempotencyKey);
            tosspaymentsTransactionService.applyCancelResult(response);
        } catch (HttpClientErrorException e) {
            Failure failure = e.getResponseBodyAs(Failure.class);
            if (failure == null) {
                log.error("결제 취소 실패(400) - 응답 파싱 실패, paymentKey: {}", request.paymentKey(), e);
                throw new InternalServerErrorException(PaymentServiceErrorMessage.CANCEL_SERVER_ERROR.getMessage(), e);
            }
            log.error("결제 취소 실패(400) - paymentKey: {}, code: {}, message: {}", request.paymentKey(), failure.code(),
                    failure.message());
            throw new BadRequestException(failure.message(), e);
        } catch (HttpServerErrorException e) {
            log.error("결제 취소 실패(5xx) - paymentKey: {}, status: {}", request.paymentKey(), e.getStatusCode());
            throw new InternalServerErrorException(PaymentServiceErrorMessage.CANCEL_SERVER_ERROR.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("결제 취소 네트워크 오류 - paymentKey: {}", request.paymentKey(), e);
            throw new InternalServerErrorException(PaymentServiceErrorMessage.CANCEL_NETWORK_ERROR.getMessage(), e);
        } catch (Exception e) {
            log.error("결제 취소 중 예상치 못한 오류 - paymentKey: {}", request.paymentKey(), e);
            throw new InternalServerErrorException(PaymentServiceErrorMessage.CANCEL_SERVER_ERROR.getMessage(), e);
        }
    }
}
