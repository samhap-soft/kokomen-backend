package com.samhap.kokomen.global.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.auth.external.GoogleOAuthClient;
import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.auth.external.dto.GoogleUserInfoResponse;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.payment.external.TossPaymentsClientBuilder;
import com.samhap.kokomen.payment.external.TosspaymentsClient;
import com.samhap.kokomen.payment.external.dto.TosspaymentsConfirmRequest;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 외부 API 클라이언트가 스프링이 주입하는 RestClient.Builder로 만들어졌을 때 JSON을 어떤 매퍼로 읽는지 고정한다.
 * BaseTest는 이 클라이언트들을 목으로 바꾸므로, 여기서는 실제 빌더로 직접 만들고 HTTP만 MockRestServiceServer로 대신한다.
 * Boot 4는 RestClient.Builder에 Jackson 3 변환기를 기본으로 넣는다. 우리 DTO는 Jackson 2 설정(카카오는 SNAKE_CASE)과
 * Jackson 2 어노테이션(토스의 @JsonDeserialize)에 기대므로, Jackson 3 변환기가 먼저 잡히면 값이 null이 되거나 파싱이 실패한다.
 */
class ExternalClientJsonMappingTest extends BaseTest {

    @Autowired
    private ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 카카오_토큰과_회원_정보의_snake_case_응답을_읽는다() {
        RestClient.Builder builder = restClientBuilderProvider.getObject();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(builder, objectMapper, "client-id", "client-secret", "admin-key");

        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess("""
                        {"access_token":"kakao-access-token","token_type":"bearer","refresh_token":"r",
                         "expires_in":21599,"scope":"profile_nickname","refresh_token_expires_in":5183999}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer kakao-access-token"))
                .andRespond(withSuccess("""
                        {"id":12345,"connected_at":"2026-11-26T01:00:00Z",
                         "kakao_account":{"profile_nickname_needs_agreement":false,"profile":{"nickname":"꼬꼬면"}}}
                        """, MediaType.APPLICATION_JSON));

        KakaoUserInfoResponse response = client.requestKakaoUserInfo("code", "https://kokomen.kr/callback");

        assertThat(response.id()).isEqualTo(12345L);
        assertThat(response.kakaoAccount().profile().nickname()).isEqualTo("꼬꼬면");
        server.verify();
    }

    @Test
    void 구글_토큰과_회원_정보_응답을_읽는다() {
        RestClient.Builder builder = restClientBuilderProvider.getObject();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(builder, objectMapper, "client-id", "client-secret");

        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("""
                        {"access_token":"google-access-token","token_type":"Bearer","expires_in":3599,"scope":"openid"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.googleapis.com/oauth2/v2/userinfo"))
                .andExpect(header("Authorization", "Bearer google-access-token"))
                .andRespond(withSuccess("""
                        {"id":"g-1","email":"a@b.c","verified_email":true,"name":"꼬꼬면","given_name":"면","family_name":"꼬꼬"}
                        """, MediaType.APPLICATION_JSON));

        GoogleUserInfoResponse response = client.requestGoogleUserInfo("code", "https://kokomen.kr/callback");

        assertThat(response.id()).isEqualTo("g-1");
        assertThat(response.verifiedEmail()).isTrue();
        assertThat(response.givenName()).isEqualTo("면");
        server.verify();
    }

    @Test
    void 토스_결제_승인_요청은_camelCase로_보내고_오프셋_시각과_객체_metadata가_담긴_응답을_읽는다() {
        TossPaymentsClientBuilder tossBuilder = new TossPaymentsClientBuilder(restClientBuilderProvider.getObject(), "test-secret");
        // TossPaymentsClientBuilder가 자기 requestFactory를 넣으므로, 그 뒤에 목 서버를 묶어야 실제 HTTP로 나가지 않는다.
        MockRestServiceServer server = MockRestServiceServer.bindTo(tossBuilder.getTossPaymentsClientBuilder()).build();
        TosspaymentsClient client = new TosspaymentsClient(tossBuilder);

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(content().json("""
                        {"paymentKey":"pk","orderId":"order-1","amount":1000}
                        """, true))
                .andRespond(withSuccess("""
                        {"version":"2022-11-16","paymentKey":"pk","type":"NORMAL","orderId":"order-1","orderName":"토큰 10개",
                         "mId":"tosspayments","currency":"KRW","method":"간편결제","totalAmount":1000,"balanceAmount":1000,
                         "status":"DONE","requestedAt":"2026-11-26T10:00:00+09:00","approvedAt":"2026-11-26T01:00:05Z",
                         "isPartialCancelable":true,"metadata":{"memberId":"1"},"country":"KR"}
                        """, MediaType.APPLICATION_JSON));

        TosspaymentsPaymentResponse response = client.confirmPayment(new TosspaymentsConfirmRequest("pk", "order-1", 1000L), "idem-1");

        assertThat(response.mId()).isEqualTo("tosspayments");
        assertThat(response.totalAmount()).isEqualTo(1000L);
        assertThat(response.requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 10, 0, 0));
        assertThat(response.approvedAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 10, 0, 5));
        assertThat(response.metadata()).isEqualTo("{\"memberId\":\"1\"}");
        server.verify();
    }
}
