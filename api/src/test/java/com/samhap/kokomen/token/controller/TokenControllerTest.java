package com.samhap.kokomen.token.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class TokenControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenService tokenService;

    @Test
    void 토큰_구매_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        PurchaseMetadata metadata = new PurchaseMetadata("token", 10, 30L);
        TokenPurchaseRequest request = new TokenPurchaseRequest(
                "payment_key_123",
                "order_123",
                300L,
                "토큰 10개 구매",
                metadata
        );

        // PaymentClient mock - 결제 성공 시뮬레이션
        willDoNothing().given(paymentClient).confirmPayment(any());

        // 구매 전 토큰 수 확인
        long initialPaidTokens = tokenService.getPaidTokenCount(member.getId());

        // when & then
        mockMvc.perform(post("/api/v1/tokens/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("token-purchase-success",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("payment_key").description("토스페이먼츠 결제 키"),
                                fieldWithPath("order_id").description("주문 ID"),
                                fieldWithPath("total_amount").description("총 결제 금액"),
                                fieldWithPath("order_name").description("주문명"),
                                fieldWithPath("metadata").description("구매 메타데이터"),
                                fieldWithPath("metadata.product_name").description("상품명 (반드시 'token')"),
                                fieldWithPath("metadata.count").description("구매할 토큰 개수"),
                                fieldWithPath("metadata.unit_price").description("토큰 단가 (30원 고정)")
                        )
                ));

        // 유료 토큰 수 증가 확인
        assertThat(tokenService.getPaidTokenCount(member.getId())).isEqualTo(initialPaidTokens + 10);
    }
}
