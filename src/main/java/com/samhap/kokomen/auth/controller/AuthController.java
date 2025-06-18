package com.samhap.kokomen.auth.controller;

import com.samhap.kokomen.auth.external.dto.MemberResponse;
import com.samhap.kokomen.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("api/v1/auth")
@RestController
public class AuthController {

    private final AuthService authService;
    private final String clientId;
    private final String redirectUri;
    private final String frontendUri;

    public AuthController(
            AuthService authService,
            @Value("${oauth.kakao.client-id}") String clientId,
            @Value("${oauth.kakao.redirect-uri}") String redirectUri,
            @Value("${oauth.frontend-uri}") String frontendUri
    ) {
        this.authService = authService;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.frontendUri = frontendUri;
    }

    @GetMapping("/kakao-login")
    public ResponseEntity<Void> kakaoLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(
                        URI.create("https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s".formatted(clientId, redirectUri)))
                .build();
    }

    @GetMapping("/kakao-login/redirect")
    public ResponseEntity<Void> kakaoLoginRedirect(
            @RequestParam String code,
            HttpServletRequest request
    ) {
        MemberResponse memberResponse = authService.kakaoLogin(code);

        HttpSession session = request.getSession(true);
        session.setAttribute("MEMBER_ID", memberResponse.id());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUri))
                .build();
    }
}
