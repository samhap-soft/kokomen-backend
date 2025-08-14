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
public class SupertoneClientBuilder {

    private static final String SUPERTONE_API_URL = "https://supertoneapi.com";

    private final RestClient.Builder supertoneClientBuilder;

    public SupertoneClientBuilder(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${supertone.api-token}") String supertoneApiToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(15000);

        this.supertoneClientBuilder = builder
                .requestFactory(requestFactory)
                .baseUrl(SUPERTONE_API_URL)
                .defaultHeader("x-sup-api-key", supertoneApiToken)
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }
}
