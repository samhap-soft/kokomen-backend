package com.samhap.kokomen.admin.service;

import com.samhap.kokomen.admin.service.dto.AdminCancelPaymentRequest;
import com.samhap.kokomen.admin.service.dto.AdminPaymentPageResponse;
import com.samhap.kokomen.admin.service.dto.AdminPaymentResponse;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.member.repository.AdminRepository;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import com.samhap.kokomen.payment.service.PaymentFacadeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdminPaymentService {

    private final TosspaymentsPaymentRepository tosspaymentsPaymentRepository;
    private final TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;
    private final PaymentFacadeService paymentFacadeService;
    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public AdminPaymentPageResponse findPayments(
            Long memberId,
            PaymentState state,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable,
            MemberAuth memberAuth
    ) {
        validateAdmin(memberAuth.memberId());
        Page<TosspaymentsPayment> page = tosspaymentsPaymentRepository.findPaymentsWithFilters(
                memberId, state, startDate, endDate, pageable
        );
        return toPageResponse(page);
    }

    public void cancelPayment(Long paymentId, AdminCancelPaymentRequest request, MemberAuth memberAuth) {
        validateAdmin(memberAuth.memberId());
        TosspaymentsPayment payment = readPaymentById(paymentId);
        paymentFacadeService.cancelPayment(request.toCancelRequest(payment.getPaymentKey()));
    }

    private TosspaymentsPayment readPaymentById(Long paymentId) {
        return tosspaymentsPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 결제입니다. paymentId = " + paymentId));
    }

    private AdminPaymentPageResponse toPageResponse(Page<TosspaymentsPayment> page) {
        List<Long> paymentIds = page.getContent().stream()
                .map(TosspaymentsPayment::getId)
                .toList();

        Map<Long, TosspaymentsPaymentResult> resultMap = paymentIds.isEmpty()
                ? Map.of()
                : tosspaymentsPaymentResultRepository.findByTosspaymentsPaymentIdIn(paymentIds)
                        .stream()
                        .collect(Collectors.toMap(
                                r -> r.getTosspaymentsPayment().getId(),
                                Function.identity()
                        ));

        List<AdminPaymentResponse> data = page.getContent().stream()
                .map(payment -> AdminPaymentResponse.of(payment, resultMap.get(payment.getId())))
                .toList();

        return AdminPaymentPageResponse.of(data, page);
    }

    private void validateAdmin(Long memberId) {
        if (!adminRepository.existsByMemberId(memberId)) {
            throw new UnauthorizedException("권한이 없습니다.");
        }
    }
}
