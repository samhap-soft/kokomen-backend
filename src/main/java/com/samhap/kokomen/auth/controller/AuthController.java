package com.samhap.kokomen.auth.controller;

import com.samhap.kokomen.auth.infrastructure.SessionInvalidator;
import com.samhap.kokomen.auth.service.AuthService;
import com.samhap.kokomen.auth.service.dto.GoogleLoginRequest;
import com.samhap.kokomen.auth.service.dto.KakaoLoginRequest;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("api/v1/auth")
@RestController
public class AuthController {

    private final AuthService authService;
    private final String kakaoClientId;
    private final String googleClientId;

    public AuthController(
            AuthService authService,
            @Value("${oauth.kakao.client-id}") String kakaoClientId,
            @Value("${oauth.google.client-id}") String googleClientId
    ) {
        this.authService = authService;
        this.kakaoClientId = kakaoClientId;
        this.googleClientId = googleClientId;
    }

    @GetMapping("/kakao-login")
    public ResponseEntity<Void> redirectKakaoLoginPage(
            @RequestParam String redirectUri,
            @RequestParam String state
    ) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(
                        "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s"
                                .formatted(kakaoClientId, redirectUri, state)))
                .build();
    }

    @PostMapping("/kakao-login")
    public ResponseEntity<MemberResponse> kakaoLogin(
            @RequestBody @Valid KakaoLoginRequest kakaoLoginRequest,
            HttpServletRequest request
    ) {
        MemberResponse memberResponse = authService.kakaoLogin(kakaoLoginRequest);

        HttpSession session = request.getSession(true);
        session.setAttribute("MEMBER_ID", memberResponse.id());

        return ResponseEntity.ok(memberResponse);
    }

    @GetMapping("/google-login")
    public ResponseEntity<Void> redirectGoogleLoginPage(
            @RequestParam String redirectUri,
            @RequestParam String state
    ) {
        String scope = "openid%20profile%20email";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(
                        "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=%s"
                                .formatted(googleClientId, redirectUri, scope, state)))
                .build();
    }

    @PostMapping("/google-login")
    public ResponseEntity<MemberResponse> googleLogin(
            @RequestBody @Valid GoogleLoginRequest googleLoginRequest,
            HttpServletRequest request
    ) {
        MemberResponse memberResponse = authService.googleLogin(googleLoginRequest);

        HttpSession session = request.getSession(true);
        session.setAttribute("MEMBER_ID", memberResponse.id());

        return ResponseEntity.ok(memberResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Authentication MemberAuth memberAuth,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SessionInvalidator.logout(request, response);

        authService.logout(memberAuth);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(
            @Authentication MemberAuth memberAuth,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SessionInvalidator.logout(request, response);

        authService.withdraw(memberAuth);
        return ResponseEntity.noContent().build();
    }

}
