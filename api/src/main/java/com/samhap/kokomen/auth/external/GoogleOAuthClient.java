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
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
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

    public void revokeGoogleToken(String googleId) {
        // 구글 OAuth는 실제 access_token이 필요하므로 googleId만으로는 토큰 무효화 불가능
        // 현재는 로컬 세션 무효화만 수행되며, 구글 측 토큰은 자연 만료됨
        // TODO: 향후 토큰 저장 로직 구현 시 실제 토큰으로 무효화 처리
        try {
            // 실제로는 동작하지 않지만, 카카오와 동일한 패턴 유지를 위해 호출 형태만 유지
            // MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            // formData.add("token", actualAccessToken); // 실제 토큰이 필요
            // 현재는 googleId를 받았지만 실제 토큰이 아니므로 API 호출하지 않음
        } catch (Exception e) {
            // 무시: 로컬 세션 무효화는 이미 완료됨
        }
    }
}
