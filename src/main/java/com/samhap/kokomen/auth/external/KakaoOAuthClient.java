package com.samhap.kokomen.auth.external;

import com.samhap.kokomen.auth.external.dto.KakaoIdResponse;
import com.samhap.kokomen.auth.external.dto.KakaoTokenResponse;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@ExecutionTimer
@Component
public class KakaoOAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String adminKey;

    public KakaoOAuthClient(
            RestClient.Builder builder,
            JsonMapper jsonMapper,
            @Value("${oauth.kakao.client-id}") String clientId,
            @Value("${oauth.kakao.client-secret}") String clientSecret,
            @Value("${oauth.kakao.admin-key}") String adminKey
    ) {
        this.restClient = builder
                .configureMessageConverters(converters -> converters
                        // JSON 변환기 자리를 주입받은 앱 매퍼(SNAKE_CASE, Jackson 2와 같게 맞춘 설정)로 고정한다.
                        // 빌더가 기본으로 넣는 변환기의 매퍼 설정에 기대지 않는다(ExternalClientJsonMappingTest).
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.adminKey = adminKey;
    }

    public KakaoUserInfoResponse requestKakaoUserInfo(String code, String redirectUri) {
        return requestKakaoUserInfo(requestKakaoToken(code, redirectUri));
    }

    private KakaoTokenResponse requestKakaoToken(String code, String redirectUri) {
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

    public KakaoIdResponse unlinkKakaoUser(Long kakaoId) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("target_id_type", "user_id");
        formData.add("target_id", String.valueOf(kakaoId));

        return restClient.post()
                .uri("https://kapi.kakao.com/v1/user/unlink")
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8")
                .body(formData)
                .retrieve()
                .body(KakaoIdResponse.class);
    }

    public KakaoIdResponse logoutKakaoUser(Long kakaoId) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("target_id_type", "user_id");
        formData.add("target_id", String.valueOf(kakaoId));

        return restClient.post()
                .uri("https://kapi.kakao.com/v1/user/logout")
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8")
                .body(formData)
                .retrieve()
                .body(KakaoIdResponse.class);
    }


}
