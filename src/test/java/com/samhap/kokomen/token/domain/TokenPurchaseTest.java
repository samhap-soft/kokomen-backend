package com.samhap.kokomen.token.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TokenPurchaseTest {

    @Test
    void 구매일로부터_1년_이내이면_환불_가능하다() {
        // given
        TokenPurchase tokenPurchase = TokenPurchaseFixtureBuilder.builder()
                .state(TokenPurchaseState.REFUNDABLE)
                .count(10)
                .remainingCount(10)
                .build();
        setCreatedAt(tokenPurchase, LocalDateTime.now().minusMonths(6));

        // when & then
        assertThat(tokenPurchase.isRefundable()).isTrue();
        assertThat(tokenPurchase.isRefundExpired()).isFalse();
    }

    @Test
    void 구매일로부터_1년이_경과하면_환불_불가능하다() {
        // given
        TokenPurchase tokenPurchase = TokenPurchaseFixtureBuilder.builder()
                .state(TokenPurchaseState.REFUNDABLE)
                .count(10)
                .remainingCount(10)
                .build();
        setCreatedAt(tokenPurchase, LocalDateTime.now().minusYears(1).minusDays(1));

        // when & then
        assertThat(tokenPurchase.isRefundable()).isFalse();
        assertThat(tokenPurchase.isRefundExpired()).isTrue();
    }

    @Test
    void 구매일로부터_정확히_1년이면_환불_가능하다() {
        // given
        TokenPurchase tokenPurchase = TokenPurchaseFixtureBuilder.builder()
                .state(TokenPurchaseState.REFUNDABLE)
                .count(10)
                .remainingCount(10)
                .build();
        setCreatedAt(tokenPurchase, LocalDateTime.now().minusYears(1).plusMinutes(1));

        // when & then
        assertThat(tokenPurchase.isRefundable()).isTrue();
        assertThat(tokenPurchase.isRefundExpired()).isFalse();
    }

    private void setCreatedAt(TokenPurchase tokenPurchase, LocalDateTime createdAt) {
        try {
            Field field = tokenPurchase.getClass().getSuperclass().getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(tokenPurchase, createdAt);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("createdAt 필드 설정 실패", e);
        }
    }
}
