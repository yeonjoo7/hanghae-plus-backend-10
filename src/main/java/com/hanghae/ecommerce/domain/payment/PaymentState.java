package com.hanghae.ecommerce.domain.payment;

/**
 * 결제 상태를 나타내는 Value Object
 */
public enum PaymentState {
    PENDING("대기중"),
    COMPLETED("완료"),
    FAILED("실패"),
    CANCELLED("취소됨"),
    REFUNDED("환불됨"),
    DELETED("삭제됨");

    private final String description;

    PaymentState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 대기 상태인지 확인
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 완료된 상태인지 확인
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 실패한 상태인지 확인
     */
    public boolean isFailed() {
        return this == FAILED;
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
     * 처리 가능한 상태인지 확인
     */
    public boolean canBeProcessed() {
        return this == PENDING;
    }

    /**
     * 취소 가능한 상태인지 확인
     */
    public boolean canBeCancelled() {
        return this == PENDING;
    }

    /**
     * 환불 가능한 상태인지 확인
     */
    public boolean canBeRefunded() {
        return this == COMPLETED;
    }

    /**
     * 문자열로부터 PaymentState 생성
     */
    public static PaymentState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("결제 상태는 null일 수 없습니다.");
        }
        
        try {
            return PaymentState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 결제 상태입니다: " + state);
        }
    }
}