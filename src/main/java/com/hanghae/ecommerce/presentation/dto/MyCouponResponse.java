package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보유 쿠폰 조회 응답 DTO
 */
@Getter
public class MyCouponResponse {
    private final List<CouponResponse> coupons;
    private final Integer totalCount;
    private final Integer availableCount;

    public MyCouponResponse(List<CouponResponse> coupons, Integer totalCount, Integer availableCount) {
        this.coupons = coupons;
        this.totalCount = totalCount;
        this.availableCount = availableCount;
    }

    @Getter
    public static class CouponResponse {
        private final Long userCouponId;
        private final Long couponId;
        private final String couponName;
        private final String couponType;
        private final String discountType;
        private final Integer discountValue;
        private final Integer minOrderAmount;
        private final List<Long> applicableProductIds;
        private final String status;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime expiresAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime issuedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime usedAt;

        public CouponResponse(Long userCouponId, Long couponId, String couponName,
                            String couponType, String discountType, Integer discountValue,
                            Integer minOrderAmount, List<Long> applicableProductIds, String status,
                            LocalDateTime expiresAt, LocalDateTime issuedAt, LocalDateTime usedAt) {
            this.userCouponId = userCouponId;
            this.couponId = couponId;
            this.couponName = couponName;
            this.couponType = couponType;
            this.discountType = discountType;
            this.discountValue = discountValue;
            this.minOrderAmount = minOrderAmount;
            this.applicableProductIds = applicableProductIds;
            this.status = status;
            this.expiresAt = expiresAt;
            this.issuedAt = issuedAt;
            this.usedAt = usedAt;
        }
    }
}