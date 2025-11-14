package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;

import java.util.List;

public interface CouponService {
    
    UserCoupon issueCoupon(String couponId, String userId);
    
    Coupon getCoupon(String couponId);
    
    List<Coupon> getAvailableCoupons();
    
    List<UserCoupon> getUserCoupons(String userId);
    
    List<UserCoupon> getAvailableUserCoupons(String userId);
    
    void useCoupon(String userCouponId, String userId);
    
    boolean validateCoupon(String couponId, String userId, double orderAmount);
}