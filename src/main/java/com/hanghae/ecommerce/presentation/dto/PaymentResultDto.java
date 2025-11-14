package com.hanghae.ecommerce.presentation.dto;

import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.product.Money;

import java.util.List;

public class PaymentResultDto {
    private final Payment payment;
    private final Order order;
    private final List<UserCouponDto> appliedCoupons;
    private final boolean success;
    private final String errorMessage;

    public PaymentResultDto(Payment payment, Order order, List<UserCouponDto> appliedCoupons, 
                           boolean success, String errorMessage) {
        this.payment = payment;
        this.order = order;
        this.appliedCoupons = appliedCoupons;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Payment getPayment() {
        return payment;
    }

    public Order getOrder() {
        return order;
    }

    public List<UserCouponDto> getAppliedCoupons() {
        return appliedCoupons;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPaymentId() {
        return payment != null ? payment.getId().toString() : null;
    }

    public String getOrderNumber() {
        return order != null ? order.getOrderNumber().getValue() : null;
    }

    public Money getFinalAmount() {
        return payment != null ? payment.getPaidAmount() : Money.zero();
    }
}