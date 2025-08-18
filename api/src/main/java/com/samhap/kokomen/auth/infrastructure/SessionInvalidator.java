package com.samhap.kokomen.auth.infrastructure;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

public class SessionInvalidator {

    private SessionInvalidator() {
    }

    public static void logout(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(false).invalidate();
        Cookie jSessionIdCookie = Arrays.stream(request.getCookies())
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("JSESSIONID 쿠키가 요청에 존재하지 않습니다."));
        jSessionIdCookie.setValue("");
        jSessionIdCookie.setMaxAge(0);
        response.addCookie(jSessionIdCookie);
    }
}
