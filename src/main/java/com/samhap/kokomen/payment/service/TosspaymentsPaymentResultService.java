package com.samhap.kokomen.payment.service;

import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.exception.PaymentServiceErrorMessage;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import java.util.Optional;
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

    /**
     * 결과 행은 결제당 하나다. tosspayments_payment_id에 유니크 제약이 없으므로, 이미 있으면(승인 경로가 저장한 경우 등)
     * 새로 저장하지 않고 그 행을 돌려준다. 조회 후 저장이라 유일성은 메서드가 아니라 결제 락이 지킨다. 그래서 결제 락
     * ({@code PaymentFacadeService.PAYMENT_LOCK_PREFIX}) 안에서만 불러야 한다. 행이 둘이 되면 결과 조회가 예외를 던져
     * 환불이 실패한다.
     */
    @Transactional
    public TosspaymentsPaymentResult saveIfAbsent(TosspaymentsPaymentResult tosspaymentsPaymentResult) {
        Long tosspaymentsPaymentId = tosspaymentsPaymentResult.getTosspaymentsPayment().getId();
        return tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(tosspaymentsPaymentId)
                .orElseGet(() -> tosspaymentsPaymentResultRepository.save(tosspaymentsPaymentResult));
    }

    @Transactional(readOnly = true)
    public TosspaymentsPaymentResult readByTosspaymentsPaymentId(Long tosspaymentsPaymentId) {
        return tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(tosspaymentsPaymentId)
                .orElseThrow(() -> {
                    log.error("결제 결과 조회 실패 - tosspaymentsPaymentId: {}", tosspaymentsPaymentId);
                    return new NotFoundException(PaymentServiceErrorMessage.PAYMENT_RESULT_NOT_FOUND.getMessage());
                });
    }

    @Transactional(readOnly = true)
    public Optional<TosspaymentsPaymentResult> findByTosspaymentsPaymentId(Long tosspaymentsPaymentId) {
        return tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentId(tosspaymentsPaymentId);
    }
}
