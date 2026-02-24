package com.samhap.kokomen.product.service;

import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.product.service.dto.ProductResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    public List<ProductResponse> findProducts() {
        return TokenProduct.getProducts()
                .stream()
                .map(ProductResponse::new)
                .toList();
    }
}