package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장바구니 조회 응답 DTO
 */
@Getter
public class CartResponse {
    private final Long cartId;
    private final Long userId;
    private final List<CartItemResponse> items;
    private final Integer totalAmount;
    private final Integer itemCount;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime updatedAt;

    public CartResponse(Long cartId, Long userId, List<CartItemResponse> items, 
                      Integer totalAmount, Integer itemCount, LocalDateTime updatedAt) {
        this.cartId = cartId;
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
        this.updatedAt = updatedAt;
    }

    @Getter
    public static class CartItemResponse {
        private final Long cartItemId;
        private final Long productId;
        private final String productName;
        private final Integer price;
        private final Integer quantity;
        private final Integer subtotal;
        private final Integer stock;
        private final Integer maxQuantityPerCart;

        public CartItemResponse(Long cartItemId, Long productId, String productName, 
                              Integer price, Integer quantity, Integer subtotal, 
                              Integer stock, Integer maxQuantityPerCart) {
            this.cartItemId = cartItemId;
            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.subtotal = subtotal;
            this.stock = stock;
            this.maxQuantityPerCart = maxQuantityPerCart;
        }
    }
}