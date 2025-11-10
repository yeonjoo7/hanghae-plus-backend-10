package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;

/**
 * 장바구니 상품 수량 변경 응답 DTO
 */
@Getter
public class UpdateCartItemResponse {
    private final Long cartItemId;
    private final Integer quantity;
    private final Integer subtotal;

    public UpdateCartItemResponse(Long cartItemId, Integer quantity, Integer subtotal) {
        this.cartItemId = cartItemId;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }
}