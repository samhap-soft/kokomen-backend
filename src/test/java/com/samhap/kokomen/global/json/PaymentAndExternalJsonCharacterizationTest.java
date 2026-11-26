package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.interview.external.SupertoneClientBuilder;
import com.samhap.kokomen.interview.external.dto.request.SupertoneRequest;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.TossPaymentsClientBuilder;
import com.samhap.kokomen.payment.external.TosspaymentsClient;
import com.samhap.kokomen.payment.external.dto.Checkout;
import com.samhap.kokomen.payment.external.dto.EasyPay;
import com.samhap.kokomen.payment.external.dto.Failure;
import com.samhap.kokomen.payment.external.dto.Receipt;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsConfirmRequest;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentCancelRequest;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.service.dto.ConfirmRequest;
import com.samhap.kokomen.payment.service.dto.WebhookPayload;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData.WebhookEasyPay;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 결제와 외부 API의 JSON을 고정한다. 토스 웹훅 본문 읽기, DB에 저장하는 구매 metadata 문자열, 외부로 보내는 요청 본문 바이트,
 * 토스 응답의 metadata(객체를 문자열로 바꾸는 역직렬화기) 결과를 본다. 외부 HTTP는 MockRestServiceServer로 대신한다.
 * 카카오·구글 응답 읽기와 토스 승인 요청·응답의 기본 모양은 ExternalClientJsonMappingTest에 있다.
 */
class PaymentAndExternalJsonCharacterizationTest extends BaseTest {

    private static final String TOSS_PAYMENT_JSON = """
            {"paymentKey":"pk","type":"NORMAL","orderId":"order-1","orderName":"토큰 10개","mId":"tosspayments",\
            "currency":"KRW","method":"간편결제","totalAmount":1000,"balanceAmount":0,"status":"CANCELED",\
            "requestedAt":"2026-11-26T10:00:00+09:00","approvedAt":"2026-11-26T01:00:05.123+00:00",\
            "isPartialCancelable":null,"metadata":%s,"unknownField":{"a":1},\
            "cancels":[{"transactionKey":"tx-c","cancelReason":"단순 변심","cancelAmount":1000,\
            "canceledAt":"2026-11-26T11:00:00+09:00","cancelStatus":"DONE","unknownCancelField":true}]}""";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    @Test
    void 토스_웹훅_본문은_camelCase로_읽고_오프셋_시각을_서울_시각으로_바꾼다() throws Exception {
        WebhookPayload payload = objectMapper.readValue("""
                {"eventType":"PAYMENT_STATUS_CHANGED","createdAt":"2026-11-26T10:00:00.000000","unknown":1,\
                "data":{"paymentKey":"pk","orderId":"order-1","orderName":"토큰 10개","mId":"tosspayments",\
                "currency":"KRW","method":"간편결제","totalAmount":1000,"balanceAmount":1000,"status":"DONE",\
                "requestedAt":"2026-11-26T10:00:00+09:00","approvedAt":"2026-11-26T01:00:05Z",\
                "lastTransactionKey":"tx-1","suppliedAmount":909,"vat":91,"taxFreeAmount":0,"taxExemptionAmount":0,\
                "isPartialCancelable":true,"easyPay":{"provider":"토스페이","amount":1000,"discountAmount":0},\
                "country":"KR","payment_key":"snake-case-is-ignored"}}""", WebhookPayload.class);

        assertThat(payload).isEqualTo(new WebhookPayload("PAYMENT_STATUS_CHANGED", "2026-11-26T10:00:00.000000",
                new WebhookPaymentData("pk", "order-1", "토큰 10개", "tosspayments", "KRW", "간편결제", 1000L, 1000L,
                        TosspaymentsStatus.DONE, LocalDateTime.of(2026, 11, 26, 10, 0, 0),
                        LocalDateTime.of(2026, 11, 26, 10, 0, 5), "tx-1", 909L, 91L, 0L, 0L, true,
                        new WebhookEasyPay("토스페이", 1000L, 0L), "KR")));
    }

    @Test
    void 토스_웹훅_본문의_primitive는_없거나_null이면_false이고_snake_case_키는_읽지_않는다() throws Exception {
        WebhookPayload missing = objectMapper.readValue(
                "{\"data\":{\"paymentKey\":\"pk\",\"status\":\"DONE\"}}", WebhookPayload.class);
        WebhookPayload nullValue = objectMapper.readValue(
                "{\"data\":{\"paymentKey\":\"pk\",\"isPartialCancelable\":null}}", WebhookPayload.class);
        WebhookPayload snakeCase = objectMapper.readValue(
                "{\"event_type\":\"PAYMENT_STATUS_CHANGED\","
                        + "\"data\":{\"payment_key\":\"pk\",\"is_partial_cancelable\":true}}",
                WebhookPayload.class);

        assertAll(
                () -> assertThat(missing.data().isPartialCancelable()).isFalse(),
                () -> assertThat(missing.data().requestedAt()).isNull(),
                () -> assertThat(nullValue.data().isPartialCancelable()).isFalse(),
                () -> assertThat(snakeCase.eventType()).isNull(),
                () -> assertThat(snakeCase.data().paymentKey()).isNull(),
                () -> assertThat(snakeCase.data().isPartialCancelable()).isFalse()
        );
    }

