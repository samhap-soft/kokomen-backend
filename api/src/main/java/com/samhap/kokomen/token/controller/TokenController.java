package com.samhap.kokomen.token.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.dto.TokenPurchaseResponse;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import com.samhap.kokomen.token.service.TokenFacadeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/tokens")
@RestController
public class TokenController {

    private final TokenFacadeService tokenFacadeService;

    @PostMapping("/purchases")
    public ResponseEntity<Void> purchaseTokens(
            @Authentication MemberAuth memberAuth,
            @Valid @RequestBody TokenPurchaseRequest request
    ) {
        tokenFacadeService.purchaseTokens(memberAuth.memberId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/purchases")
    public ResponseEntity<List<TokenPurchaseResponse>> readMyTokenPurchases(
            @RequestParam(required = false) TokenPurchaseState state,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @Authentication MemberAuth memberAuth
    ) {
        List<TokenPurchaseResponse> purchases = tokenFacadeService.readMyTokenPurchases(memberAuth.memberId(), state, pageable);
        return ResponseEntity.ok(purchases);
    }

    @PostMapping("/refund")
    public ResponseEntity<Void> refundTokens(
            @Authentication MemberAuth memberAuth,
            @Valid @RequestBody TokenRefundRequest request
    ) {
        tokenFacadeService.refundTokens(memberAuth.memberId(), request);
        return ResponseEntity.noContent().build();
    }
}
