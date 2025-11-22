package com.hanghae.ecommerce.presentation.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long cartId;
    private Long userId;
    private List<CartItem> items;
    private Integer totalAmount;
    private Integer itemCount;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItem {
        private Long cartItemId;
        private Long productId;
        private String productName;
        private Integer price;
        private Integer quantity;
        private Integer subtotal;
        private Integer stock;
        private Integer maxQuantityPerCart;
    }
}
