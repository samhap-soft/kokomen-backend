package com.samhap.kokomen.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StopWatch;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final List<String> WHITE_LIST = List.of(
            "/favicon.ico",
            "/docs/index.html",
            "/metrics",
            "/actuator/**");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        filterChain.doFilter(request, response);

        stopWatch.stop();
        String requestId = request.getHeader("X-RequestID");
        log.info("[requestId:{}] {} {} ({}) - {}ms",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                HttpStatus.valueOf(response.getStatus()),
                stopWatch.getTotalTimeMillis());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestURI = request.getRequestURI();
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        return WHITE_LIST.stream().anyMatch(path -> antPathMatcher.match(path, requestURI));
    }
}
