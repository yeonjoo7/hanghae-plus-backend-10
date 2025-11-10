package com.hanghae.ecommerce.presentation.exception;

/**
 * 이미 발급받은 쿠폰을 다시 발급받으려 할 때 발생하는 예외
 */
public class CouponAlreadyIssuedException extends BusinessException {
    
    public CouponAlreadyIssuedException() {
        super("COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다");
    }
}