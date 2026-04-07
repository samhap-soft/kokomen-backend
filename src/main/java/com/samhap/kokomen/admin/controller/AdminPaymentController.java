package com.samhap.kokomen.admin.controller;

import com.samhap.kokomen.admin.service.AdminPaymentService;
import com.samhap.kokomen.admin.service.dto.AdminCancelPaymentRequest;
import com.samhap.kokomen.admin.service.dto.AdminPaymentPageResponse;
import com.samhap.kokomen.payment.domain.PaymentState;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payments")
@RestController
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping
    public ResponseEntity<AdminPaymentPageResponse> findPayments(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) PaymentState state,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminPaymentPageResponse response = adminPaymentService.findPayments(memberId, state, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Void> cancelPayment(
            @PathVariable Long paymentId,
            @RequestBody @Valid AdminCancelPaymentRequest request
    ) {
        adminPaymentService.cancelPayment(paymentId, request);
        return ResponseEntity.ok().build();
    }
}
