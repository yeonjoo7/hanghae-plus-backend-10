package com.hanghae.ecommerce.domain.coupon;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 정보를 담는 DTO
 */
public class UserCouponInfo {
    private final UserCoupon userCoupon;
    private final Coupon coupon;
    
    public UserCouponInfo(UserCoupon userCoupon, Coupon coupon) {
        if (userCoupon == null) {
            throw new IllegalArgumentException("사용자 쿠폰 정보는 null일 수 없습니다.");
        }
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰 정보는 null일 수 없습니다.");
        }
        this.userCoupon = userCoupon;
        this.coupon = coupon;
    }
    
    public Long getUserCouponId() {
        return userCoupon.getId();
    }
    
    public Long getCouponId() {
        return coupon.getId();
    }
    
    public String getCouponName() {
        return coupon.getName();
    }
    
    public UserCoupon getUserCoupon() {
        return userCoupon;
    }
    
    public Coupon getCoupon() {
        return coupon;
    }
    
    public boolean canUse() {
        return userCoupon.canUse() && coupon.isWithinValidPeriod();
    }
    
    public boolean isUsed() {
        return userCoupon.isUsed();
    }
    
    public LocalDateTime getUsedAt() {
        return userCoupon.getUsedAt();
    }
    
    public LocalDateTime getExpirationDate() {
        return userCoupon.getExpirationDate();
    }
    
    public UserCouponState getState() {
        return userCoupon.getState();
    }
}