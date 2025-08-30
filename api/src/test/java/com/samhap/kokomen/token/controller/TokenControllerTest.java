package com.samhap.kokomen.token.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
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
    @Autowired
    private TokenPurchaseRepository tokenPurchaseRepository;

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
        willDoNothing().given(paymentClient).confirmPayment(any());
        long initialPaidTokens = tokenService.readPaidTokenCount(member.getId());

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
        assertThat(tokenService.readPaidTokenCount(member.getId())).isEqualTo(initialPaidTokens + 10);
        assertThat(tokenPurchaseRepository.findFirstUsableTokenByState(member.getId(), TokenPurchaseState.REFUNDABLE)).isPresent();
    }

    @Test
    void 토큰_환불_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());

        // 환불 가능한 토큰 구매 내역 생성 
        TokenPurchase tokenPurchase = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .paymentKey("test-payment-key")
                        .count(10)
                        .remainingCount(10)
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build()
        );

        // 유료 토큰 추가 (환불할 토큰이 있어야 함)
        tokenService.addPaidTokens(member.getId(), 10);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        TokenRefundRequest request = new TokenRefundRequest(tokenPurchase.getId(), "단순 변심");
        willDoNothing().given(paymentClient).refundPayment(any());

        long initialPaidTokens = tokenService.readPaidTokenCount(member.getId());

        // when & then
        mockMvc.perform(post("/api/v1/tokens/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("token-refund-success",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("token_purchase_id").description("환불할 토큰 구매 내역의 ID"),
                                fieldWithPath("reason").description("환불 사유")
                        )
                ));

        // 환불 처리 확인
        TokenPurchase refundedTokenPurchase = tokenPurchaseRepository.findById(tokenPurchase.getId()).get();
        assertThat(refundedTokenPurchase.getState()).isEqualTo(TokenPurchaseState.REFUNDED);
        assertThat(refundedTokenPurchase.getRemainingCount()).isEqualTo(0);
        assertThat(tokenService.readPaidTokenCount(member.getId())).isEqualTo(initialPaidTokens - 10);
    }

    @Test
    void 환불_불가능한_상태_토큰_환불_실패() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());

        // 이미 사용된 토큰 구매 내역 생성 (환불 불가능)
        TokenPurchase tokenPurchase = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .count(10)
                        .remainingCount(5)  // 5개 사용됨
                        .state(TokenPurchaseState.USABLE)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        TokenRefundRequest request = new TokenRefundRequest(tokenPurchase.getId(), "단순 변심");

        // when & then
        mockMvc.perform(post("/api/v1/tokens/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isBadRequest())
                .andDo(document("token-refund-fail-not-refundable",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("token_purchase_id").description("환불할 토큰 구매 내역의 ID"),
                                fieldWithPath("reason").description("환불 사유")
                        )
                ));
    }

    @Test
    void 타인의_토큰_환불_실패() throws Exception {
        // given
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1001L).build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1002L).build());
        tokenService.createTokensForNewMember(member1.getId());
        tokenService.createTokensForNewMember(member2.getId());

        // member2의 토큰 구매 내역
        TokenPurchase tokenPurchase = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member2.getId())
                        .count(10)
                        .remainingCount(10)
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build()
        );

        // member1로 로그인 시도
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member1.getId());

        TokenRefundRequest request = new TokenRefundRequest(tokenPurchase.getId(), "단순 변심");

        // when & then
        mockMvc.perform(post("/api/v1/tokens/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isBadRequest())
                .andDo(document("token-refund-fail-not-owner",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("token_purchase_id").description("환불할 토큰 구매 내역의 ID"),
                                fieldWithPath("reason").description("환불 사유")
                        )
                ));
    }

    @Test
    void 내_토큰_구매_내역_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());

        // 여러 토큰 구매 내역 생성
        TokenPurchase refundable = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(150L)
                        .productName("token")
                        .count(5)
                        .remainingCount(5)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build()
        );

        TokenPurchase usable = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(300L)
                        .productName("token")
                        .count(10)
                        .remainingCount(8)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.USABLE)
                        .build()
        );

        TokenPurchase exhausted = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(150L)
                        .productName("token")
                        .count(5)
                        .remainingCount(0)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.EXHAUSTED)
                        .build()
        );

        TokenPurchase refunded = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(150L)
                        .productName("token")
                        .count(5)
                        .remainingCount(0)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.REFUNDED)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/tokens/purchases")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4))
                .andDo(document("token-purchases-list",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("state").description("토큰 상태 필터 (REFUNDABLE, USABLE, EXHAUSTED, REFUNDED)").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본값: 10)").optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("토큰 구매 내역 ID"),
                                fieldWithPath("[].total_amount").description("총 결제 금액"),
                                fieldWithPath("[].product_name").description("상품명"),
                                fieldWithPath("[].count").description("구매한 토큰 개수"),
                                fieldWithPath("[].remaining_count").description("남은 토큰 개수"),
                                fieldWithPath("[].state").description("토큰 상태 (환불 가능, 사용 중, 사용 완료, 환불 완료)"),
                                fieldWithPath("[].unit_price").description("토큰 단가")
                        )
                ));
    }

    @Test
    void 내_토큰_구매_내역_상태별_필터링_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());

        // REFUNDABLE 상태 토큰 구매 내역 생성
        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(150L)
                        .productName("token")
                        .count(5)
                        .remainingCount(5)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build()
        );

        // USABLE 상태 토큰 구매 내역 생성
        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(300L)
                        .productName("token")
                        .count(10)
                        .remainingCount(8)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.USABLE)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then - REFUNDABLE 상태만 조회
        mockMvc.perform(get("/api/v1/tokens/purchases")
                        .param("state", "REFUNDABLE")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].state").value("환불 가능"))
                .andDo(document("token-purchases-list-filtered",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("state").description("토큰 상태 필터 (REFUNDABLE, USABLE, EXHAUSTED, REFUNDED)").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본값: 10)").optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("토큰 구매 내역 ID"),
                                fieldWithPath("[].total_amount").description("총 결제 금액"),
                                fieldWithPath("[].product_name").description("상품명"),
                                fieldWithPath("[].count").description("구매한 토큰 개수"),
                                fieldWithPath("[].remaining_count").description("남은 토큰 개수"),
                                fieldWithPath("[].state").description("토큰 상태 (환불 가능, 사용 중, 사용 완료, 환불 완료)"),
                                fieldWithPath("[].unit_price").description("토큰 단가")
                        )
                ));
    }
}
