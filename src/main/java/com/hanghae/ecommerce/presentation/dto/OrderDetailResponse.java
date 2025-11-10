package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 조회 응답 DTO
 */
@Getter
public class OrderDetailResponse {
    private final Long orderId;
    private final String orderNumber;
    private final Long userId;
    private final String status;
    private final List<OrderItemResponse> orderItems;
    private final PaymentResponse payment;
    private final List<AppliedCouponResponse> appliedCoupons;
    private final ShippingAddressResponse shippingAddress;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime updatedAt;

    public OrderDetailResponse(Long orderId, String orderNumber, Long userId, String status,
                             List<OrderItemResponse> orderItems, PaymentResponse payment,
                             List<AppliedCouponResponse> appliedCoupons,
                             ShippingAddressResponse shippingAddress,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.status = status;
        this.orderItems = orderItems;
        this.payment = payment;
        this.appliedCoupons = appliedCoupons;
        this.shippingAddress = shippingAddress;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
    public static class PaymentResponse {
        private final Long paymentId;
        private final String method;
        private final Integer originalAmount;
        private final Integer discountAmount;
        private final Integer finalAmount;
        private final String status;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime paidAt;

        public PaymentResponse(Long paymentId, String method, Integer originalAmount,
                             Integer discountAmount, Integer finalAmount, String status,
                             LocalDateTime paidAt) {
            this.paymentId = paymentId;
            this.method = method;
            this.originalAmount = originalAmount;
            this.discountAmount = discountAmount;
            this.finalAmount = finalAmount;
            this.status = status;
            this.paidAt = paidAt;
        }
    }

    @Getter
    public static class AppliedCouponResponse {
        private final Long couponId;
        private final String couponName;
        private final Integer discountAmount;

        public AppliedCouponResponse(Long couponId, String couponName, Integer discountAmount) {
            this.couponId = couponId;
            this.couponName = couponName;
            this.discountAmount = discountAmount;
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