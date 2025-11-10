package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 사용 이력 조회 응답 DTO
 */
@Getter
public class CouponUsageHistoryResponse {
    private final List<UsageHistoryItem> usageHistory;
    private final Pagination pagination;

    public CouponUsageHistoryResponse(List<UsageHistoryItem> usageHistory, Pagination pagination) {
        this.usageHistory = usageHistory;
        this.pagination = pagination;
    }

    @Getter
    public static class UsageHistoryItem {
        private final Long userCouponId;
        private final String couponName;
        private final Long orderId;
        private final String orderNumber;
        private final Integer discountAmount;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime usedAt;

        public UsageHistoryItem(Long userCouponId, String couponName, Long orderId, 
                              String orderNumber, Integer discountAmount, LocalDateTime usedAt) {
            this.userCouponId = userCouponId;
            this.couponName = couponName;
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.discountAmount = discountAmount;
            this.usedAt = usedAt;
        }
    }

    @Getter
    public static class Pagination {
        private final Integer currentPage;
        private final Integer totalPages;
        private final Integer totalItems;
        private final Integer itemsPerPage;

        public Pagination(Integer currentPage, Integer totalPages, Integer totalItems, Integer itemsPerPage) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.totalItems = totalItems;
            this.itemsPerPage = itemsPerPage;
        }
    }
}