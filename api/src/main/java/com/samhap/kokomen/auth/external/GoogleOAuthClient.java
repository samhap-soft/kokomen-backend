package com.samhap.kokomen.auth.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.auth.external.dto.GoogleTokenResponse;
import com.samhap.kokomen.auth.external.dto.GoogleUserInfoResponse;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@ExecutionTimer
@Component
public class GoogleOAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${oauth.google.client-id}") String clientId,
            @Value("${oauth.google.client-secret}") String clientSecret
    ) {
        this.restClient = builder
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public GoogleUserInfoResponse requestGoogleUserInfo(String code, String redirectUri) {
        return requestGoogleUserInfo(requestGoogleToken(code, redirectUri));
    }

    private GoogleTokenResponse requestGoogleToken(String code, String redirectUri) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        return restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(formData)
                .retrieve()
                .body(GoogleTokenResponse.class);
    }

    private GoogleUserInfoResponse requestGoogleUserInfo(GoogleTokenResponse googleTokenResponse) {
        String accessToken = googleTokenResponse.accessToken();

        return restClient.get()
                .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GoogleUserInfoResponse.class);
    }
}