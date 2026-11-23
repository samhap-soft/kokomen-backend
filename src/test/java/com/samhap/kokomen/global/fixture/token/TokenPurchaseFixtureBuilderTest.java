package com.samhap.kokomen.global.fixture.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.token.domain.TokenPurchase;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TokenPurchaseFixtureBuilderTest {

    private static final int BUILD_COUNT = 1_000;

    // 반복문에서 기본값으로 구매 내역을 여러 건 저장하는 테스트가 있다. payment_key는 UNIQUE(uk_token_purchase_payment_key)라
    // 겹치면 저장이 실패한다. order_id는 UNIQUE가 아니지만 실제 주문처럼 건마다 다르게 만든다. 같은 밀리초에 연달아 만들어도 둘 다 달라야 한다.
    @Test
    void 기본_paymentKey와_orderId는_연달아_만들어도_모두_다르다() {
        // when
        List<TokenPurchase> purchases = IntStream.range(0, BUILD_COUNT)
                .mapToObj(index -> TokenPurchaseFixtureBuilder.builder().build())
                .toList();

        // then
        assertAll(
                () -> assertThat(purchases).extracting(TokenPurchase::getPaymentKey).doesNotHaveDuplicates(),
                () -> assertThat(purchases).extracting(TokenPurchase::getOrderId).doesNotHaveDuplicates()
        );
    }

    @Test
    void 지정한_paymentKey와_orderId는_그대로_쓴다() {
        // when
        TokenPurchase purchase = TokenPurchaseFixtureBuilder.builder()
                .paymentKey("payment-key-1")
                .orderId("order-id-1")
                .build();

        // then
        assertAll(
                () -> assertThat(purchase.getPaymentKey()).isEqualTo("payment-key-1"),
                () -> assertThat(purchase.getOrderId()).isEqualTo("order-id-1")
        );
    }
}
