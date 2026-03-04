package com.samhap.kokomen.payment.controller;

import com.samhap.kokomen.payment.service.WebhookService;
import com.samhap.kokomen.payment.service.dto.WebhookPayload;
import com.samhap.kokomen.payment.tool.PaymentKeyMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks")
@RestController
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/tosspayments")
    public ResponseEntity<Void> handleTosspaymentsWebhook(
            @RequestHeader("tosspayments-webhook-transmission-time") String transmissionTime,
            @RequestHeader("tosspayments-webhook-transmission-id") String transmissionId,
            @RequestHeader("tosspayments-webhook-transmission-retried-count") int retriedCount,
            @RequestBody WebhookPayload payload
    ) {
        log.info("웹훅 수신 - transmissionId: {}, retriedCount: {}, transmissionTime: {}, state: {}, paymentKey: {}",
                transmissionId, retriedCount,
                transmissionTime, payload.data().status(), PaymentKeyMasker.mask(payload.data().paymentKey()));
        webhookService.handlePaymentStatusChanged(payload);
        return ResponseEntity.ok().build();
    }
}
