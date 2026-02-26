package com.samhap.kokomen.payment.service;

import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.exception.PaymentServiceErrorMessage;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.service.dto.ConfirmRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class TosspaymentsPaymentService {

    private final TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TosspaymentsPayment saveTosspaymentsPayment(ConfirmRequest request) {
        return tosspaymentsPaymentRepository.save(request.toTosspaymentsPayment());
    }

    @Transactional(readOnly = true)
    public TosspaymentsPayment readById(Long id) {
        return tosspaymentsPaymentRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("결제 정보 조회 실패 - id: {}", id);
                    return new NotFoundException(PaymentServiceErrorMessage.PAYMENT_NOT_FOUND_BY_ID.getMessage());
                });
    }

    @Transactional(readOnly = true)
    public TosspaymentsPayment readByPaymentKey(String paymentKey) {
        return tosspaymentsPaymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> {
                    log.error("결제 정보 조회 실패 - paymentKey: {}", paymentKey);
                    return new NotFoundException(PaymentServiceErrorMessage.PAYMENT_NOT_FOUND_BY_PAYMENT_KEY.getMessage());
                });
    }

    @Transactional(readOnly = true)
    public Optional<TosspaymentsPayment> findByPaymentKey(String paymentKey) {
        return tosspaymentsPaymentRepository.findByPaymentKey(paymentKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateState(Long tosspaymentsPaymentId, PaymentState state) {
        TosspaymentsPayment tosspaymentsPayment = readById(tosspaymentsPaymentId);
        tosspaymentsPayment.updateState(state);
    }
}
