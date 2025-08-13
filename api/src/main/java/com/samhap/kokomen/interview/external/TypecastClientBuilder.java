package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Getter
@Component
public class TypecastClientBuilder {

    private static final String TYPECAST_API_URL = "https://typecast.ai/api/speak";

    private final RestClient.Builder typecastClientBuilder;

    public TypecastClientBuilder(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${typecast.api-token}") String typecastApiToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);

        this.typecastClientBuilder = builder
                .requestFactory(requestFactory)
                .baseUrl(TYPECAST_API_URL)
                .defaultHeader("Authorization", "Bearer " + typecastApiToken)
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }
}
