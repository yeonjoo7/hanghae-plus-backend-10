package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회 응답 DTO
 */
@Getter
public class ProductDetailResponse {
    private final Long productId;
    private final String name;
    private final String description;
    private final Integer price;
    private final Integer stock;
    private final Integer maxQuantityPerCart;
    private final String status;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime updatedAt;

    public ProductDetailResponse(Long productId, String name, String description, 
                               Integer price, Integer stock, Integer maxQuantityPerCart, 
                               String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.maxQuantityPerCart = maxQuantityPerCart;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}