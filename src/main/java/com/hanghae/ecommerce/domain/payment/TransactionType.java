package com.hanghae.ecommerce.domain.payment;

/**
 * 거래 유형을 나타내는 Value Object
 */
public enum TransactionType {
    CHARGE("충전"),
    PAYMENT("결제"),
    REFUND("환불");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 충전 거래인지 확인
     */
    public boolean isCharge() {
        return this == CHARGE;
    }

    /**
     * 결제 거래인지 확인
     */
    public boolean isPayment() {
        return this == PAYMENT;
    }

    /**
     * 환불 거래인지 확인
     */
    public boolean isRefund() {
        return this == REFUND;
    }

    /**
     * 잔액 증가 거래인지 확인
     */
    public boolean isBalanceIncrease() {
        return this == CHARGE || this == REFUND;
    }

    /**
     * 잔액 감소 거래인지 확인
     */
    public boolean isBalanceDecrease() {
        return this == PAYMENT;
    }

    /**
     * 문자열로부터 TransactionType 생성
     */
    public static TransactionType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("거래 유형은 null일 수 없습니다.");
        }
        
        try {
            return TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 거래 유형입니다: " + type);
        }
    }
}