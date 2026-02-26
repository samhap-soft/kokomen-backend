package com.samhap.kokomen.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    @Value("${retry.tosspayments.max-attempts}")
    private int maxAttempts;

    @Value("${retry.tosspayments.initial-interval}")
    private long initialInterval;

    @Value("${retry.tosspayments.multiplier}")
    private double multiplier;

    @Value("${retry.tosspayments.max-interval}")
    private long maxInterval;

    @Bean
    public RetryTemplate tosspaymentsConfirmRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        TosspaymentsConfirmRetryPolicy retryPolicy = new TosspaymentsConfirmRetryPolicy(maxAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
