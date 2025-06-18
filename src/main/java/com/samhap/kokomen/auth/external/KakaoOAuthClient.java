package com.samhap.kokomen.auth.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.auth.external.dto.KakaoTokenResponse;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String redirectUri;
    private final String clientSecret;

    public KakaoOAuthClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${oauth.kakao.client-id}") String clientId,
            @Value("${oauth.kakao.redirect-uri}") String redirectUri,
            @Value("${oauth.kakao.client-secret}") String clientSecret
    ) {
        this.restClient = builder
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.clientSecret = clientSecret;
    }

    public KakaoUserInfoResponse requestKakaoUserInfo(String code) {
        return requestKakaoUserInfo(requestKakaoToken(code));
    }

    private KakaoTokenResponse requestKakaoToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);
        formData.add("client_secret", clientSecret);

        return restClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8")
                .body(formData)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    private KakaoUserInfoResponse requestKakaoUserInfo(KakaoTokenResponse kakaoTokenResponse) {
        String accessToken = kakaoTokenResponse.accessToken();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("property_keys", "[\"kakao_account.profile\"]");

        return restClient.post()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8")
                .body(body)
                .retrieve()
                .body(KakaoUserInfoResponse.class);
    }
}
