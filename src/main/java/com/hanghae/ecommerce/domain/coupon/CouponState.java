package com.hanghae.ecommerce.domain.coupon;

/**
 * 쿠폰 상태를 나타내는 Value Object
 */
public enum CouponState {
    NORMAL("정상"),
    EXPIRED("만료됨"),
    DISCONTINUED("중단됨"),
    DELETED("삭제됨");

    private final String description;

    CouponState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 발급 가능한 상태인지 확인
     */
    public boolean isIssuable() {
        return this == NORMAL;
    }

    /**
     * 삭제된 상태인지 확인
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * 만료된 상태인지 확인
     */
    public boolean isExpired() {
        return this == EXPIRED;
    }

    /**
     * 문자열로부터 CouponState 생성
     */
    public static CouponState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("쿠폰 상태는 null일 수 없습니다.");
        }
        
        try {
            return CouponState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 쿠폰 상태입니다: " + state);
        }
    }
}