package com.hanghae.ecommerce.order.dto;

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
public class OrderResponse {
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String status;
    private List<OrderItem> orderItems;
    private Integer totalAmount;
    private OrderRequest.ShippingAddress shippingAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private Long orderItemId;
        private Long productId;
        private String productName;
        private Integer price;
        private Integer quantity;
        private Integer subtotal;
    }
}
