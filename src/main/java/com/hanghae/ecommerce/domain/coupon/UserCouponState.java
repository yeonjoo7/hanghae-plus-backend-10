package com.hanghae.ecommerce.domain.coupon;

/**
 * 사용자 쿠폰 상태를 나타내는 Value Object
 */
public enum UserCouponState {
    AVAILABLE("사용가능"),
    USED("사용됨"),
    EXPIRED("만료됨"),
    DELETED("삭제됨");

    private final String description;

    UserCouponState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 사용 가능한 상태인지 확인
     */
    public boolean isUsable() {
        return this == AVAILABLE;
    }

    /**
     * 사용된 상태인지 확인
     */
    public boolean isUsed() {
        return this == USED;
    }

    /**
     * 만료된 상태인지 확인
     */
    public boolean isExpired() {
        return this == EXPIRED;
    }

    /**
     * 삭제된 상태인지 확인
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * 문자열로부터 UserCouponState 생성
     */
    public static UserCouponState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("사용자 쿠폰 상태는 null일 수 없습니다.");
        }
        
        try {
            return UserCouponState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 사용자 쿠폰 상태입니다: " + state);
        }
    }
}