package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 잔액 조회 응답 DTO
 */
@Getter
public class BalanceResponse {
    private final Long userId;
    private final Integer balance;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime lastUpdatedAt;

    public BalanceResponse(Long userId, Integer balance, LocalDateTime lastUpdatedAt) {
        this.userId = userId;
        this.balance = balance;
        this.lastUpdatedAt = lastUpdatedAt;
    }
}