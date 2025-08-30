package com.samhap.kokomen.token.controller;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.service.TokenFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/tokens")
@RestController
public class TokenController {

    private final TokenFacadeService tokenFacadeService;

    @PostMapping("/purchase")
    public ResponseEntity<Void> purchaseTokens(
            @Authentication MemberAuth memberAuth,
            @Valid @RequestBody TokenPurchaseRequest request
    ) {
        tokenFacadeService.purchaseTokens(memberAuth.memberId(), request);
        return ResponseEntity.noContent().build();
    }
}
