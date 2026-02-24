package com.samhap.kokomen.payment.service;

import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TosspaymentsTransactionService {

    private final TosspaymentsPaymentService tosspaymentsPaymentService;
    private final TosspaymentsPaymentResultService tosspaymentsPaymentResultService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TosspaymentsPaymentResult applyTosspaymentsPaymentResult(TosspaymentsPaymentResult tosspaymentsPaymentResult, PaymentState paymentState) {
        TosspaymentsPayment tosspaymentsPayment = tosspaymentsPaymentService.readById(tosspaymentsPaymentResult.getTosspaymentsPayment().getId());
        tosspaymentsPayment.updateState(paymentState);
        return tosspaymentsPaymentResultService.save(tosspaymentsPaymentResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyCancelResult(TosspaymentsPaymentResponse response) {
        TosspaymentsPayment payment = tosspaymentsPaymentService.readByPaymentKey(response.paymentKey());
        payment.updateState(PaymentState.CANCELED);

        TosspaymentsPaymentResult result = tosspaymentsPaymentResultService.readByTosspaymentsPaymentId(payment.getId());

        if (response.cancels() != null && !response.cancels().isEmpty()) {
            TosspaymentsCancel tosspaymentsCancel = response.cancels().get(0);
            result.updateCancelInfo(
                    tosspaymentsCancel.cancelReason(),
                    tosspaymentsCancel.canceledAt(),
                    tosspaymentsCancel.easyPayDiscountAmount(),
                    response.lastTransactionKey(),
                    tosspaymentsCancel.cancelStatus(),
                    response.status()
            );
        }
    }
}
