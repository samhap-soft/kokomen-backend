package com.samhap.kokomen.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(1)
@Aspect
@Component
public class PaymentLoggingAspect {

    @Around("execution(* com.samhap.kokomen.token.external.PaymentClient.*(..))")
    public Object logPaymentApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("[Payment API 요청] {} - args: {}", methodName, args);

        Object result = joinPoint.proceed();
        log.info("[Payment API 응답] {} - response: {}", methodName, result);
        return result;
    }
}
