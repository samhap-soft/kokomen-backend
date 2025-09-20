package com.samhap.kokomen.token.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.Getter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Getter
@Component
public class PaymentClientBuilder {

    private final RestClient.Builder paymentClientBuilder;

    public PaymentClientBuilder(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${payment.base-url}") String paymentBaseUrl,
            @Value("${notification.connect-timeout}") Duration connectTimeout,
            @Value("${notification.read-timeout}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.paymentClientBuilder = builder
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get("requestId");
                    if (requestId != null) {
                        request.getHeaders().add("X-RequestID", requestId);
                    }
                    return execution.execute(request, body);
                })
                .requestFactory(requestFactory)
                .baseUrl(paymentBaseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }
}
