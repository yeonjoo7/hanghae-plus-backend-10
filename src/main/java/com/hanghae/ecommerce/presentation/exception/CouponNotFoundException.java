package com.hanghae.ecommerce.presentation.exception;

/**
 * 쿠폰을 찾을 수 없을 때 발생하는 예외
 */
public class CouponNotFoundException extends BusinessException {
    
    public CouponNotFoundException(Long couponId) {
        super("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다", couponId);
    }
}