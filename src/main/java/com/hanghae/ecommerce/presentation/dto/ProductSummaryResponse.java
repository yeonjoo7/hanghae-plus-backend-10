package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;

/**
 * 상품 요약 정보 응답 DTO (여러 상품 조회용)
 */
@Getter
public class ProductSummaryResponse {
    private final Long productId;
    private final String name;
    private final Integer price;
    private final Integer stock;
    private final String status;

    public ProductSummaryResponse(Long productId, String name, Integer price, Integer stock, String status) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
    }
}