package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;

/**
 * 장바구니 상품 추가 응답 DTO
 */
@Getter
public class AddCartItemResponse {
    private final Long cartItemId;
    private final Long productId;
    private final String productName;
    private final Integer quantity;
    private final Integer price;
    private final Integer subtotal;

    public AddCartItemResponse(Long cartItemId, Long productId, String productName, 
                             Integer quantity, Integer price, Integer subtotal) {
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.subtotal = subtotal;
    }
}