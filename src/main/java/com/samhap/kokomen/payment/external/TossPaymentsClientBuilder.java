package com.samhap.kokomen.payment.external;

import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

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
                .configureMessageConverters(converters -> converters
                        // 토스 API는 camelCase라 앱 매퍼(SNAKE_CASE)가 아닌 전용 매퍼로 JSON 변환기 자리를 바꾼다
                        // (ExternalClientJsonMappingTest).
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(createJsonMapper())));
    }

    // Jackson 2 때 new ObjectMapper()에 JavaTimeModule을 등록하고 아래 셋을 바꾸던 매퍼와 같다. Jackson 3은 java.time을 기본으로 다룬다.
    private JsonMapper createJsonMapper() {
        return Jackson2CompatibleJsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build();
    }
}
