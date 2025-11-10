package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 잔액 충전 응답 DTO
 */
@Getter
public class ChargeBalanceResponse {
    private final Long transactionId;
    private final String type;
    private final Integer amount;
    private final BalanceInfo balance;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime createdAt;

    public ChargeBalanceResponse(Long transactionId, String type, Integer amount,
                               BalanceInfo balance, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    @Getter
    public static class BalanceInfo {
        private final Integer before;
        private final Integer after;

        public BalanceInfo(Integer before, Integer after) {
            this.before = before;
            this.after = after;
        }
    }
}