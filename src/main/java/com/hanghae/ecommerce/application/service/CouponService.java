package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;

import java.util.List;

public interface CouponService {
    
    UserCoupon issueCoupon(Long couponId, Long userId);
    
    Coupon getCoupon(String couponId);
    
    List<Coupon> getAvailableCoupons();
    
    List<UserCoupon> getUserCoupons(Long userId);
    
    List<UserCoupon> getAvailableUserCoupons(String userId);
    
    void useCoupon(Long userCouponId, Long userId);
    
    boolean validateCoupon(String couponId, String userId, double orderAmount);
}