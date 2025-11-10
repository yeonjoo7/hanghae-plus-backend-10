package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 목록 조회 응답 DTO
 */
@Getter
public class OrderListResponse {
    private final List<OrderSummaryResponse> orders;
    private final Pagination pagination;

    public OrderListResponse(List<OrderSummaryResponse> orders, Pagination pagination) {
        this.orders = orders;
        this.pagination = pagination;
    }

    @Getter
    public static class OrderSummaryResponse {
        private final Long orderId;
        private final String orderNumber;
        private final String status;
        private final Integer totalAmount;
        private final Integer discountAmount;
        private final Integer finalAmount;
        private final Integer itemCount;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime createdAt;

        public OrderSummaryResponse(Long orderId, String orderNumber, String status,
                                  Integer totalAmount, Integer discountAmount, Integer finalAmount,
                                  Integer itemCount, LocalDateTime createdAt) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.status = status;
            this.totalAmount = totalAmount;
            this.discountAmount = discountAmount;
            this.finalAmount = finalAmount;
            this.itemCount = itemCount;
            this.createdAt = createdAt;
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