package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 응답 DTO
 */
@Getter
public class PaymentResponse {
    private final Long paymentId;
    private final Long orderId;
    private final String orderNumber;
    private final String paymentMethod;
    private final Integer originalAmount;
    private final Integer discountAmount;
    private final Integer finalAmount;
    private final List<AppliedCouponResponse> appliedCoupons;
    private final BalanceResponse balance;
    private final String status;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime paidAt;

    public PaymentResponse(Long paymentId, Long orderId, String orderNumber, String paymentMethod,
                         Integer originalAmount, Integer discountAmount, Integer finalAmount,
                         List<AppliedCouponResponse> appliedCoupons, BalanceResponse balance,
                         String status, LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.paymentMethod = paymentMethod;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.appliedCoupons = appliedCoupons;
        this.balance = balance;
        this.status = status;
        this.paidAt = paidAt;
    }

    @Getter
    public static class AppliedCouponResponse {
        private final Long couponId;
        private final String couponName;
        private final String couponType;
        private final Integer discountAmount;
        private final Long appliedProductId; // CART_ITEM 타입의 쿠폰에만 존재

        public AppliedCouponResponse(Long couponId, String couponName, String couponType,
                                   Integer discountAmount, Long appliedProductId) {
            this.couponId = couponId;
            this.couponName = couponName;
            this.couponType = couponType;
            this.discountAmount = discountAmount;
            this.appliedProductId = appliedProductId;
        }
    }

    @Getter
    public static class BalanceResponse {
        private final Integer before;
        private final Integer after;
        private final Integer used;

        public BalanceResponse(Integer before, Integer after, Integer used) {
            this.before = before;
            this.after = after;
            this.used = used;
        }
    }
}