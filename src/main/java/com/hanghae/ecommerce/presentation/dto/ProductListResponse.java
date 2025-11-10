package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;

import java.util.List;

/**
 * 여러 상품 조회 응답 DTO
 */
@Getter
public class ProductListResponse {
    private final List<ProductSummaryResponse> products;
    private final Integer totalCount;

    public ProductListResponse(List<ProductSummaryResponse> products, Integer totalCount) {
        this.products = products;
        this.totalCount = totalCount;
    }
}