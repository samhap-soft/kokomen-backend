package com.samhap.kokomen.interview.external;

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
public class NotificationClientBuilder {

    private final RestClient.Builder notificationClientBuilder;

    public NotificationClientBuilder(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${notification.base-url}") String notificationBaseUrl,
            @Value("${notification.connect-timeout}") Duration connectTimeout,
            @Value("${notification.read-timeout}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.notificationClientBuilder = builder
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get("requestId");
                    if (requestId != null) {
                        request.getHeaders().add("X-Request-Id", requestId);
                    }
                    return execution.execute(request, body);
                })
                .requestFactory(requestFactory)
                .baseUrl(notificationBaseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }
}
