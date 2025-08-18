package com.samhap.kokomen.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Order(2)
@Aspect
@Component
public class ExecutionTimerAspect {

    @Around("@within(com.samhap.kokomen.global.annotation.ExecutionTimer) || @annotation(com.samhap.kokomen.global.annotation.ExecutionTimer)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Object result = joinPoint.proceed();
        stopWatch.stop();
        logExecutionTime(joinPoint.getSignature(), stopWatch.getTotalTimeMillis());
        return result;
    }

    private static void logExecutionTime(Signature signature, long executionTime) {
        String className = signature.getDeclaringTypeName();
        String methodName = signature.getName();
        log.info("Execution time : {}.{} - {}ms", className, methodName, executionTime);
    }
}
