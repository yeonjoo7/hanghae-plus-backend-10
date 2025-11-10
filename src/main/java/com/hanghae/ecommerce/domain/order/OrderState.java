package com.hanghae.ecommerce.domain.order;

/**
 * 주문 상태를 나타내는 Value Object
 */
public enum OrderState {
    PENDING_PAYMENT("결제대기"),
    COMPLETED("완료"),
    CANCELLED("취소됨"),
    REFUNDED("환불됨"),
    DELETED("삭제됨");

    private final String description;

    OrderState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 결제 대기 상태인지 확인
     */
    public boolean isPendingPayment() {
        return this == PENDING_PAYMENT;
    }

    /**
     * 완료된 상태인지 확인
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 취소된 상태인지 확인
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }

    /**
     * 환불된 상태인지 확인
     */
    public boolean isRefunded() {
        return this == REFUNDED;
    }

    /**
     * 삭제된 상태인지 확인
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * 결제 가능한 상태인지 확인
     */
    public boolean canBePaid() {
        return this == PENDING_PAYMENT;
    }

    /**
     * 취소 가능한 상태인지 확인
     */
    public boolean canBeCancelled() {
        return this == PENDING_PAYMENT;
    }

    /**
     * 환불 가능한 상태인지 확인
     */
    public boolean canBeRefunded() {
        return this == COMPLETED;
    }

    /**
     * 문자열로부터 OrderState 생성
     */
    public static OrderState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("주문 상태는 null일 수 없습니다.");
        }
        
        try {
            return OrderState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 주문 상태입니다: " + state);
        }
    }
}