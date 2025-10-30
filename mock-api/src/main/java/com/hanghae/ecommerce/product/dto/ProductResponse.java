package com.hanghae.ecommerce.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long productId;
    private String name;
    private String description;
    private Integer price;
    private Integer stock;
    private Integer maxQuantityPerCart;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
