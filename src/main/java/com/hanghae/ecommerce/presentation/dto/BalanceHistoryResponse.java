package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 잔액 사용 이력 조회 응답 DTO
 */
@Getter
public class BalanceHistoryResponse {
    private final List<TransactionResponse> transactions;
    private final Pagination pagination;

    public BalanceHistoryResponse(List<TransactionResponse> transactions, Pagination pagination) {
        this.transactions = transactions;
        this.pagination = pagination;
    }

    @Getter
    public static class TransactionResponse {
        private final Long transactionId;
        private final String type;
        private final Integer amount;
        private final Integer balanceBefore;
        private final Integer balanceAfter;
        private final String description;
        private final Long relatedOrderId;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private final LocalDateTime createdAt;

        public TransactionResponse(Long transactionId, String type, Integer amount,
                                 Integer balanceBefore, Integer balanceAfter, String description,
                                 Long relatedOrderId, LocalDateTime createdAt) {
            this.transactionId = transactionId;
            this.type = type;
            this.amount = amount;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
            this.description = description;
            this.relatedOrderId = relatedOrderId;
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