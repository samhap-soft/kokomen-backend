package com.samhap.kokomen.global.config;

import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

public class TosspaymentsConfirmRetryPolicy extends SimpleRetryPolicy {

    public TosspaymentsConfirmRetryPolicy(int maxAttempts) {
        super(maxAttempts);
    }

    @Override
    public boolean canRetry(RetryContext context) {
        Throwable lastException = context.getLastThrowable();
        if (lastException != null && !isRetryableException(lastException)) {
            return false;
        }
        return super.canRetry(context);
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof HttpServerErrorException) {
            return true;
        }
        if (throwable instanceof ResourceAccessException) {
            return true;
        }
        if (throwable instanceof HttpClientErrorException e) {
            return e.getStatusCode().value() == 409;
        }
        return false;
    }
}
