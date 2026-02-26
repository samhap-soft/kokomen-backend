package com.samhap.kokomen.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Order(1)
@Aspect
@Component
public class TosspaymentsLoggingAspect {

    @Around("execution(* com.samhap.kokomen.payment.external.TosspaymentsClient.*(..))")
    public Object logTosspaymentsApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("[토스페이먼츠 API 요청] {} - args: {}", methodName, args);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();
            log.info("[토스페이먼츠 API 응답] {} - {}ms - response: {}",
                    methodName, stopWatch.getTotalTimeMillis(), result);
            return result;
        } catch (Throwable e) {
            stopWatch.stop();
            log.error("[토스페이먼츠 API 에러] {} - {}ms - error: {}",
                    methodName, stopWatch.getTotalTimeMillis(), e.getMessage());
            throw e;
        }
    }
}
