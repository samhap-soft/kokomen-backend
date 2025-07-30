package com.samhap.kokomen.global.aop;

import com.samhap.kokomen.global.exception.RedisException;
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
public class RedisExceptionWrappingAspect {

    @Around("@within(com.samhap.kokomen.global.annotation.RedisExceptionWrapper) || @annotation(com.samhap.kokomen.global.annotation.RedisExceptionWrapper)")
    public Object wrapException(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (RedisException e) {
            throw e;
        } catch (Exception e) {
            throw new RedisException("레디스 예외 발생", e);
        }
    }
}
