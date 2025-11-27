package com.hanghae.ecommerce.presentation.dto;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;

public class UserCouponDto {
    private final UserCoupon userCoupon;
    private final Coupon coupon;

    public UserCouponDto(UserCoupon userCoupon, Coupon coupon) {
        this.userCoupon = userCoupon;
        this.coupon = coupon;
    }

    public UserCoupon getUserCoupon() {
        return userCoupon;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public String getUserCouponId() {
        return userCoupon.getId().toString();
    }

    public String getCouponId() {
        return coupon.getId().toString();
    }

    public String getCouponName() {
        return coupon.getName();
    }

    public UserCouponState getState() {
        return userCoupon.getState();
    }

    public boolean canUse() {
        return userCoupon.canUse();
    }

    public boolean isExpired() {
        return userCoupon.isExpired();
    }
}