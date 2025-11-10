package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 생성 응답 DTO
 */
@Getter
public class CreateOrderResponse {
    private final Long orderId;
    private final String orderNumber;
    private final String status;
    private final List<OrderItemResponse> orderItems;
    private final Integer totalAmount;
    private final ShippingAddressResponse shippingAddress;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime createdAt;

    public CreateOrderResponse(Long orderId, String orderNumber, String status,
                             List<OrderItemResponse> orderItems, Integer totalAmount,
                             ShippingAddressResponse shippingAddress, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.orderItems = orderItems;
        this.totalAmount = totalAmount;
        this.shippingAddress = shippingAddress;
        this.createdAt = createdAt;
    }

    @Getter
    public static class OrderItemResponse {
        private final Long orderItemId;
        private final Long productId;
        private final String productName;
        private final Integer price;
        private final Integer quantity;
        private final Integer subtotal;

        public OrderItemResponse(Long orderItemId, Long productId, String productName,
                               Integer price, Integer quantity, Integer subtotal) {
            this.orderItemId = orderItemId;
            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }
    }

    @Getter
    public static class ShippingAddressResponse {
        private final String recipientName;
        private final String phone;
        private final String zipCode;
        private final String address;
        private final String detailAddress;

        public ShippingAddressResponse(String recipientName, String phone, String zipCode,
                                     String address, String detailAddress) {
            this.recipientName = recipientName;
            this.phone = phone;
            this.zipCode = zipCode;
            this.address = address;
            this.detailAddress = detailAddress;
        }
    }
}