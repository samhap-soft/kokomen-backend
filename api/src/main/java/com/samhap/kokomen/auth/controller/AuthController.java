package com.samhap.kokomen.auth.controller;

import com.samhap.kokomen.auth.service.AuthService;
import com.samhap.kokomen.auth.service.dto.KakaoLoginRequest;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
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
    private final String clientId;

    public AuthController(
            AuthService authService,
            @Value("${oauth.kakao.client-id}") String clientId
    ) {
        this.authService = authService;
        this.clientId = clientId;
    }

    @GetMapping("/kakao-login")
    public ResponseEntity<Void> redirectKakaoLoginPage(
            @RequestParam String redirectUri,
            @RequestParam String state
    ) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s"
                        .formatted(clientId, redirectUri, state)))
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

    @PostMapping("/kakao-logout")
    public ResponseEntity<Void> kakaoLogout(
            @Authentication MemberAuth memberAuth,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        request.getSession(false).invalidate();
        Cookie jSessionIdCookie = Arrays.stream(request.getCookies())
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("JSESSIONID 쿠키가 요청에 존재하지 않습니다."));
        jSessionIdCookie.setValue("");
        jSessionIdCookie.setMaxAge(0);
        response.addCookie(jSessionIdCookie);

        authService.withdraw(memberAuth);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/kakao-withdraw")
    public ResponseEntity<Void> kakaoWithdraw(
            @Authentication MemberAuth memberAuth,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        request.getSession(false).invalidate();
        Cookie jSessionIdCookie = Arrays.stream(request.getCookies())
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("JSESSIONID 쿠키가 요청에 존재하지 않습니다."));
        jSessionIdCookie.setValue("");
        jSessionIdCookie.setMaxAge(0);
        response.addCookie(jSessionIdCookie);

        authService.withdraw(memberAuth);
        return ResponseEntity.noContent().build();
    }
}
