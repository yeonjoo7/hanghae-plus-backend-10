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
    private final Money beforeBalance;
    private final Money afterBalance;

    public PaymentResultDto(Payment payment, Order order, List<UserCouponDto> appliedCoupons, 
                           boolean success, String errorMessage) {
        this.payment = payment;
        this.order = order;
        this.appliedCoupons = appliedCoupons;
        this.success = success;
        this.errorMessage = errorMessage;
        this.beforeBalance = null;
        this.afterBalance = null;
    }
    
    public PaymentResultDto(Payment payment, Order order, List<UserCouponDto> appliedCoupons, 
                           boolean success, String errorMessage, Money beforeBalance, Money afterBalance) {
        this.payment = payment;
        this.order = order;
        this.appliedCoupons = appliedCoupons;
        this.success = success;
        this.errorMessage = errorMessage;
        this.beforeBalance = beforeBalance;
        this.afterBalance = afterBalance;
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

    /**
     * 사용자 잔액 정보 조회
     * 결제 후 사용자의 잔액 정보를 반환합니다.
     * 
     * @return 사용자 ID와 잔액 정보가 포함된 객체
     */
    public BalanceInfo getBalanceInfo() {
        Money before = beforeBalance != null ? beforeBalance : Money.zero();
        Money after = afterBalance != null ? afterBalance : Money.zero();
        Money used = payment != null ? payment.getPaidAmount() : Money.zero();
        
        return new BalanceInfo(before, after, used);
    }

    /**
     * 잔액 정보를 담는 내부 클래스
     */
    public static class BalanceInfo {
        private final Money before;
        private final Money after;
        private final Money used;

        public BalanceInfo(Money before, Money after, Money used) {
            this.before = before;
            this.after = after;
            this.used = used;
        }

        public Money getBefore() {
            return before;
        }
        
        public Money getAfter() {
            return after;
        }
        
        public Money getUsed() {
            return used;
        }
    }
}