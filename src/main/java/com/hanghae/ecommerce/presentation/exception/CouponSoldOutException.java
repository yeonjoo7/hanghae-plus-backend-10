package com.hanghae.ecommerce.presentation.exception;

/**
 * 쿠폰이 모두 소진되었을 때 발생하는 예외
 */
public class CouponSoldOutException extends BusinessException {
    
    public CouponSoldOutException() {
        super("COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다");
    }
}