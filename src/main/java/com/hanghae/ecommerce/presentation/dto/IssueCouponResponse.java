package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 응답 DTO
 */
@Getter
public class IssueCouponResponse {
    private final Long userCouponId;
    private final Long couponId;
    private final String couponName;
    private final String couponType;
    private final String discountType;
    private final Integer discountValue;
    private final Integer minOrderAmount;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime expiresAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime issuedAt;

    public IssueCouponResponse(Long userCouponId, Long couponId, String couponName,
                             String couponType, String discountType, Integer discountValue,
                             Integer minOrderAmount, LocalDateTime expiresAt, LocalDateTime issuedAt) {
        this.userCouponId = userCouponId;
        this.couponId = couponId;
        this.couponName = couponName;
        this.couponType = couponType;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiresAt = expiresAt;
        this.issuedAt = issuedAt;
    }
}