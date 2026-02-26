package com.samhap.kokomen.payment.external;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Getter
@Component
public class TossPaymentsClientBuilder {

    private static final String TOSSPAYMENTS_API_URL = "https://api.tosspayments.com";

    private final RestClient.Builder tossPaymentsClientBuilder;

    public TossPaymentsClientBuilder(
            RestClient.Builder builder,
            @Value("${tosspayments.widget-secret-key}") String tossPaymentsWidgetSecretKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(15000);

        String encodedSecretKey = Base64.getEncoder().encodeToString((tossPaymentsWidgetSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        this.tossPaymentsClientBuilder = builder
                .requestFactory(requestFactory)
                .baseUrl(TOSSPAYMENTS_API_URL)
                .defaultHeader("Authorization", "Basic " + encodedSecretKey)
                .defaultHeader("Content-Type", "application/json")
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(createObjectMapper()));
                });
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        return objectMapper;
    }
}
