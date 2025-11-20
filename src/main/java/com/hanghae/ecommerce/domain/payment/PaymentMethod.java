package com.hanghae.ecommerce.domain.payment;

/**
 * 결제 수단을 나타내는 Value Object
 */
public enum PaymentMethod {
    POINT("포인트"),
    BALANCE("잔액"),
    CARD("카드"),
    BANK_TRANSFER("계좌이체");

    private final String description;

    PaymentMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 포인트 결제인지 확인
     */
    public boolean isPoint() {
        return this == POINT;
    }
    
    /**
     * 잔액 결제인지 확인
     */
    public boolean isBalance() {
        return this == BALANCE;
    }

    /**
     * 카드 결제인지 확인
     */
    public boolean isCard() {
        return this == CARD;
    }

    /**
     * 계좌이체인지 확인
     */
    public boolean isBankTransfer() {
        return this == BANK_TRANSFER;
    }

    /**
     * 즉시 처리 가능한 결제 수단인지 확인
     */
    public boolean isInstantProcessable() {
        return this == POINT || this == BALANCE;
    }

    /**
     * 문자열로부터 PaymentMethod 생성
     */
    public static PaymentMethod fromString(String method) {
        if (method == null) {
            throw new IllegalArgumentException("결제 수단은 null일 수 없습니다.");
        }
        
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 결제 수단입니다: " + method);
        }
    }
}