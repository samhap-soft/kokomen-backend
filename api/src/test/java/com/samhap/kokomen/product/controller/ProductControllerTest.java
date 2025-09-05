package com.samhap.kokomen.product.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.product.domain.TokenProduct;
import org.junit.jupiter.api.Test;

class ProductControllerTest extends BaseControllerTest {

    @Test
    void 상품_목록을_조회한다() throws Exception {
        // given
        String expectedJson = """
                [
                  {
                    "order_name": "%s",
                    "product_name": "%s",
                    "price": %d
                  },
                  {
                    "order_name": "%s",
                    "product_name": "%s",
                    "price": %d
                  },
                  {
                    "order_name": "%s",
                    "product_name": "%s",
                    "price": %d
                  },
                  {
                    "order_name": "%s",
                    "product_name": "%s",
                    "price": %d
                  },
                  {
                    "order_name": "%s",
                    "product_name": "%s",
                    "price": %d
                  }
                ]
                """.formatted(
                TokenProduct.TOKEN_10.getOrderName(), TokenProduct.TOKEN_10.name(), TokenProduct.TOKEN_10.getPrice(),
                TokenProduct.TOKEN_20.getOrderName(), TokenProduct.TOKEN_20.name(), TokenProduct.TOKEN_20.getPrice(),
                TokenProduct.TOKEN_50.getOrderName(), TokenProduct.TOKEN_50.name(), TokenProduct.TOKEN_50.getPrice(),
                TokenProduct.TOKEN_100.getOrderName(), TokenProduct.TOKEN_100.name(), TokenProduct.TOKEN_100.getPrice(),
                TokenProduct.TOKEN_200.getOrderName(), TokenProduct.TOKEN_200.name(), TokenProduct.TOKEN_200.getPrice());

        // when & then
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson))
                .andDo(document("product-findProducts",
                        responseFields(
                                fieldWithPath("[].order_name").description("상품 주문명 (예: 토큰 10개)"),
                                fieldWithPath("[].product_name").description("상품명 (예: TOKEN_10)"),
                                fieldWithPath("[].price").description("상품 가격")
                        )));
    }
}