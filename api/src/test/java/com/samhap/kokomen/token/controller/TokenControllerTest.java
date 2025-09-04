package com.samhap.kokomen.token.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.dto.PaymentResponse;
import com.samhap.kokomen.token.dto.PaymentResponse.EasyPay;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.JsonFieldType;

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
    void 토큰_구매_DTO_검증_실패() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String invalidRequestJson = """
                {
                    "payment_key": "payment-key-123",
                    "order_id": "order-id-123",
                    "price": -1000,
                    "order_name": "",
                    "product_name": ""
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/token-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();

                    // DTO 필드 검증이 정상 동작
                    assertThat(response).containsAnyOf(
                            "product_name은 비어있거나 공백일 수 없습니다.",
                            "order_name은 비어있거나 공백일 수 없습니다.",
                            "price는 양수여야 합니다."
                    );
                });
    }

    @Test
    void 토큰_구매_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        TokenPurchaseRequest request = new TokenPurchaseRequest(
                "payment_key_123",
                "order_123",
                500L,
                "토큰 10개",
                "TOKEN_10"
        );
        given(paymentClient.confirmPayment(any())).willReturn(new PaymentResponse("간편결제", new EasyPay("카카오페이")));
        long initialPaidTokens = tokenService.readPaidTokenCount(member.getId());

        // when & then
        mockMvc.perform(post("/api/v1/token-purchases")
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
                                fieldWithPath("price").description("결제 금액"),
                                fieldWithPath("order_name").description("주문명"),
                                fieldWithPath("product_name").description("상품명 (예: TOKEN_10)")
                        )
                ));

        // 유료 토큰 수 증가 확인
        assertThat(tokenService.readPaidTokenCount(member.getId())).isEqualTo(initialPaidTokens + 10);
        assertThat(tokenPurchaseRepository.findFirstUsableTokenByState(member.getId(), TokenPurchaseState.REFUNDABLE)).isPresent();
        assertThat(tokenPurchaseRepository.findById(1L).get().getEasyPayProvider()).isEqualTo("카카오페이");
        assertThat(tokenPurchaseRepository.findById(1L).get().getPaymentMethod()).isEqualTo("간편결제");
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
                        .paymentMethod("간편결제")
                        .easyPayProvider("카카오페이")
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
                        .paymentMethod("간편결제")
                        .easyPayProvider("카카오페이")
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
                        .paymentMethod("카드결제")
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
                        .paymentMethod("카드결제")
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then
        mockMvc.perform(get("/api/v1/token-purchases")
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
                                parameterWithName("size").description("페이지 크기 (기본값: 10)").optional(),
                                parameterWithName("sort").description(
                                                "정렬 기준 (필드명,방향). 사용 가능한 필드: id, totalAmount, count, remainingCount, unitPrice, createdAt, updatedAt. 방향: asc, desc. 예: id,desc, totalAmount,asc, createdAt,desc (기본값: id,desc)")
                                        .optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("토큰 구매 내역 ID"),
                                fieldWithPath("[].total_amount").description("총 결제 금액"),
                                fieldWithPath("[].product_name").description("상품명"),
                                fieldWithPath("[].count").description("구매한 토큰 개수"),
                                fieldWithPath("[].remaining_count").description("남은 토큰 개수"),
                                fieldWithPath("[].state").description("토큰 상태 (환불 가능, 사용 중, 사용 완료, 환불 완료)"),
                                fieldWithPath("[].unit_price").description("토큰 단가"),
                                fieldWithPath("[].payment_method").type(JsonFieldType.STRING).description("결제 방법"),
                                fieldWithPath("[].easy_pay_provider").type(JsonFieldType.STRING).description("간편결제 제공업체").optional()
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
        mockMvc.perform(get("/api/v1/token-purchases")
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
                                parameterWithName("size").description("페이지 크기 (기본값: 10)").optional(),
                                parameterWithName("sort").description(
                                                "정렬 기준 (필드명,방향). 사용 가능한 필드: id, totalAmount, count, remainingCount, unitPrice, createdAt, updatedAt. 방향: asc, desc. 예: id,desc, totalAmount,asc, createdAt,desc (기본값: id,desc)")
                                        .optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("토큰 구매 내역 ID"),
                                fieldWithPath("[].total_amount").description("총 결제 금액"),
                                fieldWithPath("[].product_name").description("상품명"),
                                fieldWithPath("[].count").description("구매한 토큰 개수"),
                                fieldWithPath("[].remaining_count").description("남은 토큰 개수"),
                                fieldWithPath("[].state").description("토큰 상태 (환불 가능, 사용 중, 사용 완료, 환불 완료)"),
                                fieldWithPath("[].unit_price").description("토큰 단가"),
                                fieldWithPath("[].payment_method").type(JsonFieldType.STRING).description("결제 방법"),
                                fieldWithPath("[].easy_pay_provider").type(JsonFieldType.STRING).description("간편결제 제공업체").optional()
                        )
                ));
    }

    @Test
    void 내_토큰_구매_내역_페이지네이션_정렬_조회_성공() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());

        // EXHAUSTED 상태의 토큰 구매 내역 3개 생성 (totalAmount DESC 정렬 테스트용)
        TokenPurchase purchase1 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(300L) // 가장 높은 금액 (첫번째)
                        .productName("token")
                        .count(10)
                        .remainingCount(0)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.EXHAUSTED)
                        .build()
        );

        TokenPurchase purchase2 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(90L) // 가장 낮은 금액 (세번째)
                        .productName("token")
                        .count(3)
                        .remainingCount(0)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.EXHAUSTED)
                        .build()
        );

        TokenPurchase purchase3 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .totalAmount(150L) // 중간 금액 (두번째)
                        .productName("token")
                        .count(5)
                        .remainingCount(0)
                        .unitPrice(30L)
                        .state(TokenPurchaseState.EXHAUSTED)
                        .build()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        // when & then - 여러 파라미터 조합으로 조회 (EXHAUSTED 상태, page=0, size=5, totalAmount DESC 정렬)
        mockMvc.perform(get("/api/v1/token-purchases")
                        .param("state", "EXHAUSTED")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "totalAmount,desc")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].total_amount").value(300)) // 첫번째: 가장 높은 금액
                .andExpect(jsonPath("$[1].total_amount").value(150)) // 두번째: 중간 금액
                .andExpect(jsonPath("$[2].total_amount").value(90))  // 세번째: 가장 낮은 금액
                .andDo(document("token-purchases-list-with-pagination-and-sorting",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("state").description("토큰 상태 필터 (REFUNDABLE, USABLE, EXHAUSTED, REFUNDED)").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작, 기본값: 0)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본값: 10)").optional(),
                                parameterWithName("sort").description(
                                                "정렬 기준 (필드명,방향). 사용 가능한 필드: id, totalAmount, count, remainingCount, unitPrice, createdAt, updatedAt. 방향: asc, desc. 예: id,desc, totalAmount,asc, createdAt,desc (기본값: id,desc)")
                                        .optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("토큰 구매 내역 ID"),
                                fieldWithPath("[].total_amount").description("총 결제 금액"),
                                fieldWithPath("[].product_name").description("상품명"),
                                fieldWithPath("[].count").description("구매한 토큰 개수"),
                                fieldWithPath("[].remaining_count").description("남은 토큰 개수"),
                                fieldWithPath("[].state").description("토큰 상태 (환불 가능, 사용 중, 사용 완료, 환불 완료)"),
                                fieldWithPath("[].unit_price").description("토큰 단가"),
                                fieldWithPath("[].payment_method").type(JsonFieldType.STRING).description("결제 방법"),
                                fieldWithPath("[].easy_pay_provider").type(JsonFieldType.STRING).description("간편결제 제공업체").optional()
                        )
                ));
    }

    @Test
    void 환불_사유_조회_성공() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/token-purchases/refund-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("CHANGE_OF_MIND"))
                .andExpect(jsonPath("$[0].message").value("단순 변심"))
                .andExpect(jsonPath("$[0].requires_reason_text").value(false))
                .andExpect(jsonPath("$[5].code").value("OTHER"))
                .andExpect(jsonPath("$[5].message").value("기타"))
                .andExpect(jsonPath("$[5].requires_reason_text").value(true))
                .andDo(document("refund-reasons-list",
                        responseFields(
                                fieldWithPath("[].code").description("환불 사유 코드"),
                                fieldWithPath("[].message").description("환불 사유 메시지"),
                                fieldWithPath("[].requires_reason_text").description("상세 사유 입력 필요 여부")
                        )
                ));
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

        TokenRefundRequest request = new TokenRefundRequest(RefundReasonCode.CHANGE_OF_MIND, null);
        willDoNothing().given(paymentClient).refundPayment(any());

        long initialPaidTokens = tokenService.readPaidTokenCount(member.getId());

        // when & then
        mockMvc.perform(patch("/api/v1/token-purchases/{tokenPurchaseId}/refund", tokenPurchase.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isNoContent())
                .andDo(document("token-refund-success",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("tokenPurchaseId").description("환불할 토큰 구매 내역의 ID")
                        ),
                        requestFields(
                                fieldWithPath("refund_reason_code").description(
                                        "환불 사유 코드 (CHANGE_OF_MIND, NO_LONGER_USING_SERVICE, NOT_AS_EXPECTED, SERVICE_DISSATISFACTION, TECHNICAL_ISSUE, OTHER)"),
                                fieldWithPath("refund_reason_text").type(JsonFieldType.STRING).description("환불 사유 상세 설명 (refund_reason_code가 OTHER일 때 필수)")
                                        .optional()
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

        TokenRefundRequest request = new TokenRefundRequest(RefundReasonCode.CHANGE_OF_MIND, null);

        // when & then
        mockMvc.perform(patch("/api/v1/token-purchases/{tokenPurchaseId}/refund", tokenPurchase.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isBadRequest())
                .andDo(document("token-refund-fail-not-refundable",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("tokenPurchaseId").description("환불할 토큰 구매 내역의 ID")
                        ),
                        requestFields(
                                fieldWithPath("refund_reason_code").description(
                                        "환불 사유 코드 (CHANGE_OF_MIND, NO_LONGER_USING_SERVICE, NOT_AS_EXPECTED, SERVICE_DISSATISFACTION, TECHNICAL_ISSUE, OTHER)"),
                                fieldWithPath("refund_reason_text").type(JsonFieldType.STRING).description("환불 사유 상세 설명 (refund_reason_code가 OTHER일 때 필수)")
                                        .optional()
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

        TokenRefundRequest request = new TokenRefundRequest(RefundReasonCode.CHANGE_OF_MIND, null);

        // when & then
        mockMvc.perform(patch("/api/v1/token-purchases/{tokenPurchaseId}/refund", tokenPurchase.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session))
                .andExpect(status().isBadRequest())
                .andDo(document("token-refund-fail-not-owner",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        pathParameters(
                                parameterWithName("tokenPurchaseId").description("환불할 토큰 구매 내역의 ID")
                        ),
                        requestFields(
                                fieldWithPath("refund_reason_code").description(
                                        "환불 사유 코드 (CHANGE_OF_MIND, NO_LONGER_USING_SERVICE, NOT_AS_EXPECTED, SERVICE_DISSATISFACTION, TECHNICAL_ISSUE, OTHER)"),
                                fieldWithPath("refund_reason_text").type(JsonFieldType.STRING).description("환불 사유 상세 설명 (refund_reason_code가 OTHER일 때 필수)")
                                        .optional()
                        )
                ));
    }
}