    @Test
    void 토큰_구매_요청은_metadata를_snake_case_JSON_문자열로_만들어_저장한다() {
        TokenPurchaseRequest request = new TokenPurchaseRequest("pk", "order-1", 500L, "토큰 10개", "TOKEN_10");

        ConfirmRequest confirmRequest = request.toPaymentConfirmRequest(7L, objectMapper);

        assertThat(confirmRequest).isEqualTo(new ConfirmRequest("pk", "order-1", 500L, "토큰 10개", 7L,
                "{\"product_name\":\"TOKEN_10\",\"count\":10,\"unit_price\":50}", ServiceType.INTERVIEW));
    }

    @Test
    void 저장된_구매_metadata는_공백과_순서와_모르는_키가_달라도_지금처럼_읽는다() throws Exception {
        assertAll(
                () -> assertThat(objectMapper.readValue(
                        "{\"product_name\":\"TOKEN_10\",\"count\":10,\"unit_price\":50}", PurchaseMetadata.class))
                        .isEqualTo(new PurchaseMetadata("TOKEN_10", 10, 50L)),
                () -> assertThat(objectMapper.readValue(
                        "{ \"unit_price\" : 50 , \"count\" : \"10\" , \"product_name\" : \"TOKEN_10\","
                                + " \"extra\": [1] }",
                        PurchaseMetadata.class)).isEqualTo(new PurchaseMetadata("TOKEN_10", 10, 50L)),
                () -> assertThat(objectMapper.readValue("{\"productName\":\"TOKEN_10\",\"count\":10,\"unitPrice\":50}",
                        PurchaseMetadata.class)).isEqualTo(new PurchaseMetadata(null, 10, null))
        );
    }

