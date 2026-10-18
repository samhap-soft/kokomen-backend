package com.samhap.kokomen.global.aop;

import com.samhap.kokomen.global.annotation.DistributedLock;
import com.samhap.kokomen.global.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Order(0)
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String LOCK_KEY_PREFIX = "lock:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final RedissonClient redissonClient;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveLockKey(joinPoint, distributedLock);
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired;
        if (distributedLock.leaseTime() == -1) {
            acquired = lock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit());
        } else {
            acquired = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
        }

        if (!acquired) {
            throw new BadRequestException("현재 다른 요청을 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String resolveLockKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        Object resolvedKey = PARSER.parseExpression(distributedLock.key()).getValue(context);
        if (resolvedKey == null) {
            throw new BadRequestException("분산 락 키를 생성할 수 없습니다.");
        }
        return createLockKey(distributedLock.prefix(), resolvedKey);
    }

    /**
     * {@code @DistributedLock}이 잡는 RLock의 키다. 애너테이션 밖에서 같은 락을 직접 잡는 곳(결제 복구 스케줄러)도
     * 키를 다시 조립하지 않고 이 메서드를 쓴다. 한쪽 형식만 바뀌면 서로 다른 락을 잡아 상호 배제가 조용히 깨진다.
     */
    public static String createLockKey(String prefix, Object key) {
        return LOCK_KEY_PREFIX + prefix + ":" + key;
    }
}
