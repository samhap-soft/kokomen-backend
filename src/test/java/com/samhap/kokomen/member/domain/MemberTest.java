package com.samhap.kokomen.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 토큰이_1개이상이면_사용할_수_있다() {
        // given
        Member member = MemberFixtureBuilder.builder().freeTokenCount(1).build();

        // when
        member.useToken();

        // then
        assertThat(member.getFreeTokenCount()).isEqualTo(0);
    }

    @Test
    void 토큰이_없는데_토큰을_사용하려_시도하면_예외가_발생한다() {
        // given
        Member member = MemberFixtureBuilder.builder().freeTokenCount(0).build();

        // when & then
        assertThatThrownBy(() -> member.useToken())
                .isInstanceOf(BadRequestException.class);
    }
}