    @Test
    void 토스로_보내는_취소_요청_본문은_camelCase이고_이모지는_서로게이트_쌍_이스케이프다() {
        TossPaymentsClientBuilder tossBuilder = new TossPaymentsClientBuilder(restClientBuilderProvider.getObject(),
                "test-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(tossBuilder.getTossPaymentsClientBuilder()).build();
        TosspaymentsClient client = new TosspaymentsClient(tossBuilder);

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/pk/cancel"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().bytes("{\"cancelReason\":\"단순 변심 \\uD83D\\uDE00 a/b <\\\"q\\\">\"}"
                        .getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess(TOSS_PAYMENT_JSON.formatted("{\"memberId\":\"1\"}"),
                        MediaType.APPLICATION_JSON));

        TosspaymentsPaymentResponse response = client.cancelPayment("pk",
                new TosspaymentsPaymentCancelRequest("단순 변심 😀 a/b <\"q\">"), "idem-1");

        assertThat(response).isEqualTo(new TosspaymentsPaymentResponse("pk", PaymentType.NORMAL, "order-1", "토큰 10개",
                "tosspayments", "KRW", "간편결제", 1000L, 0L, TosspaymentsStatus.CANCELED,
                LocalDateTime.of(2026, 11, 26, 10, 0, 0), LocalDateTime.of(2026, 11, 26, 10, 0, 5, 123000000),
                null, null, null, null, null, false, "{\"memberId\":\"1\"}", null, null, null, null, null,
                List.of(new TosspaymentsCancel("tx-c", "단순 변심", null, LocalDateTime.of(2026, 11, 26, 11, 0, 0), null,
                        null, 1000L, null, null, "DONE", null))));
        server.verify();
    }

    @Test
    void 토스로_보내는_승인_요청_본문은_필드_순서까지_그대로이고_응답의_중첩_객체를_모두_읽는다() {
        TossPaymentsClientBuilder tossBuilder = new TossPaymentsClientBuilder(restClientBuilderProvider.getObject(),
                "test-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(tossBuilder.getTossPaymentsClientBuilder()).build();
        TosspaymentsClient client = new TosspaymentsClient(tossBuilder);

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(header("Idempotency-Key", "idem-1"))
                .andExpect(content().bytes("{\"paymentKey\":\"pk\",\"orderId\":\"order-1\",\"amount\":1000}"
                        .getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk","type":"BILLING","orderId":"order-1","orderName":"토큰 10개",\
                        "mId":"tosspayments","currency":"KRW","method":"간편결제","totalAmount":1000,"balanceAmount":1000,\
                        "status":"DONE","requestedAt":"2026-11-26T10:00:00+09:00","approvedAt":"2026-11-26T01:00:05Z",\
                        "lastTransactionKey":"tx-1","suppliedAmount":909,"vat":91,"taxFreeAmount":0,\
                        "taxExemptionAmount":0,\
                        "isPartialCancelable":true,"metadata":null,\
                        "receipt":{"url":"https://dashboard.tosspayments.com/receipt?ref=1&x=2"},\
                        "checkout":{"url":"https://api.tosspayments.com/v1/payments/pk/checkout"},\
                        "easyPay":{"provider":"토스페이","amount":1000,"discountAmount":0},"country":"KR",\
                        "failure":{"code":"REJECT_CARD_COMPANY","message":"카드사 거절 😀"},"cancels":[]}""",
                        MediaType.APPLICATION_JSON));

        TosspaymentsPaymentResponse response = client.confirmPayment(
                new TosspaymentsConfirmRequest("pk", "order-1", 1000L), "idem-1");

        assertThat(response).isEqualTo(new TosspaymentsPaymentResponse("pk", PaymentType.BILLING, "order-1",
                "토큰 10개", "tosspayments", "KRW", "간편결제", 1000L, 1000L, TosspaymentsStatus.DONE,
                LocalDateTime.of(2026, 11, 26, 10, 0, 0), LocalDateTime.of(2026, 11, 26, 10, 0, 5), "tx-1", 909L, 91L,
                0L, 0L, true, null, new Receipt("https://dashboard.tosspayments.com/receipt?ref=1&x=2"),
                new Checkout("https://api.tosspayments.com/v1/payments/pk/checkout"),
                new EasyPay("토스페이", 1000L, 0L), "KR", new Failure("REJECT_CARD_COMPANY", "카드사 거절 😀"), List.of()));
        server.verify();
    }

    @Test
    void 토스_응답의_metadata는_모양대로_문자열로_바꾼다() {
        assertAll(
                () -> assertThat(metadataOf("""
                        {"memberId":"1","count":10,"price":1000.0,"rate":1.50,"big":12345678901234567890,\
                        "flag":true,"nothing":null,"list":[1,"a"],"text":"😀 \\"q\\" a/b","nested":{"k":"v"}}"""))
                        .isEqualTo("""
                                {"memberId":"1","count":10,"price":1000.0,"rate":1.5,\
                                "big":12345678901234567890,"flag":true,"nothing":null,"list":[1,"a"],\
                                "text":"😀 \\"q\\" a/b","nested":{"k":"v"}}"""),
                () -> assertThat(metadataOf("\"이미 문자열\"")).isEqualTo("이미 문자열"),
                () -> assertThat(metadataOf("[1,2.0,\"x\"]")).isEqualTo("[1,2.0,\"x\"]"),
                () -> assertThat(metadataOf("123")).isEqualTo("123"),
                () -> assertThat(metadataOf("null")).isNull()
        );
    }

    @Test
    void 토스_응답의_모르는_결제_상태는_RestClientException이다() {
        TossPaymentsClientBuilder tossBuilder = new TossPaymentsClientBuilder(restClientBuilderProvider.getObject(),
                "test-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(tossBuilder.getTossPaymentsClientBuilder()).build();
        TosspaymentsClient client = new TosspaymentsClient(tossBuilder);

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/pk"))
                .andRespond(withSuccess("{\"paymentKey\":\"pk\",\"status\":\"WAITING_FOR_DEPOSIT\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getPayment("pk")).isInstanceOf(RestClientException.class);
    }

    @Test
    void Supertone으로_보내는_요청_본문은_이모지를_서로게이트_쌍_이스케이프로_쓴다() {
        SupertoneClientBuilder supertoneBuilder = new SupertoneClientBuilder(restClientBuilderProvider.getObject(),
                objectMapper, "test-token");
        MockRestServiceServer server = MockRestServiceServer.bindTo(supertoneBuilder.getSupertoneClientBuilder())
                .build();
        SupertoneClient client = new SupertoneClient(supertoneBuilder);

        server.expect(requestTo("https://supertoneapi.com/v1/text-to-speech/838617ea6b672b84de0813"))
                .andExpect(header("x-sup-api-key", "test-token"))
                .andExpect(content().bytes("{\"text\":\"트랜잭션이란? \\uD83D\\uDE00\",\"language\":\"ko\"}"
                        .getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.request(new SupertoneRequest("트랜잭션이란? 😀")).voiceData()).containsExactly(1, 2, 3);
        server.verify();
    }

    private String metadataOf(String metadataJson) {
        TossPaymentsClientBuilder tossBuilder = new TossPaymentsClientBuilder(restClientBuilderProvider.getObject(),
                "test-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(tossBuilder.getTossPaymentsClientBuilder()).build();
        TosspaymentsClient client = new TosspaymentsClient(tossBuilder);
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/pk"))
                .andRespond(withSuccess(TOSS_PAYMENT_JSON.formatted(metadataJson), MediaType.APPLICATION_JSON));

        String metadata = client.getPayment("pk").metadata();
        server.verify();
        return metadata;
    }
}
