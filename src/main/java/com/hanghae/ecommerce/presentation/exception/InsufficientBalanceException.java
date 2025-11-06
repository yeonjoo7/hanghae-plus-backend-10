package com.hanghae.ecommerce.presentation.exception;

import java.util.Map;

/**
 * 잔액이 부족할 때 발생하는 예외
 */
public class InsufficientBalanceException extends BusinessException {
    
    public InsufficientBalanceException(int requiredAmount, int currentBalance) {
        super("INSUFFICIENT_BALANCE", "잔액이 부족합니다", 
              Map.of("requiredAmount", requiredAmount, 
                     "currentBalance", currentBalance,
                     "shortfall", requiredAmount - currentBalance));
    }
}