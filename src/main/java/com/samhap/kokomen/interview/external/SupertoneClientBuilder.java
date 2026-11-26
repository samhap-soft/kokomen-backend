package com.samhap.kokomen.interview.external;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Getter
@Component
public class SupertoneClientBuilder {

    private static final String SUPERTONE_API_URL = "https://supertoneapi.com";

    private final RestClient.Builder supertoneClientBuilder;

    public SupertoneClientBuilder(
            RestClient.Builder builder,
            JsonMapper jsonMapper,
            @Value("${supertone.api-token}") String supertoneApiToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(15000);

        this.supertoneClientBuilder = builder
                .requestFactory(requestFactory)
                .baseUrl(SUPERTONE_API_URL)
                .defaultHeader("x-sup-api-key", supertoneApiToken)
                .configureMessageConverters(converters -> converters
                        // JSON 변환기 자리를 주입받은 앱 매퍼(SNAKE_CASE, Jackson 2와 같게 맞춘 설정)로 고정한다.
                        // 빌더가 기본으로 넣는 변환기의 매퍼 설정에 기대지 않는다(ExternalClientJsonMappingTest).
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)));
    }
}
