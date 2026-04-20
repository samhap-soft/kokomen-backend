package com.samhap.kokomen.admin.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.admin.service.dto.AdminCancelPaymentRequest;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResultFixtureBuilder;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

class AdminPaymentControllerTest extends BaseControllerTest {

    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

    @Autowired
    private TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 전체_결제목록_조회_API() throws Exception {
        // given
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_list")
                        .orderId("order_list")
                        .memberId(1L)
                        .orderName("테스트 상품")
                        .totalAmount(10000L)
                        .build()
        );
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .method("카드")
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .approvedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                        .receiptUrl("https://receipt.url")
                        .build()
        );

        // when & then
        mockMvc.perform(get("/api/v1/admin/payments")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total_count").isNumber())
                .andDo(document("admin-findPayments",
                        queryParameters(
                                parameterWithName("memberId").description("회원 ID 필터 (선택)").optional(),
                                parameterWithName("state").description("결제 상태 필터 (선택)").optional(),
                                parameterWithName("startDate").description("시작일 필터 (ISO 형식, 선택)").optional(),
                                parameterWithName("endDate").description("종료일 필터 (ISO 형식, 선택)").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지 크기").optional()
                        ),
                        responseFields(
                                fieldWithPath("data").type(JsonFieldType.ARRAY).description("결제 목록"),
                                fieldWithPath("data[].id").type(JsonFieldType.NUMBER).description("결제 ID"),
                                fieldWithPath("data[].payment_key").type(JsonFieldType.STRING).description("결제 키"),
                                fieldWithPath("data[].member_id").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("data[].order_id").type(JsonFieldType.STRING).description("주문 ID"),
                                fieldWithPath("data[].order_name").type(JsonFieldType.STRING).description("주문명"),
                                fieldWithPath("data[].total_amount").type(JsonFieldType.NUMBER).description("결제 금액"),
                                fieldWithPath("data[].metadata").type(JsonFieldType.STRING).description("메타데이터 (JSON)"),
                                fieldWithPath("data[].state").type(JsonFieldType.STRING).description("결제 상태"),
                                fieldWithPath("data[].service_type").type(JsonFieldType.STRING).description("서비스 타입"),
                                fieldWithPath("data[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                                fieldWithPath("data[].updated_at").type(JsonFieldType.STRING).description("수정일시"),
                                fieldWithPath("data[].result").type(JsonFieldType.OBJECT).description("결제 결과 상세")
                                        .optional(),
                                fieldWithPath("data[].result.method").type(JsonFieldType.STRING).description("결제 수단")
                                        .optional(),
                                fieldWithPath("data[].result.balance_amount").type(JsonFieldType.NUMBER)
                                        .description("잔액").optional(),
                                fieldWithPath("data[].result.tosspayments_status").type(JsonFieldType.STRING)
                                        .description("토스페이먼츠 상태").optional(),
                                fieldWithPath("data[].result.requested_at").type(JsonFieldType.STRING)
                                        .description("요청일시").optional(),
                                fieldWithPath("data[].result.approved_at").type(JsonFieldType.STRING)
                                        .description("승인일시").optional(),
                                fieldWithPath("data[].result.cancel_reason").type(JsonFieldType.STRING)
                                        .description("취소 사유").optional(),
                                fieldWithPath("data[].result.canceled_at").type(JsonFieldType.STRING)
                                        .description("취소일시").optional(),
                                fieldWithPath("data[].result.cancel_status").type(JsonFieldType.STRING)
                                        .description("취소 상태").optional(),
                                fieldWithPath("data[].result.receipt_url").type(JsonFieldType.STRING)
                                        .description("영수증 URL").optional(),
                                fieldWithPath("data[].result.easy_pay_provider").type(JsonFieldType.STRING)
                                        .description("간편결제 제공자").optional(),
                                fieldWithPath("current_page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                                fieldWithPath("total_count").type(JsonFieldType.NUMBER).description("전체 건수"),
                                fieldWithPath("total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                                fieldWithPath("has_next").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 멤버별_결제목록_조회_API() throws Exception {
        // given
        Long targetMemberId = 100L;
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_member")
                        .orderId("order_member")
                        .memberId(targetMemberId)
                        .build()
        );
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .method("카드")
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .build()
        );

        // when & then
        mockMvc.perform(get("/api/v1/admin/payments")
                        .param("memberId", targetMemberId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].member_id").value(targetMemberId));
    }

    @Test
    void 결제_취소_API() throws Exception {
        // given
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_cancel_api")
                        .orderId("order_cancel_api")
                        .build()
        );
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .build()
        );

        TosspaymentsPaymentResponse cancelResponse = createCancelResponse(payment);
        when(tosspaymentsClient.cancelPayment(
                argThat(key -> key.equals("payment_key_cancel_api")),
                argThat(req -> req.cancelReason().equals("고객 요청")),
                argThat(key -> key != null)
        )).thenReturn(cancelResponse);

        AdminCancelPaymentRequest request = new AdminCancelPaymentRequest("고객 요청");

        // when & then
        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/cancel", payment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("admin-cancelPayment",
                        pathParameters(
                                parameterWithName("paymentId").description("결제 ID")
                        ),
                        requestFields(
                                fieldWithPath("cancel_reason").type(JsonFieldType.STRING).description("취소 사유")
                        )
                ));
    }

    private TosspaymentsPaymentResponse createCancelResponse(TosspaymentsPayment payment) {
        return new TosspaymentsPaymentResponse(
                payment.getPaymentKey(),
                PaymentType.NORMAL,
                payment.getOrderId(),
                payment.getOrderName(),
                "mId",
                "KRW",
                "카드",
                payment.getTotalAmount(),
                0L,
                TosspaymentsStatus.CANCELED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "transaction_key",
                9091L,
                909L,
                0L,
                0L,
                true,
                null,
                null,
                null,
                null,
                "KR",
                null,
                Collections.singletonList(
                        new TosspaymentsCancel(
                                "cancel_transaction_key",
                                "고객 요청",
                                0L,
                                LocalDateTime.now(),
                                0L,
                                "receipt_key",
                                payment.getTotalAmount(),
                                0L,
                                0L,
                                "DONE",
                                null
                        )
                )
        );
    }
}
