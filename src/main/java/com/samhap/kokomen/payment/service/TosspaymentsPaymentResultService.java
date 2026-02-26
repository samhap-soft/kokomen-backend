package com.samhap.kokomen.payment.service;

import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.exception.PaymentServiceErrorMessage;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class TosspaymentsPaymentResultService {

    private final TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;

    @Transactional
    public TosspaymentsPaymentResult save(TosspaymentsPaymentResult tosspaymentsPaymentResult) {
        return tosspaymentsPaymentResultRepository.save(tosspaymentsPaymentResult);
    }

    @Transactional(readOnly = true)
    public TosspaymentsPaymentResult readByTosspaymentsPaymentId(Long tosspaymentsPaymentId) {
        return tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(tosspaymentsPaymentId)
                .orElseThrow(() -> {
                    log.error("결제 결과 조회 실패 - tosspaymentsPaymentId: {}", tosspaymentsPaymentId);
                    return new NotFoundException(PaymentServiceErrorMessage.PAYMENT_RESULT_NOT_FOUND.getMessage());
                });
    }
}
