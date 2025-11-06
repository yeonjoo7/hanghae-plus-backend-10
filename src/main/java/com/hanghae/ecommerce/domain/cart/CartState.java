package com.hanghae.ecommerce.domain.cart;

/**
 * 장바구니 상태를 나타내는 Value Object
 */
public enum CartState {
    NORMAL("정상"),
    ORDERED("주문됨"),
    DELETED("삭제됨");

    private final String description;

    CartState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 활성 상태인지 확인
     */
    public boolean isActive() {
        return this == NORMAL;
    }

    /**
     * 주문된 상태인지 확인
     */
    public boolean isOrdered() {
        return this == ORDERED;
    }

    /**
     * 삭제된 상태인지 확인
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * 문자열로부터 CartState 생성
     */
    public static CartState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("장바구니 상태는 null일 수 없습니다.");
        }
        
        try {
            return CartState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 장바구니 상태입니다: " + state);
        }
    }
}