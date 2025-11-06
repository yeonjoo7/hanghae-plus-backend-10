package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 상품 수량 변경 요청 DTO
 */
@Getter
@NoArgsConstructor
public class UpdateCartItemRequest {
    
    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer quantity;

    public UpdateCartItemRequest(Integer quantity) {
        this.quantity = quantity;
    }
}